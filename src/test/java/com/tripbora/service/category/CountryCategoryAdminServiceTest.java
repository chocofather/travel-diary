package com.tripbora.service.category;

import com.tripbora.dto.CountryCategoryForm;
import com.tripbora.model.CountryCategory;
import com.tripbora.model.CountryCategoryTranslation;
import com.tripbora.repository.category.CountryCategoryMapper;
import com.tripbora.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 지역 등록·삭제.
 *
 * <p>지역 트리는 여행지·코스·검색이 함께 쓰는 기준 데이터라, 잘못 들어가거나 쓰이는 지역이 지워지면
 * 목록에서 조용히 사라지는 형태로만 드러난다. 그래서 등록 검증, 삭제 차단, 캐시 무효화를 고정한다.
 *
 * <p>계층: 대한민국(7) → 서울(38, depth 3) → 종로구(235, depth 4) /
 * 유럽(2) → 독일(20, depth 2) → 베를린(100, depth 3)
 */
class CountryCategoryAdminServiceTest {

    private CountryCategoryMapper mapper;
    private CountryCategorySeedTransactionService seedService;
    private FileUploadService fileUploadService;
    private CountryCategoryCache cache;
    private CountryCategoryService countryCategoryService;
    private CountryCategoryAdminService service;
    private final Map<Long, CountryCategory> regions = new HashMap<>();

    @BeforeEach
    void setUp() {
        mapper = mock(CountryCategoryMapper.class);
        seedService = mock(CountryCategorySeedTransactionService.class);
        fileUploadService = mock(FileUploadService.class);
        cache = new CountryCategoryCache();
        countryCategoryService = new CountryCategoryService(mapper, fileUploadService, cache);
        service = new CountryCategoryAdminService(mapper, countryCategoryService, seedService, fileUploadService, cache);

        CountryCategory korea = region(7L, "대한민국", "South Korea", "KR", null, 1);
        CountryCategory europe = region(2L, "유럽", "Europe", "EU", null, 1);
        CountryCategory germany = region(20L, "독일", "Germany", "DE", 2L, 2);
        CountryCategory seoul = region(38L, "서울", "Seoul", "KR-11", 7L, 3);
        region(235L, "종로구", "Jongno-gu", "KR-11-01", 38L, 4);
        CountryCategory berlin = region(100L, "베를린", "Berlin", "DE-BER", 20L, 3);
        regions.values().forEach(r -> when(mapper.selectById(r.getId())).thenReturn(r));

        when(mapper.selectCourseCountries()).thenReturn(List.of(korea, germany));
        when(mapper.findByDepth(1, null)).thenReturn(List.of(europe, korea));
        when(mapper.selectByParentId(20L)).thenReturn(List.of(berlin));
        when(mapper.selectByParentId(7L)).thenReturn(List.of(seoul));
        when(mapper.selectChildDepths(20L)).thenReturn(List.of(3));
        when(mapper.selectChildDepths(7L)).thenReturn(List.of(3));
        when(mapper.findTranslationsByCountryCategoryIds(List.of(100L)))
                .thenReturn(List.of(translation(100L, "ja", "ベルリン")));
        doAnswer(invocation -> {
            invocation.<CountryCategory>getArgument(0).setId(500L);
            return 1;
        }).when(mapper).insertRegion(any());
    }

    // ---------- 등록 ----------

    @Test
    void leafUnderCountryIsStoredWithParentDepthAndNoCode() {
        CountryCategoryForm form = form(20L, " 슈방가우 ", "Schwangau");
        form.setCode("SHOULD-NOT-BE-USED");

        CountryCategory created = service.create(form);

        ArgumentCaptor<CountryCategory> inserted = ArgumentCaptor.forClass(CountryCategory.class);
        verify(mapper).insertRegion(inserted.capture());
        assertThat(inserted.getValue().getRegionName()).isEqualTo("슈방가우");
        assertThat(inserted.getValue().getParentId()).isEqualTo(20L);
        assertThat(inserted.getValue().getDepth()).isEqualTo(3);
        // 도시 leaf 는 임의 코드를 만들지 않는다
        assertThat(inserted.getValue().getCode()).isNull();
        assertThat(inserted.getValue().getIconPath()).isNull();
        assertThat(created.getId()).isEqualTo(500L);
    }

    @Test
    void translationsAreStoredForAllGivenLanguagesInTheSameCall() {
        CountryCategoryForm form = form(20L, "슈방가우", "Schwangau");
        form.setNameJa("シュヴァンガウ");
        form.setNameZhCn("施万高");
        form.setNameZhTw(" ");

        service.create(form);

        ArgumentCaptor<CountryCategoryTranslation> saved = ArgumentCaptor.forClass(CountryCategoryTranslation.class);
        verify(mapper, times(4)).insertTranslation(saved.capture());
        Map<String, String> byLanguage = saved.getAllValues().stream()
                .peek(t -> assertThat(t.getCountryCategoryId()).isEqualTo(500L))
                .collect(Collectors.toMap(CountryCategoryTranslation::getLanguageCode, CountryCategoryTranslation::getName));
        // ko/en 은 기본 정보 값, 비운 언어(zh-TW)는 줄을 만들지 않는다
        assertThat(byLanguage).containsExactlyInAnyOrderEntriesOf(Map.of(
                "ko", "슈방가우", "en", "Schwangau", "ja", "シュヴァンガウ", "zh-CN", "施万高"));
    }

    @Test
    void duplicateNamesUnderTheSameParentAreRejected() {
        assertRejected(form(20L, "베를린", "Other"), "regionName");
        assertRejected(form(20L, "다른 이름", "  berlin "), "nameEn");
        CountryCategoryForm sameJapanese = form(20L, "다른 이름", "Other");
        sameJapanese.setNameJa("ベルリン");
        assertRejected(sameJapanese, "nameJa");
        verify(mapper, never()).insertRegion(any());
        verify(mapper, never()).insertTranslation(any());
    }

    @Test
    void blankNamesAndMissingOrUnknownParentsAreRejected() {
        assertRejected(form(20L, "  ", "Schwangau"), "regionName");
        assertRejected(form(20L, "슈방가우", ""), "nameEn");
        assertRejected(form(null, "슈방가우", "Schwangau"), "parentId");
        assertRejected(form(999L, "슈방가우", "Schwangau"), "parentId");
        verify(mapper, never()).insertRegion(any());
    }

    @Test
    void leavesCannotReceiveChildren() {
        // 베를린(도시)·종로구(시/군/구) 아래는 공개 목록이 다루지 않는 4단계라 막는다
        assertRejected(form(100L, "미테", "Mitte"), "parentId");
        assertRejected(form(235L, "청운동", "Cheongun-dong"), "parentId");
        verify(mapper, never()).insertRegion(any());
    }

    @Test
    void countryUnderContinentNeedsAValidUnusedCountryCode() {
        assertRejected(form(2L, "오스트리아", "Austria"), "code");
        CountryCategoryForm hyphenated = form(2L, "오스트리아", "Austria");
        hyphenated.setCode("AT-1");
        assertRejected(hyphenated, "code");
        CountryCategoryForm used = form(2L, "오스트리아", "Austria");
        used.setCode("de");
        when(mapper.countByCode("DE")).thenReturn(1);
        assertRejected(used, "code");

        CountryCategoryForm valid = form(2L, "오스트리아", "Austria");
        valid.setCode(" at ");
        when(mapper.selectChildDepths(2L)).thenReturn(List.of(2));
        service.create(valid);

        ArgumentCaptor<CountryCategory> inserted = ArgumentCaptor.forClass(CountryCategory.class);
        verify(mapper).insertRegion(inserted.capture());
        assertThat(inserted.getValue().getCode()).isEqualTo("AT");
        assertThat(inserted.getValue().getDepth()).isEqualTo(2);
    }

    @Test
    void domesticDistrictFollowsTheDepthUsedElsewhereWhenTheParentHasNoChildrenYet() {
        // 국내 시/도(depth 3) 아래 시/군/구는 depth 4 를 쓴다. 부모 depth + 1 로 추측하지 않는다.
        CountryCategory sejong = region(39L, "세종", "Sejong", "KR-50", 7L, 3);
        when(mapper.selectById(39L)).thenReturn(sejong);
        when(mapper.selectChildDepths(39L)).thenReturn(List.of());
        when(mapper.selectGrandchildDepths(7L)).thenReturn(List.of(4));

        service.create(form(39L, "조치원읍", "Jochiwon-eup"));

        ArgumentCaptor<CountryCategory> inserted = ArgumentCaptor.forClass(CountryCategory.class);
        verify(mapper).insertRegion(inserted.capture());
        assertThat(inserted.getValue().getDepth()).isEqualTo(4);
        assertThat(inserted.getValue().getCode()).isNull();
    }

    @Test
    void domesticProvinceCodeMustKeepTheCountryPrefix() {
        CountryCategoryForm wrong = form(7L, "새도", "New Province");
        wrong.setCode("NP");
        assertRejected(wrong, "code");

        CountryCategoryForm valid = form(7L, "새도", "New Province");
        valid.setCode("kr-99");
        service.create(valid);

        ArgumentCaptor<CountryCategory> inserted = ArgumentCaptor.forClass(CountryCategory.class);
        verify(mapper).insertRegion(inserted.capture());
        assertThat(inserted.getValue().getCode()).isEqualTo("KR-99");
        assertThat(inserted.getValue().getDepth()).isEqualTo(3);
    }

    @Test
    void creatingARegionDropsTheCachedRegionTree() {
        countryCategoryService.getById(20L);
        countryCategoryService.getRegionsByDepthAndParent(3, 20L);
        assertThat(cache.size()).isPositive();

        service.create(form(20L, "슈방가우", "Schwangau"));

        assertThat(cache.size()).isZero();
    }

    // ---------- 삭제 ----------

    @Test
    void regionWithChildrenIsNotDeleted() {
        when(mapper.countChildren(20L)).thenReturn(1);

        assertThatThrownBy(() -> service.delete(20L))
                .isInstanceOf(CountryCategoryDeleteBlockedException.class)
                .hasMessage("하위 지역이 존재하여 삭제할 수 없습니다.");
        verify(mapper, never()).deleteById(anyLong());
    }

    @Test
    void regionUsedByDestinationsOrCoursesIsNotDeleted() {
        when(mapper.countDestinationsByRegionId(100L)).thenReturn(3);
        assertThatThrownBy(() -> service.delete(100L))
                .isInstanceOf(CountryCategoryDeleteBlockedException.class)
                .hasMessage("이 지역을 사용하는 여행지가 있어 삭제할 수 없습니다.");

        when(mapper.countDestinationsByRegionId(100L)).thenReturn(0);
        when(mapper.countCoursesByCountryId(100L)).thenReturn(1);
        assertThatThrownBy(() -> service.delete(100L))
                .isInstanceOf(CountryCategoryDeleteBlockedException.class)
                .hasMessageContaining("여행 코스");
        verify(mapper, never()).deleteById(anyLong());
    }

    @Test
    void rootAndSeedRegionsAreProtected() {
        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(CountryCategoryDeleteBlockedException.class)
                .hasMessage("최상위 지역은 삭제할 수 없습니다.");

        // 기준 데이터(JSON) 지역은 지워도 다음 기동 때 다시 생기므로 막는다
        when(seedService.isSeedRegion(100L)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(100L))
                .isInstanceOf(CountryCategoryDeleteBlockedException.class)
                .hasMessageContaining("기본 제공 지역");
        verify(mapper, never()).deleteById(anyLong());
    }

    @Test
    void unusedLeafIsDeletedWithItsIconAndTheCacheIsDropped() {
        CountryCategory added = region(501L, "기자", "Giza", null, 20L, 3);
        added.setIconPath("/uploads/icons/0f8c7a3e-2b1d-4c5e-9a6f-1234567890ab.png");
        when(mapper.selectById(501L)).thenReturn(added);
        when(mapper.deleteById(501L)).thenReturn(1);
        countryCategoryService.getById(501L);
        assertThat(cache.size()).isPositive();

        CountryCategory deleted = service.delete(501L);

        assertThat(deleted.getParentId()).isEqualTo(20L);
        // 번역은 FK ON DELETE CASCADE 로 함께 지워지므로 따로 지우지 않는다
        verify(mapper).deleteById(501L);
        verify(fileUploadService).deleteSavedFile(added.getIconPath(), CountryCategoryService.ICON_DIRECTORY);
        assertThat(cache.size()).isZero();
    }

    @Test
    void iconSharedWithAnotherRegionIsKept() {
        CountryCategory added = region(501L, "기자", "Giza", null, 20L, 3);
        added.setIconPath("/uploads/icons/0f8c7a3e-2b1d-4c5e-9a6f-1234567890ab.png");
        when(mapper.selectById(501L)).thenReturn(added);
        when(mapper.deleteById(501L)).thenReturn(1);
        when(mapper.countOtherRegionsByIconPath(501L, added.getIconPath())).thenReturn(1);

        service.delete(501L);

        verify(fileUploadService, never()).deleteSavedFile(anyString(), anyString());
    }

    @Test
    void foreignKeyViolationIsReportedAsBlockedDelete() {
        CountryCategory added = region(501L, "기자", "Giza", null, 20L, 3);
        when(mapper.selectById(501L)).thenReturn(added);
        when(mapper.deleteById(501L)).thenThrow(new DataIntegrityViolationException("fk"));

        assertThatThrownBy(() -> service.delete(501L))
                .isInstanceOf(CountryCategoryDeleteBlockedException.class)
                .hasMessageContaining("참조");
        verify(fileUploadService, never()).deleteSavedFile(anyString(), eq(CountryCategoryService.ICON_DIRECTORY));
    }

    // ---------- 도우미 ----------

    private void assertRejected(CountryCategoryForm form, String field) {
        assertThatThrownBy(() -> service.create(form))
                .isInstanceOfSatisfying(CountryCategoryValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo(field));
    }

    private static CountryCategoryForm form(Long parentId, String regionName, String nameEn) {
        CountryCategoryForm form = new CountryCategoryForm();
        form.setParentId(parentId);
        form.setRegionName(regionName);
        form.setNameEn(nameEn);
        return form;
    }

    private CountryCategory region(Long id, String name, String nameEn, String code, Long parentId, int depth) {
        CountryCategory region = new CountryCategory();
        region.setId(id);
        region.setRegionName(name);
        region.setNameEn(nameEn);
        region.setCode(code);
        region.setParentId(parentId);
        region.setDepth(depth);
        regions.put(id, region);
        return region;
    }

    private static CountryCategoryTranslation translation(Long categoryId, String languageCode, String name) {
        CountryCategoryTranslation translation = new CountryCategoryTranslation();
        translation.setCountryCategoryId(categoryId);
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        return translation;
    }
}
