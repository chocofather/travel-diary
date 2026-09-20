package com.example.travlediary.service.category;

import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.repository.category.CountryCategoryMapper;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 국가·지역 기준 데이터를 요청마다 다시 읽지 않는다.
 *
 * <p>이 표는 기동할 때 JSON 에서 채우고 그 뒤로는 관리자가 아이콘을 바꿀 때 말고는 변하지 않는다.
 * 그런데 여행지 목록 한 번에 지역 조회가 수십 번 일어난다. 그래서 요청 사이에도 들고 있는다.
 *
 * <p>여기서 고정하는 것은 두 가지다. <b>같은 것을 두 번 읽지 않는다</b>는 것과,
 * <b>자료가 바뀌면 들고 있던 것을 버린다</b>는 것이다. 뒤엣것이 무너지면 관리자가 바꾼 내용이
 * 재시작 전까지 보이지 않게 되므로, 바꾸는 길마다 하나씩 확인한다.
 */
class CountryCategoryCacheTest {

    private CountryCategoryMapper mapper;
    private FileUploadService fileUploadService;
    private CountryCategoryCache cache;
    private CountryCategoryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(CountryCategoryMapper.class);
        fileUploadService = mock(FileUploadService.class);
        cache = new CountryCategoryCache();
        service = new CountryCategoryService(mapper, fileUploadService, cache);
    }

    // ---------- 같은 것을 두 번 읽지 않는다 ----------

    @Test
    void theSameRegionIsReadFromTheDatabaseOnlyOnce() {
        when(mapper.selectById(7L)).thenReturn(region(7L, "대한민국"));

        for (int i = 0; i < 5; i++) {
            assertThat(service.getById(7L).getRegionName()).isEqualTo("대한민국");
        }

        verify(mapper, times(1)).selectById(7L);
    }

    @Test
    void differentRegionsAreEachReadAndEachReturnedCorrectly() {
        when(mapper.selectById(7L)).thenReturn(region(7L, "대한민국"));
        when(mapper.selectById(38L)).thenReturn(region(38L, "서울"));

        assertThat(service.getById(7L).getRegionName()).isEqualTo("대한민국");
        assertThat(service.getById(38L).getRegionName()).isEqualTo("서울");
        assertThat(service.getById(7L).getRegionName()).isEqualTo("대한민국");
        assertThat(service.getById(38L).getRegionName()).isEqualTo("서울");

        verify(mapper, times(1)).selectById(7L);
        verify(mapper, times(1)).selectById(38L);
    }

    /** 하위 지역 id 묶음은 재귀 질의라 가장 비싸다. 여행지 목록이 요청마다 여러 번 부른다. */
    @Test
    void theRegionSubtreeIsResolvedOnlyOnce() {
        when(mapper.findAllRegionIdsUnder(7L)).thenReturn(List.of(7L, 38L, 235L));

        for (int i = 0; i < 4; i++) {
            assertThat(service.getAllRegionIdsUnder(7L)).containsExactly(7L, 38L, 235L);
        }

        verify(mapper, times(1)).findAllRegionIdsUnder(7L);
    }

    @Test
    void subregionsAreKeyedByBothParentAndDepth() {
        when(mapper.selectByParentIdAndDepth(7L, 3)).thenReturn(List.of(region(38L, "서울")));
        when(mapper.selectByParentIdAndDepth(7L, 4)).thenReturn(List.of(region(39L, "종로구")));

        assertThat(service.getSubregions(7L, 3)).hasSize(1);
        assertThat(service.getSubregions(7L, 4)).hasSize(1);
        assertThat(service.getSubregions(7L, 3)).hasSize(1);

        verify(mapper, times(1)).selectByParentIdAndDepth(7L, 3);
        verify(mapper, times(1)).selectByParentIdAndDepth(7L, 4);
    }

    /** 코스 국가 목록 하나를 여러 메서드가 나눠 쓴다. 그 전부가 한 번의 조회로 끝나야 한다. */
    @Test
    void theCourseCountryListIsSharedByEveryDerivedLookup() {
        when(mapper.selectCourseCountries()).thenReturn(List.of(
                region(7L, "대한민국"), overseas(100L, 1L, "일본")));

        service.getCourseCountries();
        service.getDomesticRootIds();
        service.getKoreaRootId();

        verify(mapper, times(1)).selectCourseCountries();
    }

    @Test
    void depthLookupsAreSharedByEveryDerivedLookup() {
        when(mapper.findByDepth(1, null)).thenReturn(List.of(region(7L, "대한민국")));
        when(mapper.selectCourseCountries()).thenReturn(List.of(region(7L, "대한민국")));

        service.getContinentRoots();
        service.getRegionsByDepth(1);
        service.getOverseasRootIds();

        verify(mapper, times(1)).findByDepth(1, null);
    }

    // ---------- 자료가 바뀌면 들고 있던 것을 버린다 ----------

    @Test
    void changingAnIconMakesTheNextLookupReadTheDatabaseAgain() {
        when(mapper.selectById(7L)).thenReturn(region(7L, "대한민국"));
        when(fileUploadService.saveFile(any(), anyString())).thenReturn("/uploads/icons/a.png");
        service.getById(7L);

        service.saveIcon(7L, new MockMultipartFile("icon", "a.png", "image/png", new byte[]{1}));

        service.getById(7L);
        verify(mapper, times(2)).selectById(7L);
    }

    /** 기준 데이터를 다시 채우면 그 전에 들고 있던 것이 남아 있으면 안 된다. */
    @Test
    void reseedingTheReferenceDataLeavesNoStaleEntry() {
        when(mapper.selectById(7L)).thenReturn(region(7L, "예전 이름"));
        service.getById(7L);

        new CountryCategorySeedTransactionService(mapper, cache)
                .insertMissing(List.of(region(900L, "새 지역")));

        when(mapper.selectById(7L)).thenReturn(region(7L, "새 이름"));
        assertThat(service.getById(7L).getRegionName()).isEqualTo("새 이름");
        verify(mapper, times(2)).selectById(7L);
    }

    @Test
    void clearingTheCacheDirectlyDropsEverything() {
        when(mapper.selectCourseCountries()).thenReturn(List.of(region(7L, "대한민국")));
        service.getCourseCountries();

        cache.invalidate();

        service.getCourseCountries();
        verify(mapper, times(2)).selectCourseCountries();
    }

    // ---------- 캐시하면 안 되는 것 ----------

    /** 무작위로 고르는 조회는 캐시하면 언제나 같은 결과가 나온다. */
    @Test
    void randomlyPickedCountriesAreNeverCached() {
        when(mapper.findRandomOverseasCountries(3))
                .thenReturn(List.of(overseas(100L, 1L, "일본")));

        service.findRandomOverseasCountries(3);
        service.findRandomOverseasCountries(3);

        verify(mapper, times(2)).findRandomOverseasCountries(3);
    }

    // ---------- 들고 있는 값이 밖에서 망가지지 않는다 ----------

    @Test
    void aCachedListCannotBeModifiedByItsCaller() {
        when(mapper.selectCourseCountries())
                .thenReturn(new java.util.ArrayList<>(List.of(region(7L, "대한민국"))));

        List<CountryCategory> countries = service.getCourseCountries();

        assertThatThrownBy(() -> countries.add(region(8L, "끼워넣기")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(service.getCourseCountries()).hasSize(1);
    }

    // ---------- 도우미 ----------

    private CountryCategory region(Long id, String name) {
        CountryCategory category = new CountryCategory();
        category.setId(id);
        category.setRegionName(name);
        category.setDepth(1);
        category.setIsVisible(1);
        return category;
    }

    private CountryCategory overseas(Long id, Long parentId, String name) {
        CountryCategory category = region(id, name);
        category.setParentId(parentId);
        category.setDepth(2);
        return category;
    }
}
