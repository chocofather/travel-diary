package com.example.travlediary.controller.destination;

import com.example.travlediary.dto.DestinationDetailDto;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.model.Destination;
import com.example.travlediary.service.category.CategoryService;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.comment.DestinationCommentService;
import com.example.travlediary.service.destination.DestinationImageService;
import com.example.travlediary.service.destination.DestinationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** 해외 상세 지도는 Maps Embed API iframe 으로 표시한다. 키는 환경변수로만 주입한다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverseasMapEmbedTest {

    @Mock
    private DestinationService destinationService;
    @Mock
    private DestinationImageService destinationImageService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private CountryCategoryService countryCategoryService;
    @Mock
    private DestinationCommentService destinationCommentService;

    private DestinationController controller;

    @BeforeEach
    void setUp() {
        controller = new DestinationController(destinationService, destinationImageService,
                categoryService, countryCategoryService, destinationCommentService);
        when(countryCategoryService.getDomesticRootIds()).thenReturn(List.of(1L));
    }

    @Test
    void overseasDestinationUsesPlaceModeWithNameCityCountrySoAMarkerShowsOnLoad() {
        givenApiKey("test-key");

        Model model = renderDetail(overseasCityRegion("후쿠오카"), "후쿠오카 타워",
                new BigDecimal("33.5932"), new BigDecimal("130.3514"));

        // q 는 좌표가 아니라 "여행지명, 도시, 국가" (마커 표시 + 장소 조회 실패 방지)
        String expectedQuery = URLEncoder.encode("후쿠오카 타워, 후쿠오카, 일본", StandardCharsets.UTF_8);
        assertThat(model.getAttribute("overseasMapEmbedUrl"))
                .isEqualTo("https://www.google.com/maps/embed/v1/place"
                        + "?key=test-key"
                        + "&q=" + expectedQuery
                        + "&center=33.5932%2C130.3514&zoom=15");
    }

    @Test
    void overseasDestinationWithoutANameFallsBackToViewModeInsteadOfAFailingLookup() {
        givenApiKey("test-key");

        Model model = renderDetail(overseasRegion(), "  ",
                new BigDecimal("35.6586"), new BigDecimal("139.7454"));

        assertThat(model.getAttribute("overseasMapEmbedUrl"))
                .isEqualTo("https://www.google.com/maps/embed/v1/view"
                        + "?key=test-key&center=35.6586%2C139.7454&zoom=15");
    }

    @Test
    void overseasDestinationGetsAGoogleMapsLinkEvenWithoutAnApiKey() {
        givenApiKey("");

        Model model = renderDetail(overseasRegion(), new BigDecimal("35.6586"), new BigDecimal("139.7454"));

        assertThat(model.getAttribute("overseasMapLinkUrl"))
                .isEqualTo("https://www.google.com/maps/search/?api=1&query=35.6586%2C139.7454");
    }

    @Test
    void withoutAnApiKeyOrAnyLocationDataTheMapAreaStaysEmpty() {
        // 환경변수 미설정 → iframe 자체를 만들지 않는다
        givenApiKey("");
        assertThat(renderDetail(overseasRegion(), new BigDecimal("35.6"), new BigDecimal("139.7"))
                .getAttribute("overseasMapEmbedUrl")).isNull();

        // 장소명도 좌표도 없으면 만들 수 있는 URL 이 없다
        givenApiKey("test-key");
        assertThat(renderDetail(overseasRegion(), null, null, null)
                .getAttribute("overseasMapEmbedUrl")).isNull();
    }

    @Test
    void placeModeStillMarksTheSpotWhenCoordinatesAreMissing() {
        givenApiKey("test-key");

        Model model = renderDetail(overseasCityRegion("후쿠오카"), "후쿠오카 타워", null, null);

        // 좌표가 없어도 장소 검색으로 마커는 표시된다 (center/zoom 만 생략)
        assertThat(model.getAttribute("overseasMapEmbedUrl"))
                .isEqualTo("https://www.google.com/maps/embed/v1/place"
                        + "?key=test-key"
                        + "&q=" + URLEncoder.encode("후쿠오카 타워, 후쿠오카, 일본", StandardCharsets.UTF_8));
        // 크게 보기 링크는 좌표 기반이라 이때는 없다
        assertThat(model.getAttribute("overseasMapLinkUrl")).isNull();
    }

    @Test
    void domesticDestinationKeepsKakaoAndGetsNoEmbedUrl() {
        givenApiKey("test-key");

        CountryCategory jongno = region(101L, "종로구", 4, 10L, "KR-11-110");
        when(countryCategoryService.getById(10L)).thenReturn(region(10L, "서울", 3, 1L, "KR-11"));
        when(countryCategoryService.getById(1L)).thenReturn(region(1L, "대한민국", 1, null, "KR"));

        Model model = renderDetail(jongno, new BigDecimal("37.5"), new BigDecimal("126.9"));

        assertThat(model.getAttribute("countryCode")).isEqualTo("KR");
        assertThat(model.getAttribute("overseasMapEmbedUrl")).isNull();
        assertThat(model.getAttribute("overseasMapLinkUrl")).isNull();
    }

    @Test
    void mapMarkupSplitsKakaoAndEmbedAndNeverHardcodesAKey() throws IOException {
        String detail = readFile("src/main/resources/templates/destination/detail.html");
        String mapSection = detail.substring(detail.indexOf("<section class=\"map\">"),
                detail.indexOf("</section>", detail.indexOf("<section class=\"map\">")));

        assertThat(mapSection)
                // 국내는 기존 Kakao 컨테이너 유지
                .contains("th:if=\"${countryCode == 'KR'}\"")
                .contains("id=\"map\"")
                // 해외는 Embed iframe
                .contains("th:src=\"${overseasMapEmbedUrl}\"")
                .contains("allowfullscreen")
                .contains("referrerpolicy=\"no-referrer-when-downgrade\"")
                // URL 이 없으면 iframe 을 만들지 않는다
                .contains("overseasMapEmbedUrl == null")
                // 지도 아래 새 탭 링크
                .contains("th:href=\"${overseasMapLinkUrl}\"")
                .contains("Google 지도에서 크게 보기")
                .contains("target=\"_blank\" rel=\"noopener\"");

        // 키 문자열은 소스 어디에도 없어야 한다
        assertThat(detail).doesNotContain("AIza");
        assertThat(readFile("src/main/resources/static/js/map-init.js"))
                .doesNotContain("AIza")
                .doesNotContain("maps.googleapis.com")
                .doesNotContain("google.maps");
        assertThat(readFile("src/main/java/com/example/travlediary/controller/destination/"
                + "DestinationController.java"))
                .doesNotContain("AIza")
                .contains("${GOOGLE_MAPS_API_KEY:}");
    }

    private void givenApiKey(String key) {
        ReflectionTestUtils.setField(controller, "googleMapsApiKey", key);
    }

    private CountryCategory overseasRegion() {
        return overseasCityRegion("도쿄");
    }

    /** 해외 도시(depth 3) → 국가(depth 2) → 대륙(depth 1) 트리 */
    private CountryCategory overseasCityRegion(String cityName) {
        when(countryCategoryService.getById(20L)).thenReturn(region(20L, "일본", 2, 2L, "JP"));
        when(countryCategoryService.getById(2L)).thenReturn(region(2L, "아시아", 1, null, "AS"));
        return region(201L, cityName, 3, 20L, "JP-13");
    }

    private Model renderDetail(CountryCategory region, BigDecimal latitude, BigDecimal longitude) {
        return renderDetail(region, "여행지", latitude, longitude);
    }

    private Model renderDetail(CountryCategory region, String name,
                               BigDecimal latitude, BigDecimal longitude) {
        when(countryCategoryService.getById(region.getId())).thenReturn(region);

        Destination destination = new Destination();
        destination.setId(7L);
        destination.setName(name);
        destination.setRegionId(region.getId());
        destination.setLatitude(latitude);
        destination.setLongitude(longitude);

        DestinationDetailDto dto = new DestinationDetailDto();
        dto.setDestination(destination);

        when(destinationService.getDestinationDetailWithInfo(7L)).thenReturn(dto);
        when(destinationService.getSimilarDestinations(7L, 4)).thenReturn(List.of());
        when(destinationService.convertToDtoWithBookmark(List.of(), null)).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        controller.destinationDetail(7L, null, model);
        return model;
    }

    private CountryCategory region(Long id, String name, int depth, Long parentId, String code) {
        CountryCategory region = new CountryCategory();
        region.setId(id);
        region.setRegionName(name);
        region.setDepth(depth);
        region.setParentId(parentId);
        region.setCode(code);
        return region;
    }

    private String readFile(String path) throws IOException {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}
