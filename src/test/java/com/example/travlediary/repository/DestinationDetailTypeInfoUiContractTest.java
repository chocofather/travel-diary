package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여행지 상세의 타입별 정보 카드는 관광명소에서 시작한 하나의 디자인(.type-info-block)을 공유한다.
 */
class DestinationDetailTypeInfoUiContractTest {

    @Test
    void everyTypeSectionUsesTheSharedInfoCardDesign() throws IOException {
        String detail = detail();

        for (String block : new String[]{
                "attraction-info type-info-block",
                "accommodation-info type-info-block",
                "activity-info type-info-block",
                "restaurant-info type-info-block",
                "shop-info type-info-block"}) {
            assertThat(detail).as("shared design on %s", block).contains("class=\"" + block + "\"");
        }

        // 카드 / 2열 그리드 / info item 구조를 5개 타입이 모두 쓴다
        assertThat(countOf(detail, "class=\"info-card\"")).isEqualTo(5);
        assertThat(countOf(detail, "class=\"info-grid\"")).isEqualTo(5);
        assertThat(countOf(detail, "class=\"info-note\"")).isEqualTo(5);
        assertThat(countOf(detail, "class=\"info-note-label\"")).isEqualTo(5);

        // 옛 한 줄 목록형 블록은 더 이상 쓰지 않는다
        assertThat(detail).doesNotContain("detail-info-block");
    }

    @Test
    void longGuidanceStaysInTheFullWidthNoteUnderTheGrid() throws IOException {
        String detail = detail();

        assertThat(detail)
                .contains("<div class=\"info-note-text\" th:utext=\"${attractionGuideWithBr}\">")
                .contains("<div class=\"info-note-text\" th:text=\"${accommodationInfo.etc ?: '-'}\">")
                .contains("<div class=\"info-note-text\" th:text=\"${activityInfo.guide ?: '-'}\">")
                .contains("<div class=\"info-note-text\" th:text=\"${restaurantInfo.etc ?: '-'}\">")
                .contains("<div class=\"info-note-text\" th:text=\"${shopInfo.guide ?: '-'}\">");
    }

    @Test
    void homepageLinksKeepTheirHrefAndOpenSafely() throws IOException {
        String detail = detail();

        for (String info : new String[]{
                "attractionInfo", "accommodationInfo", "activityInfo", "restaurantInfo", "shopInfo"}) {
            assertThat(detail).as("href of %s", info)
                    .contains("th:href=\"${" + info + ".homepageUrl}\"");
        }
        // 긴 URL 대신 공통 문구를 쓰고, 새 창 + rel 속성은 관광지 구현을 그대로 따른다
        assertThat(countOf(detail, "class=\"info-item-link\"")).isEqualTo(5);
        assertThat(countOf(detail, "#{destination.detail.info.officialWebsite}")).isEqualTo(5);
        assertThat(detail).contains("target=\"_blank\" rel=\"noopener\"");
        assertThat(detail).doesNotContain("th:text=\"${accommodationInfo.homepageUrl}\"");
    }

    @Test
    void typeInfoCssIsSharedAndCollapsesToOneColumnOnNarrowScreens() throws IOException {
        String css = resource("/static/css/detail.css");

        assertThat(css)
                .contains(".type-info-block .info-card")
                .contains(".type-info-block .info-grid")
                .contains(".type-info-block .info-item")
                .contains(".type-info-block .info-item-icon")
                .contains(".type-info-block .info-item-label")
                .contains(".type-info-block .info-item-value")
                .contains(".type-info-block .info-note")
                .contains("grid-template-columns: repeat(2, minmax(0, 1fr))")
                .contains("@media (max-width: 720px)")
                .contains("grid-template-columns: minmax(0, 1fr)");
    }

    @Test
    void amenityRenderingIsUntouched() throws IOException {
        String detail = detail();

        assertThat(countOf(detail, "class=\"amenity-row\"")).isEqualTo(5);
        assertThat(countOf(detail, "${#strings.isEmpty(a.iconUrl)}")).isEqualTo(5);
        assertThat(detail).contains("class=\"amenities-list\"").contains("class=\"amenity-icon\"");
    }

    private String detail() throws IOException {
        return resource("/templates/destination/detail.html");
    }

    private int countOf(String source, String token) {
        int count = 0;
        int index = source.indexOf(token);
        while (index >= 0) {
            count++;
            index = source.indexOf(token, index + token.length());
        }
        return count;
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
