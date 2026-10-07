package com.tripbora.service.destinationimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tripbora.dto.DestinationForm;
import com.tripbora.dto.destinationimport.DestinationImportIssue;
import com.tripbora.dto.destinationimport.DestinationImportPreview;
import com.tripbora.dto.destinationimport.DestinationImportResult;
import com.tripbora.dto.destinationimport.DestinationImportRowStatus;
import com.tripbora.model.Amenity;
import com.tripbora.model.AmenityTranslation;
import com.tripbora.model.Category;
import com.tripbora.model.CountryCategory;
import com.tripbora.model.DestinationDuplicateIndexRow;
import com.tripbora.model.DestinationType;
import com.tripbora.repository.amenity.AmenityMapper;
import com.tripbora.repository.category.CategoryMapper;
import com.tripbora.repository.destination.DestinationMapper;
import com.tripbora.service.amenity.AmenityService;
import com.tripbora.service.category.CategoryService;
import com.tripbora.service.category.CountryCategoryService;
import com.tripbora.service.destination.DestinationDuplicateCheck;
import com.tripbora.service.destination.DestinationDuplicateReason;
import com.tripbora.service.destination.DestinationDuplicateService;
import com.tripbora.service.destination.DestinationDuplicateStatus;
import com.tripbora.service.destination.DestinationSaveOrchestrationService;
import com.tripbora.service.destination.DestinationService;
import com.tripbora.service.destination.DuplicateDestinationException;
import com.tripbora.service.kto.KtoTourApiException;
import com.tripbora.service.kto.KtoTourService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 여행지 JSON 일괄등록. 파서·검증·매핑·공통 중복 판별은 실제 클래스를 쓰고, DB 조회와 외부 API·저장만 가짜다.
 *
 * <p>지역(가짜 ID): 대한민국(7) → 서울(38) → 종로구(235)·중구(236) / 부산(39) → 중구(240),
 * 아시아(1) → 일본(8) → 후쿠오카(55)·도쿄(56) / 싱가포르(9, 하위 없음)</p>
 * <p>카테고리: 궁궐·역사(1)·전망대(2)=ATTRACTION, 한식(3)=RESTAURANTS, 디저트(4)=CAFE, 호텔(5)=ACCOMMODATION<br>
 * 편의시설: PARKING(10)=ATTRACTION·RESTAURANTS, WIFI(11)=CAFE, POOL(12)=ACCOMMODATION</p>
 */
class DestinationImportServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String GYEONGBOKGUNG = """
            {
              "key": "gyeongbokgung",
              "type": "ATTRACTION",
              "season": "ALL_SEASONS",
              "region": {"country": "대한민국", "city": "서울특별시", "district": "종로구"},
              "latitude": null,
              "longitude": null,
              "external": {"tourApiContentId": null, "wikidataQid": null, "googlePlaceId": null},
              "categories": ["궁궐·역사"],
              "mainCategory": null,
              "amenities": ["parking"],
              "translations": {
                "ko": {"name": "경복궁", "shortDescription": "조선 왕조의 법궁", "description": "  "},
                "en": {"name": "Gyeongbokgung Palace", "shortDescription": null, "description": null}
              },
              "info": {"openingHours": null, "guide": "궁궐 안내", "translations": {"en": {"guide": "Palace guide"}}},
              "evidence": []
            }
            """;

    private static final String FUKUOKA_TOWER = """
            {
              "key": "fukuoka-tower",
              "type": "ATTRACTION",
              "season": "ALL_SEASONS",
              "region": {"country": "Japan", "city": "후쿠오카", "district": null},
              "latitude": 33.59,
              "longitude": 130.35,
              "external": {"tourApiContentId": null, "wikidataQid": "q999001", "googlePlaceId": "ChIJ0000000000000000000000"},
              "categories": ["전망대"],
              "mainCategory": "전망대",
              "amenities": [],
              "translations": {
                "ko": {"name": "후쿠오카 타워", "shortDescription": null, "description": null},
                "ja": {"name": "福岡タワー", "shortDescription": null, "description": null}
              },
              "info": {"openingHours": "09:30~22:00"},
              "evidence": [{"url": "https://example.com/fukuoka-tower", "fields": ["openingHours"]}]
            }
            """;

    private final CountryCategoryService regions = mock(CountryCategoryService.class);
    private final CategoryMapper categoryMapper = mock(CategoryMapper.class);
    private final AmenityMapper amenityMapper = mock(AmenityMapper.class);
    private final DestinationMapper destinationMapper = mock(DestinationMapper.class);
    private final DestinationSaveOrchestrationService orchestration = mock(DestinationSaveOrchestrationService.class);
    private final DestinationService destinationService = mock(DestinationService.class);
    private final KtoTourService ktoTourService = mock(KtoTourService.class);
    private final List<DestinationDuplicateIndexRow> index = new ArrayList<>();
    private final Map<Long, CountryCategory> regionById = new HashMap<>();

    private DestinationImportService service;

    @BeforeEach
    void setUp() {
        region(7L, "대한민국", "South Korea", null);
        region(38L, "서울", "Seoul", 7L);
        region(235L, "종로구", "Jongno-gu", 38L);
        region(236L, "중구", "Jung-gu", 38L);
        region(39L, "부산", "Busan", 7L);
        region(240L, "중구", "Jung-gu", 39L);
        region(1L, "아시아", "Asia", null);
        region(8L, "일본", "Japan", 1L);
        region(55L, "후쿠오카", "Fukuoka", 8L);
        region(56L, "도쿄", "Tokyo", 8L);
        region(9L, "싱가포르", "Singapore", 1L);
        when(regions.getDomesticRootIds()).thenReturn(List.of(7L));
        when(regions.getOverseasRootIds()).thenReturn(List.of(1L));
        when(regions.getById(anyLong())).thenAnswer(invocation -> regionById.get(invocation.<Long>getArgument(0)));
        when(regions.getRegionsByParentId(anyLong())).thenAnswer(invocation -> regionById.values().stream()
                .filter(region -> invocation.<Long>getArgument(0).equals(region.getParentId()))
                .sorted(java.util.Comparator.comparing(CountryCategory::getId)).toList());

        Map<String, List<Category>> categories = Map.of(
                "ATTRACTION", List.of(category(1L, "궁궐·역사"), category(2L, "전망대")),
                "RESTAURANTS", List.of(category(3L, "한식")),
                "CAFE", List.of(category(4L, "디저트")),
                "ACCOMMODATION", List.of(category(5L, "호텔")));
        when(categoryMapper.findByDestinationType(anyString()))
                .thenAnswer(invocation -> categories.getOrDefault(invocation.<String>getArgument(0), List.of()));
        when(categoryMapper.findAll()).thenReturn(List.of(category(1L, "궁궐·역사"), category(2L, "전망대"),
                category(3L, "한식"), category(4L, "디저트"), category(5L, "호텔")));

        AmenityTranslation parking = amenity(10, "PARKING", "주차");
        Map<String, List<AmenityTranslation>> amenities = Map.of(
                "ATTRACTION", List.of(parking),
                "RESTAURANTS", List.of(parking),
                "CAFE", List.of(amenity(11, "WIFI", "와이파이")),
                "ACCOMMODATION", List.of(amenity(12, "POOL", "수영장")));
        when(amenityMapper.findTranslationsByDestinationTypeAndLang(anyString(), eq("ko")))
                .thenAnswer(invocation -> amenities.getOrDefault(invocation.<String>getArgument(0), List.of()));
        when(amenityMapper.findAll()).thenReturn(List.of(amenityCode(10, "PARKING"), amenityCode(11, "WIFI"),
                amenityCode(12, "POOL")));
        when(destinationMapper.findDuplicateIndex()).thenReturn(index);

        DestinationDuplicateService duplicateService = new DestinationDuplicateService(destinationMapper, regions);
        service = new DestinationImportService(new DestinationImportParser(),
                new DestinationImportMasterResolver(regions, new CategoryService(categoryMapper),
                        new AmenityService(amenityMapper, null, null)),
                new DestinationImportValidator(), new DestinationImportFormMapper(), duplicateService,
                orchestration, destinationService, ktoTourService);
    }

    /** 설계 예시 두 건(국내 경복궁·해외 후쿠오카 타워)을 한 파일로 미리보기 한다. */
    @Test
    void theDomesticAndOverseasExamplesPreviewAsRegistrableRows() throws Exception {
        DestinationImportPreview preview = service.preview(file(GYEONGBOKGUNG, FUKUOKA_TOWER));

        assertThat(preview.fileErrors()).isEmpty();
        assertThat(preview.total()).isEqualTo(2);
        assertThat(preview.summary().registrable()).isEqualTo(2);
        DestinationImportPreview.Row palace = preview.rows().get(0);
        assertThat(palace.status()).isEqualTo(DestinationImportRowStatus.NOT_REGISTERED);
        assertThat(palace.errors()).isEmpty();
        // 시·도 정식 명칭("서울특별시")은 지역 데이터 이름("서울")으로 맞춘다.
        assertThat(palace.regionLabel()).isEqualTo("대한민국 > 서울 > 종로구");
        assertThat(palace.sourceType()).isEqualTo(DestinationService.ADMIN_SOURCE_TYPE);
        // 대표를 적지 않았으면 저장과 같은 규칙(가장 작은 ID)으로 고른 값을 보여준다.
        assertThat(palace.mainCategory()).isEqualTo("궁궐·역사");
        assertThat(texts(palace.warnings())).anyMatch(text -> text.contains("좌표가 없어"));

        DestinationImportPreview.Row tower = preview.rows().get(1);
        assertThat(tower.status()).isEqualTo(DestinationImportRowStatus.NOT_REGISTERED);
        assertThat(tower.regionLabel()).isEqualTo("일본 > 후쿠오카");
        assertThat(tower.sourceType()).isEqualTo(DestinationService.WIKIDATA_SOURCE_TYPE);
        assertThat(tower.externalId()).isEqualTo("Q999001");
        assertThat(texts(tower.warnings())).anyMatch(text -> text.startsWith(
                "destinations[1].external.googlePlaceId: 외부 검증되지 않은 Place ID"));
        assertThat(tower.factFields()).contains("latitude", "longitude", "wikidataQid", "googlePlaceId", "openingHours");
        // 근거가 있으면 근거 없음 경고는 없다.
        assertThat(texts(tower.warnings())).noneMatch(text -> text.contains("evidence"));
        assertThat(tower.evidence()).singleElement()
                .satisfies(evidence -> assertThat(evidence.url()).isEqualTo("https://example.com/fukuoka-tower"));
        verifyNoInteractions(ktoTourService, orchestration);
    }

    @Test
    void rowsShowJsonPathsAndReasonsForEachMistake() throws Exception {
        List<ObjectNode> items = new ArrayList<>();
        ObjectNode wrongRegion = palace("r0", "궁0");
        ((ObjectNode) wrongRegion.get("region")).put("district", "없는구");
        items.add(wrongRegion);
        items.add(withCategories(palace("r1", "궁1"), "없는카테고리"));
        items.add(withCategories(palace("r2", "궁2"), "한식"));
        items.add(withAmenities(palace("r3", "궁3"), "NOPE"));
        items.add(withAmenities(palace("r4", "궁4"), "POOL"));
        ObjectNode restaurant = withAmenities(withCategories(palace("r5", "식당5"), "한식"));
        restaurant.put("type", "RESTAURANTS");
        restaurant.set("info", JSON.createObjectNode().put("openingHours", "가".repeat(71)));
        items.add(restaurant);
        items.add(palace("r6", "궁6").put("latitude", 37.5));
        ObjectNode badIds = palace("r7", "궁7");
        ((ObjectNode) badIds.get("external")).put("tourApiContentId", "abc");
        items.add(badIds);
        ObjectNode domesticForeignIds = palace("r8", "궁8");
        ((ObjectNode) domesticForeignIds.get("external")).put("wikidataQid", "Q123").put("googlePlaceId",
                "ChIJ0000000000000000000001");
        items.add(domesticForeignIds);
        ObjectNode overseasContentId = (ObjectNode) JSON.readTree(FUKUOKA_TOWER);
        overseasContentId.put("key", "r9");
        ((ObjectNode) overseasContentId.get("external")).put("tourApiContentId", "126508").putNull("wikidataQid")
                .put("googlePlaceId", "short");
        items.add(overseasContentId);
        ObjectNode unknownFields = palace("r10", "궁10").put("image", "a.jpg");
        ((ObjectNode) unknownFields.get("info")).put("openingHour", "09:00");
        items.add(unknownFields);
        ObjectNode emoji = palace("r11", "궁11");
        ((ObjectNode) emoji.get("info")).put("guide", "궁궐 🏯 안내");
        items.add(emoji);

        DestinationImportPreview preview = service.preview(file(items));

        assertThat(preview.rows()).extracting(DestinationImportPreview.Row::status)
                .containsOnly(DestinationImportRowStatus.INVALID);
        assertThat(preview.summary().invalid()).isEqualTo(12);
        assertThat(errors(preview, 0)).anyMatch(text -> text.startsWith(
                "destinations[0].region.district: '서울' 아래에 '없는구' 지역이 없습니다."));
        assertThat(errors(preview, 1)).anyMatch(text -> text.startsWith(
                "destinations[1].categories[0]: '없는카테고리' 카테고리가 없습니다."));
        assertThat(errors(preview, 2)).anyMatch(text -> text.startsWith(
                "destinations[2].categories[0]: '한식'은(는) ATTRACTION 여행지에서 쓸 수 없는 카테고리입니다."));
        assertThat(errors(preview, 3)).anyMatch(text -> text.startsWith(
                "destinations[3].amenities[0]: 'NOPE' 편의시설 code 가 없습니다."));
        assertThat(errors(preview, 4)).anyMatch(text -> text.startsWith(
                "destinations[4].amenities[0]: 'POOL'은(는) ATTRACTION 여행지에서 쓸 수 없는 편의시설입니다."));
        assertThat(errors(preview, 5)).contains("destinations[5].info.openingHours: 최대 64자, 현재 71자");
        assertThat(errors(preview, 6)).anyMatch(text -> text.startsWith(
                "destinations[6].longitude: latitude 와 longitude 는 함께"));
        assertThat(errors(preview, 7)).anyMatch(text -> text.startsWith(
                "destinations[7].external.tourApiContentId: 숫자만"));
        assertThat(errors(preview, 8)).anyMatch(text -> text.startsWith(
                        "destinations[8].external.wikidataQid: 국내 여행지에는 Wikidata QID 를 넣을 수 없습니다"))
                .anyMatch(text -> text.startsWith(
                        "destinations[8].external.googlePlaceId: 국내 여행지에는 Google Place ID 를 넣을 수 없습니다"));
        assertThat(errors(preview, 9)).anyMatch(text -> text.startsWith(
                        "destinations[9].external.tourApiContentId: 해외 여행지에는 TourAPI contentId 를 넣을 수 없습니다"))
                .anyMatch(text -> text.startsWith("destinations[9].external.googlePlaceId: 영문·숫자"));
        assertThat(errors(preview, 10)).anyMatch(text -> text.startsWith("destinations[10].image: 알 수 없는 필드입니다."))
                .anyMatch(text -> text.startsWith("destinations[10].info.openingHour: ATTRACTION 운영정보에 없는 필드입니다."));
        assertThat(errors(preview, 11)).anyMatch(text -> text.startsWith("destinations[11].info.guide: 이모지 등 4바이트 문자")
                && text.contains("attraction_info"));
        // 잘못된 형식의 contentId 는 TourAPI 에 묻지 않는다.
        verifyNoInteractions(ktoTourService);
    }

    @Test
    void overseasRegionsNeedACityOnlyWhenTheCountryHasOne() throws Exception {
        ObjectNode noCity = tower("t0", "타워0");
        ((ObjectNode) noCity.get("region")).putNull("city");
        ObjectNode countryOnly = tower("t1", "마리나 베이");
        ((ObjectNode) countryOnly.get("region")).put("country", "싱가포르").putNull("city");
        ObjectNode cityUnderLeaf = tower("t2", "가든스");
        ((ObjectNode) cityUnderLeaf.get("region")).put("country", "싱가포르").put("city", "주롱");

        DestinationImportPreview preview = service.preview(file(List.of(noCity, countryOnly, cityUnderLeaf)));

        assertThat(errors(preview, 0)).anyMatch(text -> text.startsWith(
                "destinations[0].region.city: '일본' 아래 지역을 지정해야 합니다. 사용할 수 있는 지역: 후쿠오카, 도쿄"));
        assertThat(preview.rows().get(1).status()).isEqualTo(DestinationImportRowStatus.NOT_REGISTERED);
        assertThat(preview.rows().get(1).regionLabel()).isEqualTo("싱가포르");
        assertThat(errors(preview, 2)).anyMatch(text -> text.startsWith(
                "destinations[2].region.city: '싱가포르' 아래에는 하위 지역이 없습니다."));
    }

    /** 이미 같은 contentId 로 등록된 곳은 등록됨, 이름·지역이 같은 관리자 등록 여행지는 중복 확인이다. */
    @Test
    void existingDestinationsMakeRowsRegisteredOrPossibleDuplicates() throws Exception {
        indexRow(501L, DestinationService.KTO_TOUR_API_SOURCE_TYPE, "126508", 235L, "경복궁");
        indexRow(502L, DestinationService.ADMIN_SOURCE_TYPE, null, 55L, "후쿠오카 타워");
        ObjectNode palace = palace("gyeongbokgung", "경복궁");
        ((ObjectNode) palace.get("external")).put("tourApiContentId", "126508");

        DestinationImportPreview preview = service.preview(file(List.of(palace, (ObjectNode) JSON.readTree(FUKUOKA_TOWER))));

        assertThat(preview.rows()).extracting(DestinationImportPreview.Row::status)
                .containsExactly(DestinationImportRowStatus.REGISTERED, DestinationImportRowStatus.POSSIBLE_DUPLICATE);
        assertThat(preview.rows().get(0).duplicate().destinationId()).isEqualTo(501L);
        assertThat(preview.rows().get(1).duplicate().destinationId()).isEqualTo(502L);
        assertThat(preview.rows().get(1).duplicate().reason()).isEqualTo(DestinationDuplicateReason.NAME_AND_REGION);
        assertThat(preview.summary().registered()).isEqualTo(1);
        assertThat(preview.summary().possibleDuplicate()).isEqualTo(1);
        // 이미 그 contentId 로 등록된 곳은 TourAPI 에 다시 묻지 않는다.
        verifyNoInteractions(ktoTourService);
    }

    @Test
    void duplicatesInsideOneFileAreFlaggedAgainstEarlierRows() throws Exception {
        ObjectNode first = palace("a", "경복궁");
        ((ObjectNode) first.get("external")).put("tourApiContentId", "126508");
        ObjectNode sameContent = palace("b", "경복궁 별관");
        ((ObjectNode) sameContent.get("external")).put("tourApiContentId", "126508");
        ObjectNode sameName = palace("c", "경복궁");
        when(ktoTourService.findTitle("126508")).thenReturn(Optional.of("경복궁"));

        DestinationImportPreview preview = service.preview(file(List.of(first, sameContent, sameName)));

        assertThat(preview.rows()).extracting(DestinationImportPreview.Row::status).containsExactly(
                DestinationImportRowStatus.NOT_REGISTERED, DestinationImportRowStatus.INVALID,
                DestinationImportRowStatus.POSSIBLE_DUPLICATE);
        assertThat(errors(preview, 1)).anyMatch(text -> text.startsWith(
                "destinations[1].external.tourApiContentId: 같은 파일의 #1 (a)"));
        assertThat(preview.rows().get(2).fileDuplicate().index()).isZero();
        assertThat(preview.rows().get(2).fileDuplicate().confirmed()).isFalse();
        assertThat(preview.rows().get(2).fileDuplicate().message()).contains("같은 파일의 #1 (a)");
        // 같은 contentId 는 TourAPI 에 한 번만 묻는다.
        verify(ktoTourService, times(1)).findTitle("126508");
    }

    @Test
    void aRepeatedKeyMakesTheLaterRowInvalid() throws Exception {
        DestinationImportPreview preview = service.preview(file(List.of(
                palace("same", "경복궁"), tower("same", "후쿠오카 타워"))));

        assertThat(preview.rows()).extracting(DestinationImportPreview.Row::status).containsExactly(
                DestinationImportRowStatus.NOT_REGISTERED, DestinationImportRowStatus.INVALID);
        assertThat(errors(preview, 1)).contains("destinations[1].key: 같은 key 'same'가 destinations[0]에도 있습니다. "
                + "key 는 파일 안에서 한 번만 쓸 수 있습니다.");
    }

    @Test
    void tourApiContentIdMustExistAndANameMismatchIsWarned() throws Exception {
        ObjectNode found = contentRow("a", "경복궁", "1001");
        ObjectNode renamed = contentRow("b", "창덕궁", "1002");
        ObjectNode missing = contentRow("c", "덕수궁", "1003");
        ObjectNode unreachable = contentRow("d", "경희궁", "1004");
        when(ktoTourService.findTitle("1001")).thenReturn(Optional.of("경복궁 (景福宮)"));
        when(ktoTourService.findTitle("1002")).thenReturn(Optional.of("창경궁"));
        when(ktoTourService.findTitle("1003")).thenReturn(Optional.empty());
        when(ktoTourService.findTitle("1004")).thenThrow(KtoTourApiException.upstreamFailure());

        DestinationImportPreview preview = service.preview(file(List.of(found, renamed, missing, unreachable)));

        assertThat(preview.rows()).extracting(DestinationImportPreview.Row::status).containsExactly(
                DestinationImportRowStatus.NOT_REGISTERED, DestinationImportRowStatus.NOT_REGISTERED,
                DestinationImportRowStatus.INVALID, DestinationImportRowStatus.INVALID);
        assertThat(preview.rows().get(0).sourceType()).isEqualTo(DestinationService.KTO_TOUR_API_SOURCE_TYPE);
        assertThat(texts(preview.rows().get(0).warnings())).noneMatch(text -> text.contains("TourAPI 이름"));
        assertThat(texts(preview.rows().get(1).warnings())).anyMatch(text -> text.startsWith(
                "destinations[1].external.tourApiContentId: TourAPI 이름 '창경궁'과 한국어 이름 '창덕궁'이 다릅니다."));
        assertThat(errors(preview, 2)).contains("destinations[2].external.tourApiContentId: TourAPI 에 없는 contentId 입니다: 1003");
        assertThat(errors(preview, 3)).anyMatch(text -> text.contains("TourAPI 에서 contentId 를 확인하지 못했습니다"));
    }

    @Test
    void fileLevelProblemsStopBeforeAnyRowIsBuilt() throws Exception {
        assertThat(fileErrors(service.preview("{\"version\": 1, \"destinations\": [")))
                .anyMatch(text -> text.startsWith("JSON 문법 오류입니다"));
        assertThat(fileErrors(service.preview("{\"version\": 1, \"version\": 1, \"destinations\": []}")))
                .anyMatch(text -> text.startsWith("같은 필드가 두 번 있습니다"));
        assertThat(fileErrors(service.preview("{\"version\": 2, \"destinations\": [" + GYEONGBOKGUNG + "], \"x\": 1}")))
                .contains("version: 지원하는 버전은 1뿐입니다.")
                .anyMatch(text -> text.startsWith("x: 알 수 없는 필드입니다."));
        List<ObjectNode> many = new ArrayList<>();
        for (int count = 0; count < 31; count++) many.add(palace("k" + count, "궁" + count));
        assertThat(fileErrors(service.preview(file(many))))
                .contains("destinations: 한 번에 최대 30건까지 등록할 수 있습니다. 현재 31건");
        assertThat(fileErrors(service.preview("{\"version\": 1, \"destinations\": [], \"meta\": {\"note\": \""
                + "가".repeat(400_000) + "\"}}")))
                .anyMatch(text -> text.startsWith("JSON은 최대 1MB까지"));
        assertThat(service.preview("{\"version\": 1, \"destinations\": [").rows()).isEmpty();
    }

    /** 등록 요청은 미리보기를 믿지 않고 그 항목을 다시 검증·매핑한 뒤 기존 등록 경로로 넘긴다. */
    @Test
    void registeringRevalidatesTheItemAndHandsAFormToTheExistingSavePath() throws Exception {
        when(orchestration.registerImportedDestination(any(), eq(7L))).thenReturn(88L);

        DestinationImportResult result = service.register(request(1, true, (ObjectNode) JSON.readTree(FUKUOKA_TOWER)), 7L);

        assertThat(result.status()).isEqualTo(DestinationImportResult.SUCCESS);
        assertThat(result.destinationId()).isEqualTo(88L);
        assertThat(result.index()).isEqualTo(1);
        assertThat(result.key()).isEqualTo("fukuoka-tower");
        ArgumentCaptor<DestinationForm> captor = ArgumentCaptor.forClass(DestinationForm.class);
        verify(orchestration).registerImportedDestination(captor.capture(), eq(7L));
        DestinationForm form = captor.getValue();
        assertThat(form.getType()).isEqualTo(DestinationType.ATTRACTION);
        assertThat(form.getSeason()).isEqualTo("ALL_SEASONS");
        assertThat(form.getRegionId()).isEqualTo(55L);
        assertThat(form.getLatitude()).isEqualByComparingTo(new BigDecimal("33.59"));
        assertThat(form.getWikidataQid()).isEqualTo("Q999001");
        assertThat(form.getKtoContentId()).isNull();
        assertThat(form.getGooglePlaceId()).isEqualTo("ChIJ0000000000000000000000");
        assertThat(form.getCategoryIds()).containsExactly(2L);
        assertThat(form.getMainCategoryId()).isEqualTo(2L);
        assertThat(form.getAttractionInfo().getOpeningHours()).isEqualTo("09:30~22:00");
        assertThat(form.getTranslations()).extracting("languageCode").containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        assertThat(form.getTranslations().get(0).getName()).isEqualTo("후쿠오카 타워");
        assertThat(form.getTranslations().get(2).getName()).isEqualTo("福岡タワー");
        assertThat(form.isAllowPossibleDuplicate()).isTrue();
        // 이미지·사진 선택은 채우지 않는다.
        assertThat(form.getImages()).isNull();
        assertThat(form.getCommonsSelectedPhotosJson()).isEmpty();
    }

    @Test
    void aDomesticAdminItemMapsToTheSameFormShapeAsTheRegistrationForm() throws Exception {
        when(orchestration.registerImportedDestination(any(), eq(7L))).thenReturn(90L);
        ObjectNode cafe = palace("cafe", "카페 한옥");
        cafe.put("type", "CAFE");
        withCategories(cafe, "한식", "디저트").put("mainCategory", "디저트");
        withAmenities(cafe, "wifi");
        cafe.set("info", JSON.readTree("{\"mainMenu\": \"약과\", \"seatCount\": 20, \"reservation\": true,"
                + " \"translations\": {\"en\": {\"mainMenu\": \"Yakgwa\"}}}"));

        assertThat(service.register(request(0, false, palace("gyeongbokgung", "경복궁")), 7L).status())
                .isEqualTo(DestinationImportResult.SUCCESS);
        assertThat(service.register(request(1, false, cafe), 7L).status()).isEqualTo(DestinationImportResult.SUCCESS);

        ArgumentCaptor<DestinationForm> captor = ArgumentCaptor.forClass(DestinationForm.class);
        verify(orchestration, times(2)).registerImportedDestination(captor.capture(), eq(7L));
        DestinationForm palace = captor.getAllValues().get(0);
        assertThat(palace.getRegionId()).isEqualTo(235L);
        assertThat(palace.getCategoryIds()).containsExactly(1L);
        // 대표를 적지 않았으면 비워 저장이 기존 규칙으로 고르게 한다.
        assertThat(palace.getMainCategoryId()).isNull();
        assertThat(palace.getAttractionAmenityIds()).containsExactly(10);
        assertThat(palace.getKtoContentId()).isNull();
        assertThat(palace.getWikidataQid()).isNull();
        // 공백뿐인 설명은 빈 값이다.
        assertThat(palace.getTranslations().get(0).getDescription()).isEmpty();
        assertThat(palace.getAttractionInfo().getGuide()).isEqualTo("궁궐 안내");
        assertThat(palace.getAttractionInfoTranslations().get(0).getLanguageCode()).isEqualTo("en");
        assertThat(palace.getAttractionInfoTranslations().get(0).getGuide()).isEqualTo("Palace guide");
        assertThat(palace.isAllowPossibleDuplicate()).isFalse();
        // CAFE 는 현재 구조대로 restaurant_info / restaurant_amenities 를 쓴다.
        DestinationForm cafeForm = captor.getAllValues().get(1);
        assertThat(cafeForm.getType()).isEqualTo(DestinationType.CAFE);
        assertThat(cafeForm.getCategoryIds()).containsExactly(3L, 4L);
        assertThat(cafeForm.getMainCategoryId()).isEqualTo(4L);
        assertThat(cafeForm.getRestaurantInfo().getMainMenu()).isEqualTo("약과");
        assertThat(cafeForm.getRestaurantInfo().getSeatCount()).isEqualTo(20);
        assertThat(cafeForm.getRestaurantInfo().getReservation()).isTrue();
        assertThat(cafeForm.getRestaurantAmenityIds()).containsExactly(11);
        assertThat(cafeForm.getRestaurantInfoTranslations().get(0).getMainMenu()).isEqualTo("Yakgwa");
        assertThat(cafeForm.getAttractionInfo()).isNull();
    }

    @Test
    void aTamperedItemIsRejectedAgainWithoutSaving() throws Exception {
        DestinationImportResult result = service.register(
                request(2, false, withCategories(palace("x", "경복궁"), "한식")), 7L);

        assertThat(result.status()).isEqualTo(DestinationImportResult.INVALID);
        assertThat(result.index()).isEqualTo(2);
        assertThat(texts(result.errors())).anyMatch(text -> text.startsWith("destinations[2].categories[0]:"));
        verifyNoInteractions(orchestration);
    }

    @Test
    void aDomesticContentIdIsCheckedOnTourApiAgainBeforeSaving() throws Exception {
        when(ktoTourService.findTitle("1003")).thenReturn(Optional.empty());

        DestinationImportResult result = service.register(request(0, false, contentRow("a", "덕수궁", "1003")), 7L);

        assertThat(result.status()).isEqualTo(DestinationImportResult.INVALID);
        verifyNoInteractions(orchestration);
    }

    /** 확인하지 않은 중복 가능성은 저장 직전 판별에서 돌아오고, 확정 중복은 등록됨으로 돌아온다. */
    @Test
    void saveTimeDuplicatesComeBackAsResultsWithoutSaving() throws Exception {
        DestinationDuplicateCheck possible = new DestinationDuplicateCheck(DestinationDuplicateStatus.POSSIBLE_DUPLICATE,
                DestinationDuplicateReason.NAME_AND_REGION, 502L, "경복궁", null, "같은 이름 · 같은 지역");
        DestinationDuplicateCheck registered = new DestinationDuplicateCheck(DestinationDuplicateStatus.REGISTERED,
                DestinationDuplicateReason.GOOGLE_PLACE_ID, 503L, "후쿠오카 타워", null, "같은 Google Place ID");
        when(orchestration.registerImportedDestination(any(), eq(7L)))
                .thenThrow(new DuplicateDestinationException(possible))
                .thenThrow(new DuplicateDestinationException(registered));

        DestinationImportResult unapproved = service.register(request(0, false, palace("a", "경복궁")), 7L);
        DestinationImportResult confirmed = service.register(request(1, true, (ObjectNode) JSON.readTree(FUKUOKA_TOWER)), 7L);

        assertThat(unapproved.status()).isEqualTo(DestinationImportResult.POSSIBLE_DUPLICATE);
        assertThat(unapproved.destinationId()).isEqualTo(502L);
        assertThat(unapproved.duplicate().message()).isEqualTo("같은 이름 · 같은 지역");
        assertThat(confirmed.status()).isEqualTo(DestinationImportResult.REGISTERED);
        assertThat(confirmed.destinationId()).isEqualTo(503L);
    }

    /** 여행지마다 따로 저장한다. 한 건이 실패해도 다른 건의 결과는 그대로다. */
    @Test
    void oneFailureDoesNotUndoOtherRegistrations() throws Exception {
        when(orchestration.registerImportedDestination(any(), eq(7L)))
                .thenReturn(91L)
                .thenThrow(new DataAccessResourceFailureException("db down"))
                .thenReturn(93L);

        List<DestinationImportResult> results = List.of(
                service.register(request(0, false, palace("a", "궁가")), 7L),
                service.register(request(1, false, palace("b", "궁나")), 7L),
                service.register(request(2, false, withCategories(palace("c", "궁다"), "없는카테고리")), 7L),
                service.register(request(3, false, palace("d", "궁라")), 7L));

        assertThat(results).extracting(DestinationImportResult::status).containsExactly(
                DestinationImportResult.SUCCESS, DestinationImportResult.FAILED,
                DestinationImportResult.INVALID, DestinationImportResult.SUCCESS);
        assertThat(results).extracting(DestinationImportResult::destinationId).containsExactly(91L, null, null, 93L);
        verify(orchestration, times(3)).registerImportedDestination(any(), eq(7L));
        verify(orchestration, never()).registerDestination(any(), any(), any());
    }

    @Test
    void aMalformedRegisterRequestIsRejected() {
        DestinationImportResult result = service.register("{\"index\": 99, \"item\": {}}", 7L);

        assertThat(result.status()).isEqualTo(DestinationImportResult.INVALID);
        assertThat(texts(result.errors())).anyMatch(text -> text.startsWith("index:"));
        verifyNoInteractions(orchestration);
    }

    private ObjectNode palace(String key, String name) throws Exception {
        ObjectNode item = (ObjectNode) JSON.readTree(GYEONGBOKGUNG);
        item.put("key", key);
        ((ObjectNode) item.get("translations").get("ko")).put("name", name);
        // 행마다 이름을 다르게 두려고 영어 이름은 뺀다(같은 영어 이름이면 파일 안 중복으로 잡힌다).
        ((ObjectNode) item.get("translations")).remove("en");
        return item;
    }

    private ObjectNode tower(String key, String name) throws Exception {
        ObjectNode item = (ObjectNode) JSON.readTree(FUKUOKA_TOWER);
        item.put("key", key);
        ((ObjectNode) item.get("translations").get("ko")).put("name", name);
        ((ObjectNode) item.get("external")).putNull("wikidataQid").putNull("googlePlaceId");
        item.putNull("latitude");
        item.putNull("longitude");
        return item;
    }

    private ObjectNode contentRow(String key, String name, String contentId) throws Exception {
        ObjectNode item = palace(key, name);
        ((ObjectNode) item.get("external")).put("tourApiContentId", contentId);
        return item;
    }

    private static ObjectNode withCategories(ObjectNode item, String... names) {
        ArrayNode array = item.putArray("categories");
        for (String name : names) array.add(name);
        return item;
    }

    private static ObjectNode withAmenities(ObjectNode item, String... codes) {
        ArrayNode array = item.putArray("amenities");
        for (String code : codes) array.add(code);
        return item;
    }

    private static String file(String... items) {
        return "{\"version\": 1, \"destinations\": [" + String.join(",", items) + "]}";
    }

    private static String file(List<ObjectNode> items) {
        ObjectNode root = JSON.createObjectNode().put("version", 1);
        root.putArray("destinations").addAll(items);
        return root.toString();
    }

    private static String request(int index, boolean allowPossibleDuplicate, ObjectNode item) {
        ObjectNode root = JSON.createObjectNode().put("index", index).put("allowPossibleDuplicate", allowPossibleDuplicate);
        root.set("item", item);
        return root.toString();
    }

    private static List<String> texts(List<DestinationImportIssue> issues) {
        return issues.stream().map(DestinationImportIssue::text).toList();
    }

    private static List<String> errors(DestinationImportPreview preview, int row) {
        return texts(preview.rows().get(row).errors());
    }

    private static List<String> fileErrors(DestinationImportPreview preview) {
        return texts(preview.fileErrors());
    }

    private void region(Long id, String name, String nameEn, Long parentId) {
        CountryCategory region = new CountryCategory();
        region.setId(id);
        region.setRegionName(name);
        region.setNameEn(nameEn);
        region.setParentId(parentId);
        regionById.put(id, region);
    }

    private void indexRow(Long id, String sourceType, String externalId, Long regionId, String name) {
        DestinationDuplicateIndexRow row = new DestinationDuplicateIndexRow();
        row.setDestinationId(id);
        row.setSourceType(sourceType);
        row.setExternalContentId(externalId);
        row.setRegionId(regionId);
        row.setLanguageCode("ko");
        row.setName(name);
        index.add(row);
    }

    private static Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }

    private static AmenityTranslation amenity(int id, String code, String name) {
        AmenityTranslation translation = new AmenityTranslation();
        translation.setAmenityId(id);
        translation.setCode(code);
        translation.setName(name);
        translation.setLanguageCode("ko");
        return translation;
    }

    private static Amenity amenityCode(int id, String code) {
        Amenity amenity = new Amenity();
        amenity.setId(id);
        amenity.setCode(code);
        return amenity;
    }
}
