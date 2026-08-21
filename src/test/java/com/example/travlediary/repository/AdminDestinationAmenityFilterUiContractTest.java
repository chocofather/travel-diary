package com.example.travlediary.repository;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 편의시설 필터 UI 계약.
 * 필터는 보이기/숨기기 전용이며 checkbox 바인딩과 체크 상태를 바꾸지 않는다.
 */
class AdminDestinationAmenityFilterUiContractTest {

    private static final String[] FORMS = {
            "/templates/admin/destinations/create.html",
            "/templates/admin/destinations/edit.html"
    };

    @Test
    void filterOffersAllAndEveryDestinationTypeGroup() throws IOException {
        String fragment = resource("/templates/admin/destinations/fragments/amenity-filter.html");
        Document filters = Jsoup.parse(fragment);

        List<String> values = filters.select("[data-amenity-filter]").eachAttr("data-amenity-filter");
        assertThat(values).containsExactly(
                "ALL", "ATTRACTION", "RESTAURANTS CAFE", "ACCOMMODATION", "ACTIVITY", "SHOP");
        assertThat(fragment)
                .contains(">전체<").contains(">여행지<").contains(">식당·카페<")
                .contains(">숙소<").contains(">액티비티<").contains(">쇼핑<")
                // 키보드 접근 가능한 버튼 + 상태 표시
                .contains("type=\"button\"")
                .contains("aria-pressed");
    }

    @Test
    void everyAmenityBlockRendersTheWholeListWithItsDestinationTypeTags() throws IOException {
        for (String path : FORMS) {
            String form = resource(path);
            Document page = Jsoup.parse(form);

            // 편의시설 블록 5개 모두 필터를 포함하고 전체 목록을 한 번만 렌더링한다
            assertThat(page.select("[data-amenity-field]")).as("form %s", path).hasSize(5);
            assertThat(page.select("[data-amenity-field] [data-amenity-options]")).hasSize(5);
            assertThat(form)
                    .contains("amenity-filter :: filters")
                    .contains("data-amenity-types=${amenityTypeTags.get(amenity.amenityId)}")
                    .doesNotContain("${attractionAmenities}")
                    .doesNotContain("${accommodationAmenities}")
                    .doesNotContain("${restaurantAmenities}")
                    .doesNotContain("${activityAmenities}")
                    .doesNotContain("${shopAmenities}");
            assertThat(form.split("\\$\\{allAmenities}", -1).length - 1)
                    .as("allAmenities 렌더링 횟수 %s", path).isEqualTo(5);

            // 저장 필드 계약은 그대로
            assertThat(form)
                    .contains("*{attractionAmenityIds}")
                    .contains("*{accommodationAmenityIds}")
                    .contains("*{restaurantAmenityIds}")
                    .contains("*{activityAmenityIds}")
                    .contains("*{shopAmenityIds}");
        }
    }

    @Test
    void eachBlockDeclaresTheDefaultFilterOfItsOwnDestinationType() throws IOException {
        for (String path : FORMS) {
            Document page = Jsoup.parse(resource(path));

            List<String> defaults = page.select("[data-amenity-field]")
                    .stream()
                    .map(field -> field.attr("data-amenity-default-filter"))
                    .toList();
            assertThat(defaults).as("form %s", path).containsExactlyInAnyOrder(
                    "ATTRACTION", "ACCOMMODATION", "RESTAURANTS CAFE", "ACTIVITY", "SHOP");

            // 음식점/카페 블록은 두 유형의 합집합을 기본 필터로 쓴다
            Element restaurantField = page.select("[data-amenity-field]").stream()
                    .filter(field -> field.html().contains("restaurantAmenityIds"))
                    .findFirst()
                    .orElseThrow();
            assertThat(restaurantField.attr("data-amenity-default-filter"))
                    .isEqualTo("RESTAURANTS CAFE");
        }
    }

    @Test
    void filterScriptOnlyTogglesVisibilityAndFollowsTheDestinationType() throws IOException {
        String script = resource("/static/js/admin-destination-amenity-filter.js");

        assertThat(script)
                .contains("data-amenity-field")
                .contains("data-amenity-filter")
                .contains("data-amenity-option")
                // dataset 접근자 (data-amenity-types / data-amenity-default-filter)
                .contains("dataset.amenityTypes")
                .contains("dataset.amenityDefaultFilter")
                .contains("aria-pressed")
                // 여행지 유형이 바뀌면 기본 필터로 되돌린다
                .contains("select[name=\"type\"]")
                .contains("addEventListener(\"change\"")
                // 체크 상태/값은 건드리지 않는다
                .doesNotContain(".checked =")
                .doesNotContain("checked = false")
                .doesNotContain("removeAttribute(\"checked\")")
                .doesNotContain("value =")
                // 이름 기반 하드코딩 금지
                .doesNotContain("수영장")
                .doesNotContain("주차장");

        for (String path : FORMS) {
            assertThat(resource(path)).as("form %s", path)
                    .contains("admin-destination-amenity-filter.js");
        }
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
