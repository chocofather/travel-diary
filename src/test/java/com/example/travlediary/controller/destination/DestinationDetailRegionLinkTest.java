package com.example.travlediary.controller.destination;

import com.example.travlediary.dto.DestinationDetailDto;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.model.Destination;
import com.example.travlediary.service.category.CategoryService;
import com.example.travlediary.service.comment.DestinationCommentService;
import com.example.travlediary.service.destination.DestinationImageService;
import com.example.travlediary.service.destination.DestinationService;
import com.example.travlediary.service.category.CountryCategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 상세 페이지의 지역 링크(/destinations?type=..&region=..)가 목록의 필터 파라미터를 그대로 쓰는지 확인.
 * type 이 비어 있으면 목록이 지역 필터를 적용하지 못한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DestinationDetailRegionLinkTest {

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

    // 국내: 대한민국(1) > 서울(10) > 종로구(101) / 해외: 아시아(2) > 일본(20) > 도쿄(201)
    private static final Long KOREA_ROOT_ID = 1L;
    private static final Long ASIA_ROOT_ID = 2L;

    @BeforeEach
    void setUp() {
        controller = new DestinationController(destinationService, destinationImageService,
                categoryService, countryCategoryService, destinationCommentService);
        when(countryCategoryService.getDomesticRootIds()).thenReturn(List.of(KOREA_ROOT_ID));
    }

    @Test
    void domesticDetailBuildsADistrictFilteredListLink() {
        givenRegionTree(
                region(101L, "종로구", 4, 10L),
                region(10L, "서울", 3, KOREA_ROOT_ID),
                region(KOREA_ROOT_ID, "대한민국", 1, null));

        Model model = renderDetail(101L);

        assertThat(model.getAttribute("type")).isEqualTo("domestic");
        assertThat(model.getAttribute("regionId")).isEqualTo(101L);
        assertThat(model.getAttribute("regionName")).isEqualTo("종로구");
        // 템플릿이 만드는 링크: 종로구가 선택된 국내 목록
        assertThat(listLink(model)).isEqualTo("/destinations?type=domestic&region=101");
    }

    @Test
    void heroBreadcrumbPointsAtTheParentRegionList() {
        givenRegionTree(
                region(101L, "종로구", 4, 10L),
                region(10L, "서울", 3, KOREA_ROOT_ID),
                region(KOREA_ROOT_ID, "대한민국", 1, null));

        Model model = renderDetail(101L);

        // 상단 breadcrumb "서울" → 서울이 선택된 국내 목록 (하위 지역 UI 노출)
        assertThat(model.getAttribute("regionPath")).isEqualTo("서울");
        assertThat(model.getAttribute("regionPathId")).isEqualTo(10L);
        assertThat("/destinations?type=" + model.getAttribute("type")
                + "&region=" + model.getAttribute("regionPathId"))
                .isEqualTo("/destinations?type=domestic&region=10");
    }

    @Test
    void heroRegionLinksUseTheSameParameterSyntaxAsTheRecommendationLink() throws java.io.IOException {
        String hero = heroBlock();

        // 서울(regionPathId) / 종로구(regionId) 두 링크 모두 목록 필터 파라미터를 그대로 사용
        assertThat(hero)
                .contains("@{/destinations(type=${type},region=${regionPathId})}")
                .contains("@{/destinations(type=${type},region=${regionId})}")
                // 문자열 연결은 &reg; 엔티티로 깨지므로 남아 있으면 안 된다
                .doesNotContain("'&region='")
                .doesNotContain("href=\"#\"");
        // 카테고리(랜드마크)는 링크로 만들지 않는다
        assertThat(hero).contains("<span th:text=\"${categoryName}\">");
    }

    @Test
    void overseasDetailBuildsACityFilteredListLink() {
        givenRegionTree(
                region(201L, "도쿄", 3, 20L),
                region(20L, "일본", 2, ASIA_ROOT_ID),
                region(ASIA_ROOT_ID, "아시아", 1, null));

        Model model = renderDetail(201L);

        assertThat(model.getAttribute("type")).isEqualTo("overseas");
        assertThat(listLink(model)).isEqualTo("/destinations?type=overseas&region=201");
    }

    @Test
    void recommendationMoreLinkReusesTheListFilterParameters() throws java.io.IOException {
        String detail = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/templates/destination/detail.html"),
                java.nio.charset.StandardCharsets.UTF_8);
        String moreLink = detail.substring(detail.indexOf("similar-destinations-more"));

        // 지역명 문자열 검색이 아니라 목록이 쓰는 type/region ID 파라미터를 그대로 사용한다.
        // 문자열 연결(&region=)은 &reg; 엔티티로 깨지므로 파라미터 문법이어야 한다.
        assertThat(moreLink)
                .contains("@{/destinations(type=${type},region=${regionId})}")
                .doesNotContain("'&region='");
    }

    private String heroBlock() throws java.io.IOException {
        String detail = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/templates/destination/detail.html"),
                java.nio.charset.StandardCharsets.UTF_8);
        int start = detail.indexOf("class=\"destination-hero\"");
        int end = detail.indexOf("</section>", start);
        assertThat(start).isGreaterThan(0);
        assertThat(end).isGreaterThan(start);
        return detail.substring(start, end);
    }

    /** detail.html 의 지역 링크 표현식과 같은 방식으로 URL 을 만든다. */
    private String listLink(Model model) {
        return "/destinations?type=" + model.getAttribute("type")
                + "&region=" + model.getAttribute("regionId");
    }

    private Model renderDetail(Long regionId) {
        Destination destination = new Destination();
        destination.setId(7L);
        destination.setName("여행지");
        destination.setRegionId(regionId);

        DestinationDetailDto dto = new DestinationDetailDto();
        dto.setDestination(destination);

        when(destinationService.getDestinationDetailWithInfo(7L)).thenReturn(dto);
        when(destinationService.getSimilarDestinations(7L, 4)).thenReturn(List.of());
        when(destinationService.convertToDtoWithBookmark(List.of(), null)).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        controller.destinationDetail(7L, null, model);
        return model;
    }

    private void givenRegionTree(CountryCategory... regions) {
        for (CountryCategory region : regions) {
            when(countryCategoryService.getById(region.getId())).thenReturn(region);
        }
    }

    private CountryCategory region(Long id, String name, int depth, Long parentId) {
        CountryCategory region = new CountryCategory();
        region.setId(id);
        region.setRegionName(name);
        region.setDepth(depth);
        region.setParentId(parentId);
        return region;
    }
}
