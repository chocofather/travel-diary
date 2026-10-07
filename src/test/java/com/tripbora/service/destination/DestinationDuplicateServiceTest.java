package com.tripbora.service.destination;

import com.tripbora.model.CountryCategory;
import com.tripbora.model.DestinationDuplicateIndexRow;
import com.tripbora.repository.destination.DestinationMapper;
import com.tripbora.service.category.CountryCategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.tripbora.service.destination.DestinationDuplicateStatus.NOT_REGISTERED;
import static com.tripbora.service.destination.DestinationDuplicateStatus.POSSIBLE_DUPLICATE;
import static com.tripbora.service.destination.DestinationDuplicateStatus.REGISTERED;
import static com.tripbora.service.destination.DestinationService.ADMIN_SOURCE_TYPE;
import static com.tripbora.service.destination.DestinationService.KTO_TOUR_API_SOURCE_TYPE;
import static com.tripbora.service.destination.DestinationService.WIKIDATA_SOURCE_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 공통 중복 판별 규칙. 지역 계층(이 테스트의 가짜 ID):
 * 대한민국(1) → 서울(10) → 종로구(11), 중구(12) / 부산(20) → 해운대구(21)
 * 아시아(100) → 일본(110) → 도쿄(111), 오사카(112)
 */
class DestinationDuplicateServiceTest {

    private final DestinationMapper mapper = mock(DestinationMapper.class);
    private final CountryCategoryService regions = mock(CountryCategoryService.class);
    private final DestinationDuplicateService service = new DestinationDuplicateService(mapper, regions);
    private final List<DestinationDuplicateIndexRow> index = new ArrayList<>();
    private final Map<Long, Long> parents = new HashMap<>();

    @BeforeEach
    void setUp() {
        region(1L, null);
        region(10L, 1L);
        region(11L, 10L);
        region(12L, 10L);
        region(20L, 1L);
        region(21L, 20L);
        region(100L, null);
        region(110L, 100L);
        region(111L, 110L);
        region(112L, 110L);
        when(regions.getById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            if (!parents.containsKey(id)) return null;
            CountryCategory category = new CountryCategory();
            category.setId(id);
            category.setParentId(parents.get(id));
            return category;
        });
        when(mapper.findDuplicateIndex()).thenReturn(index);
    }

    @Test
    void anAdminDestinationWithoutExternalIdIsFoundByTheSameNameNearby() {
        // 관리자가 직접 등록한 경복궁: external_content_id 없음
        destination(1L, ADMIN_SOURCE_TYPE, null, null, 11L, "37.5796", "126.9770", "ko", "경복궁");

        DestinationDuplicateCheck check = service.check(tourApi("126508", "경복궁", 11L, "37.5789", "126.9768"));

        assertThat(check.status()).isEqualTo(POSSIBLE_DUPLICATE);
        assertThat(check.reason()).isEqualTo(DestinationDuplicateReason.NAME_AND_NEARBY);
        assertThat(check.destinationId()).isEqualTo(1L);
        assertThat(check.destinationName()).isEqualTo("경복궁");
        assertThat(check.distanceMeters()).isLessThan(200);
        assertThat(check.message()).contains("가까운 위치");
    }

    @Test
    void theSameProviderExternalIdIsAConfirmedDuplicate() {
        destination(2L, KTO_TOUR_API_SOURCE_TYPE, "126508", null, 11L, "37.5796", "126.9770", "ko", "경복궁");
        destination(3L, WIKIDATA_SOURCE_TYPE, "Q243", null, 111L, null, null, "ko", "에펠탑");

        DestinationDuplicateCheck tourApi = service.check(tourApi("126508", "다른 이름", null, null, null));
        DestinationDuplicateCheck wikidata = service.check(wikidata("Q243", List.of("Eiffel Tower"), null, null, null));

        assertThat(tourApi.status()).isEqualTo(REGISTERED);
        assertThat(tourApi.reason()).isEqualTo(DestinationDuplicateReason.EXTERNAL_CONTENT_ID);
        assertThat(tourApi.destinationId()).isEqualTo(2L);
        assertThat(wikidata.status()).isEqualTo(REGISTERED);
        assertThat(wikidata.destinationId()).isEqualTo(3L);
    }

    @Test
    void anExternalIdFromAnotherProviderIsNotAConfirmedDuplicate() {
        destination(2L, KTO_TOUR_API_SOURCE_TYPE, "243", null, 11L, null, null, "ko", "경복궁");

        DestinationDuplicateCheck check = service.check(
                new DestinationDuplicateQuery(WIKIDATA_SOURCE_TYPE, "243", null, List.of("에펠탑"), 111L, null, null));

        assertThat(check.status()).isEqualTo(NOT_REGISTERED);
    }

    @Test
    void theSameGooglePlaceIdOnBothSidesIsAConfirmedDuplicate() {
        destination(4L, ADMIN_SOURCE_TYPE, null, "ChIJLU7jZClu5kcR4PcOOO6p3I0", 111L, null, null, "ko", "에펠탑");

        DestinationDuplicateCheck check = service.check(new DestinationDuplicateQuery(null, null,
                " ChIJLU7jZClu5kcR4PcOOO6p3I0 ", List.of("전혀 다른 이름"), null, null, null));
        DestinationDuplicateCheck noPlaceId = service.check(new DestinationDuplicateQuery(null, null,
                null, List.of("전혀 다른 이름"), null, null, null));

        assertThat(check.status()).isEqualTo(REGISTERED);
        assertThat(check.reason()).isEqualTo(DestinationDuplicateReason.GOOGLE_PLACE_ID);
        assertThat(noPlaceId.status()).isEqualTo(NOT_REGISTERED);
    }

    @Test
    void anAdminDestinationWithoutCoordinatesIsFoundByAnyLanguageNameInTheSameRegionHierarchy() {
        // 관리자가 국가(일본)까지만 고르고 좌표 없이 영어 이름도 넣어 둔 여행지
        destination(5L, ADMIN_SOURCE_TYPE, null, null, 110L, null, null, "ko", "도쿄 타워");
        destination(5L, ADMIN_SOURCE_TYPE, null, null, 110L, null, null, "en", "Tokyo Tower");

        // Wikidata 후보는 도시(도쿄) 지역에서 찾았고 좌표가 있다. 영어 표기만 대소문자·공백이 다르다.
        DestinationDuplicateCheck check = service.check(
                wikidata("Q193454", List.of("TOKYO  TOWER", "東京タワー"), 111L, "35.6586", "139.7454"));

        assertThat(check.status()).isEqualTo(POSSIBLE_DUPLICATE);
        assertThat(check.reason()).isEqualTo(DestinationDuplicateReason.NAME_AND_REGION);
        assertThat(check.destinationId()).isEqualTo(5L);
        // 표시 이름은 한국어를 먼저 쓴다.
        assertThat(check.destinationName()).isEqualTo("도쿄 타워");
    }

    @Test
    void theSameNameInAnotherRegionIsNotADuplicate() {
        destination(6L, ADMIN_SOURCE_TYPE, null, null, 11L, null, null, "ko", "중앙시장");

        // 서울 종로구 ↔ 부산 해운대구: 겹치는 지역이 최상위(대한민국)뿐이면 같은 지역으로 보지 않는다.
        DestinationDuplicateCheck otherCity = service.check(tourApi("900001", "중앙시장", 21L, null, null));
        // 같은 서울 안이라도 형제 구는 다른 지역이다.
        DestinationDuplicateCheck siblingDistrict = service.check(tourApi("900002", "중앙시장", 12L, null, null));
        // 지역도 좌표도 모르는 후보는 이름만으로 중복이라 하지 않는다.
        DestinationDuplicateCheck nameOnly = service.check(tourApi("900003", "중앙시장", null, null, null));
        // 시/도까지만 고른 기존 여행지와 그 아래 구의 후보는 같은 지역 계층이다.
        DestinationDuplicateCheck parentRegion = service.check(tourApi("900004", "중앙시장", 10L, null, null));

        assertThat(otherCity.status()).isEqualTo(NOT_REGISTERED);
        assertThat(siblingDistrict.status()).isEqualTo(NOT_REGISTERED);
        assertThat(nameOnly.status()).isEqualTo(NOT_REGISTERED);
        assertThat(parentRegion.status()).isEqualTo(POSSIBLE_DUPLICATE);
    }

    @Test
    void theSameNameFarAwayIsNotADuplicateEvenInTheSameRegion() {
        destination(7L, ADMIN_SOURCE_TYPE, null, null, 10L, "37.5796", "126.9770", "ko", "한강공원");

        // 같은 서울이지만 약 9km 떨어진 곳. 양쪽 좌표가 있으면 좌표로만 본다.
        DestinationDuplicateCheck check = service.check(tourApi("900005", "한강공원", 11L, "37.5172", "127.0473"));

        assertThat(check.status()).isEqualTo(NOT_REGISTERED);
    }

    @Test
    void aNearbyPlaceWithADifferentNameIsNotADuplicate() {
        destination(8L, ADMIN_SOURCE_TYPE, null, null, 11L, "37.5796", "126.9770", "ko", "경복궁");

        // 경복궁 바로 옆(수백 m)이지만 다른 여행지
        DestinationDuplicateCheck check = service.check(tourApi("126512", "국립고궁박물관", 11L, "37.5765", "126.9752"));

        assertThat(check.status()).isEqualTo(NOT_REGISTERED);
    }

    @Test
    void candidatesWithoutAnyIdentifiersAreComparedOnlyByNameAndLocation() {
        destination(9L, ADMIN_SOURCE_TYPE, null, null, 11L, null, null, "ko", "국립민속박물관");

        DestinationDuplicateCheck sameRegion = service.check(new DestinationDuplicateQuery(
                null, null, null, List.of("국립 민속 박물관"), 11L, null, null));
        DestinationDuplicateCheck unknown = service.check(new DestinationDuplicateQuery(
                null, null, null, List.of(), 11L, null, null));

        assertThat(sameRegion.status()).isEqualTo(POSSIBLE_DUPLICATE);
        assertThat(unknown.status()).isEqualTo(NOT_REGISTERED);
    }

    @Test
    void theNearestSameNameDestinationIsReportedAndTheIndexIsReadOncePerBatch() {
        destination(10L, ADMIN_SOURCE_TYPE, null, null, 11L, "37.5800", "126.9800", "ko", "경복궁");
        destination(11L, ADMIN_SOURCE_TYPE, null, null, 11L, "37.5797", "126.9771", "ko", "경복궁(景福宮)");

        List<DestinationDuplicateCheck> checks = service.checkAll(List.of(
                tourApi("126508", "경복궁", 11L, "37.5796", "126.9770"),
                tourApi("900006", "새로운 여행지", 11L, "37.5796", "126.9770")));

        assertThat(checks).extracting(DestinationDuplicateCheck::status)
                .containsExactly(POSSIBLE_DUPLICATE, NOT_REGISTERED);
        assertThat(checks.get(0).destinationId()).isEqualTo(11L);
        verify(mapper, times(1)).findDuplicateIndex();
    }

    @Test
    void namesAreNormalizedOnlyForSpacingCaseBracketsAndSymbols() {
        assertThat(DestinationNameNormalizer.normalize(" 경복궁 (景福宮) ")).isEqualTo("경복궁");
        assertThat(DestinationNameNormalizer.normalize("N서울타워[남산타워]")).isEqualTo("n서울타워");
        assertThat(DestinationNameNormalizer.normalize("N 서울 타워")).isEqualTo("n서울타워");
        assertThat(DestinationNameNormalizer.normalize("Ｔｏｋｙｏ　Ｔｏｗｅｒ")).isEqualTo("tokyotower");
        assertThat(DestinationNameNormalizer.normalize("St. Peter's Basilica")).isEqualTo("stpetersbasilica");
        // 괄호만으로 된 이름은 괄호 안 글자를 쓴다.
        assertThat(DestinationNameNormalizer.normalize("(가나)")).isEqualTo("가나");
        // 단어가 더 붙은 이름은 다른 이름이다.
        assertThat(DestinationNameNormalizer.normalize("서울 경복궁"))
                .isNotEqualTo(DestinationNameNormalizer.normalize("경복궁"));
        assertThat(DestinationNameNormalizer.normalize("A")).isNull();
        assertThat(DestinationNameNormalizer.normalize("  ")).isNull();
    }

    private void region(Long id, Long parentId) {
        parents.put(id, parentId);
    }

    private void destination(Long id, String sourceType, String externalId, String placeId, Long regionId,
                             String latitude, String longitude, String languageCode, String name) {
        DestinationDuplicateIndexRow row = new DestinationDuplicateIndexRow();
        row.setDestinationId(id);
        row.setSourceType(sourceType);
        row.setExternalContentId(externalId);
        row.setGooglePlaceId(placeId);
        row.setRegionId(regionId);
        row.setLatitude(latitude == null ? null : new BigDecimal(latitude));
        row.setLongitude(longitude == null ? null : new BigDecimal(longitude));
        row.setLanguageCode(languageCode);
        row.setName(name);
        index.add(row);
    }

    private DestinationDuplicateQuery tourApi(String contentId, String title, Long regionId,
                                              String latitude, String longitude) {
        return new DestinationDuplicateQuery(KTO_TOUR_API_SOURCE_TYPE, contentId, null, List.of(title), regionId,
                latitude == null ? null : new BigDecimal(latitude),
                longitude == null ? null : new BigDecimal(longitude));
    }

    private DestinationDuplicateQuery wikidata(String qid, List<String> names, Long regionId,
                                               String latitude, String longitude) {
        return new DestinationDuplicateQuery(WIKIDATA_SOURCE_TYPE, qid, null, names, regionId,
                latitude == null ? null : new BigDecimal(latitude),
                longitude == null ? null : new BigDecimal(longitude));
    }
}
