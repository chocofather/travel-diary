package com.tripbora.service.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripbora.model.CountryCategory;
import com.tripbora.model.CountryCategoryTranslation;
import com.tripbora.repository.category.CountryCategoryMapper;
import com.tripbora.service.category.CountryCategoryBulkCreateService.Result;
import com.tripbora.service.category.CountryCategoryBulkCreateService.Row;
import com.tripbora.service.category.CountryCategoryBulkCreateService.Status;
import com.tripbora.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 지역 일괄 등록.
 *
 * <p>행마다 단건 등록 서비스를 그대로 부르므로 같은 검증을 받는지, 한 행의 실패가 다른 행을 막지 않는지,
 * 파일 자체가 잘못되면 아무것도 저장하지 않는지를 고정한다.
 *
 * <p>계층: 대한민국(7) → 서울(38) / 유럽(2) → 독일(20) → 베를린(100) / 아시아(1) → 인도네시아(30)
 */
class CountryCategoryBulkCreateServiceTest {

    private CountryCategoryMapper mapper;
    private CountryCategoryCache cache;
    private CountryCategoryBulkCreateService service;
    private final Map<Long, CountryCategory> regions = new HashMap<>();

    @BeforeEach
    void setUp() {
        mapper = mock(CountryCategoryMapper.class);
        FileUploadService fileUploadService = mock(FileUploadService.class);
        cache = new CountryCategoryCache();
        CountryCategoryService countryCategoryService = new CountryCategoryService(mapper, fileUploadService, cache);
        CountryCategoryAdminService adminService = new CountryCategoryAdminService(mapper, countryCategoryService,
                mock(CountryCategorySeedTransactionService.class), fileUploadService, cache);
        service = new CountryCategoryBulkCreateService(adminService, new ObjectMapper());

        CountryCategory korea = region(7L, "대한민국", "South Korea", "KR", null, 1);
        CountryCategory asia = region(1L, "아시아", "Asia", "AS", null, 1);
        CountryCategory europe = region(2L, "유럽", "Europe", "EU", null, 1);
        CountryCategory indonesia = region(30L, "인도네시아", "Indonesia", "ID", 1L, 2);
        CountryCategory germany = region(20L, "독일", "Germany", "DE", 2L, 2);
        CountryCategory seoul = region(38L, "서울", "Seoul", "KR-11", 7L, 3);
        CountryCategory berlin = region(100L, "베를린", "Berlin", "DE-BER", 20L, 3);
        regions.values().forEach(r -> when(mapper.selectById(r.getId())).thenReturn(r));

        when(mapper.selectCourseCountries()).thenReturn(List.of(korea, indonesia, germany));
        when(mapper.findByDepth(1, null)).thenReturn(List.of(asia, europe, korea));
        when(mapper.selectByParentId(7L)).thenReturn(List.of(seoul));
        when(mapper.selectByParentId(1L)).thenReturn(List.of(indonesia));
        when(mapper.selectByParentId(2L)).thenReturn(List.of(germany));
        when(mapper.selectByParentId(20L)).thenReturn(List.of(berlin));
        when(mapper.selectByParentId(30L)).thenReturn(List.of());
        when(mapper.selectChildDepths(20L)).thenReturn(List.of(3));
        when(mapper.selectChildDepths(30L)).thenReturn(List.of());
        when(mapper.selectGrandchildDepths(1L)).thenReturn(List.of(3));
        when(mapper.selectChildDepths(2L)).thenReturn(List.of(2));
        when(mapper.findTranslationsByCountryCategoryIds(List.of(100L)))
                .thenReturn(List.of(translation(100L, "ja", "ベルリン")));
        AtomicLong nextId = new AtomicLong(500L);
        doAnswer(invocation -> {
            invocation.<CountryCategory>getArgument(0).setId(nextId.getAndIncrement());
            return 1;
        }).when(mapper).insertRegion(any());
    }

    @Test
    void validLeavesAreStoredWithAllFiveLanguagesUnderTheirNamedParents() {
        Result result = service.create("""
                [
                  {"parent": "인도네시아", "ko": "중부 자바", "en": "Central Java",
                   "ja": "中部ジャワ州", "zh-CN": "中爪哇省", "zh-TW": "中爪哇省"},
                  {"parent": "독일", "ko": "슈방가우", "en": "Schwangau"}
                ]
                """);

        assertThat(result.fileError()).isNull();
        assertThat(result.rows()).extracting(Row::status).containsExactly(Status.CREATED, Status.CREATED);
        assertThat(result.created()).isEqualTo(2);

        ArgumentCaptor<CountryCategory> inserted = ArgumentCaptor.forClass(CountryCategory.class);
        verify(mapper, times(2)).insertRegion(inserted.capture());
        CountryCategory centralJava = inserted.getAllValues().get(0);
        assertThat(centralJava.getParentId()).isEqualTo(30L);
        // 형제가 없으면 같은 계층(다른 국가의 도시)의 depth 를 따르고, 해외 leaf 는 code·subregion 을 만들지 않는다
        assertThat(centralJava.getDepth()).isEqualTo(3);
        assertThat(centralJava.getCode()).isNull();
        assertThat(centralJava.getSubregion()).isNull();
        assertThat(inserted.getAllValues().get(1).getParentId()).isEqualTo(20L);

        ArgumentCaptor<CountryCategoryTranslation> saved = ArgumentCaptor.forClass(CountryCategoryTranslation.class);
        verify(mapper, times(7)).insertTranslation(saved.capture());
        Map<String, String> centralJavaNames = saved.getAllValues().stream()
                .filter(t -> t.getCountryCategoryId() == 500L)
                .collect(Collectors.toMap(CountryCategoryTranslation::getLanguageCode, CountryCategoryTranslation::getName));
        assertThat(centralJavaNames).containsExactlyInAnyOrderEntriesOf(Map.of(
                "ko", "중부 자바", "en", "Central Java", "ja", "中部ジャワ州", "zh-CN", "中爪哇省", "zh-TW", "中爪哇省"));
    }

    @Test
    void oneBadRowDoesNotBlockTheOthers() {
        Result result = service.create("""
                [
                  {"parent": "독일", "ko": "슈방가우", "en": "Schwangau"},
                  {"parent": "독일", "ko": "베를린", "en": "Berlin"},
                  {"parent": "독일", "ko": "다른 이름", "en": "Other", "ja": "ベルリン"},
                  {"parent": "독일", "ko": "슈방가우", "en": "Schwangau 2"},
                  {"parent": "없는 나라", "ko": "어딘가", "en": "Somewhere"},
                  {"parent": "베를린", "ko": "미테", "en": "Mitte"},
                  {"parent": "독일", "ko": "영어 없음"},
                  {"parent": "독일", "ko": "필드 오류", "en": "Typo", "zh-cn": "错"},
                  {"parent": "독일", "ko": "숫자", "en": 1},
                  "문자열 한 줄",
                  {"parent": "인도네시아", "ko": "중부 자바", "en": "Central Java"}
                ]
                """);

        assertThat(result.rows()).extracting(Row::status).containsExactly(
                Status.CREATED,    // 정상
                Status.DUPLICATE,  // DB 에 이미 있는 이름 (한국어)
                Status.DUPLICATE,  // DB 에 이미 있는 이름 (일본어 번역)
                Status.DUPLICATE,  // 같은 파일 앞 행과 같은 이름
                Status.FAILED,     // 없는 부모
                Status.FAILED,     // 도시 leaf 아래 (잘못된 계층)
                Status.FAILED,     // 필수 영어명 없음
                Status.FAILED,     // 알 수 없는 필드
                Status.FAILED,     // 문자열이 아닌 값
                Status.FAILED,     // 객체가 아닌 행
                Status.CREATED);   // 앞 행이 실패해도 정상 행은 등록된다
        assertThat(result.rows().get(3).message()).contains("JSON 안의 앞 행");
        assertThat(result.rows().get(5).message()).contains("부모 지역을 찾을 수 없습니다");
        assertThat(result.rows().get(7).message()).contains("zh-cn");
        assertThat(result.total()).isEqualTo(11);
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.duplicates()).isEqualTo(3);
        assertThat(result.failed()).isEqualTo(6);
        verify(mapper, times(2)).insertRegion(any());
    }

    @Test
    void countryRowsFollowTheSameCodeRuleAsSingleRegistration() {
        Result result = service.create("""
                [
                  {"parent": "유럽", "ko": "오스트리아", "en": "Austria"},
                  {"parent": "유럽", "ko": "스위스", "en": "Switzerland", "code": "ch"}
                ]
                """);

        assertThat(result.rows()).extracting(Row::status).containsExactly(Status.FAILED, Status.CREATED);
        assertThat(result.rows().get(0).message()).contains("코드");
        ArgumentCaptor<CountryCategory> inserted = ArgumentCaptor.forClass(CountryCategory.class);
        verify(mapper).insertRegion(inserted.capture());
        assertThat(inserted.getValue().getCode()).isEqualTo("CH");
        assertThat(inserted.getValue().getDepth()).isEqualTo(2);
    }

    @Test
    void ambiguousParentNamesAreRejectedInsteadOfPickingOne() {
        CountryCategory otherGermany = region(21L, "독일", "Germany (other)", "DX", 1L, 2);
        when(mapper.selectByParentId(1L)).thenReturn(List.of(regions.get(30L), otherGermany));

        Result result = service.create("[{\"parent\": \"독일\", \"ko\": \"슈방가우\", \"en\": \"Schwangau\"}]");

        assertThat(result.rows()).extracting(Row::status).containsExactly(Status.FAILED);
        assertThat(result.rows().get(0).message()).contains("여러 곳");
        verify(mapper, never()).insertRegion(any());
    }

    @Test
    void unreadableOrOversizedFilesStoreNothing() {
        assertThat(service.create("  ").fileError()).isNotNull();
        assertThat(service.create("[{\"parent\": \"독일\",").fileError()).startsWith("JSON 형식이 올바르지 않습니다");
        assertThat(service.create("{\"parent\": \"독일\"}").fileError()).contains("배열");
        assertThat(service.create("[]").fileError()).isNotNull();

        String row = "{\"parent\": \"독일\", \"ko\": \"지역%d\", \"en\": \"Region %d\"}";
        String thirtyOne = java.util.stream.IntStream.rangeClosed(1, 31)
                .mapToObj(i -> row.formatted(i, i))
                .collect(Collectors.joining(",", "[", "]"));
        Result tooMany = service.create(thirtyOne);
        assertThat(tooMany.fileError()).contains("최대 30건");
        assertThat(tooMany.rows()).isEmpty();
        verify(mapper, never()).insertRegion(any());

        String thirty = java.util.stream.IntStream.rangeClosed(1, 30)
                .mapToObj(i -> row.formatted(i, i))
                .collect(Collectors.joining(",", "[", "]"));
        assertThat(service.create(thirty).created()).isEqualTo(30);
    }

    @Test
    void eachCreatedRowDropsTheCachedRegionTree() {
        new CountryCategoryService(mapper, mock(FileUploadService.class), cache).getById(20L);
        assertThat(cache.size()).isPositive();

        service.create("[{\"parent\": \"독일\", \"ko\": \"슈방가우\", \"en\": \"Schwangau\"}]");

        assertThat(cache.size()).isZero();
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
