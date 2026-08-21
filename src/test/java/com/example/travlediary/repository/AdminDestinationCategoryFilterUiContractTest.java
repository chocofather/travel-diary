package com.example.travlediary.repository;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카테고리 필터 UI 계약.
 * 유형 필터와 기존 검색은 AND 로 함께 적용되고, 체크 상태는 건드리지 않는다.
 */
class AdminDestinationCategoryFilterUiContractTest {

    private static final String[] FORMS = {
            "/templates/admin/destinations/create.html",
            "/templates/admin/destinations/edit.html"
    };

    @Test
    void filterOffersAllAndEveryDestinationTypeGroup() throws IOException {
        String fragment = resource("/templates/admin/destinations/fragments/category-filter.html");
        Document filters = Jsoup.parse(fragment);

        List<String> values = filters.select("[data-category-filter]").eachAttr("data-category-filter");
        assertThat(values).containsExactly(
                "ALL", "ATTRACTION", "RESTAURANTS CAFE", "ACCOMMODATION", "ACTIVITY", "SHOP");
        assertThat(fragment)
                .contains(">전체<").contains(">여행지<").contains(">식당·카페<")
                .contains(">숙소<").contains(">액티비티<").contains(">쇼핑<")
                .contains("type=\"button\"")
                .contains("aria-pressed")
                // 편의시설 필터와 상태가 섞이지 않도록 별도 네임스페이스를 쓴다
                .doesNotContain("data-amenity-filter");
    }

    @Test
    void categorySectionKeepsOneCheckboxPerCategoryWithItsTypeTags() throws IOException {
        for (String path : FORMS) {
            String form = resource(path);
            Document page = Jsoup.parse(form);

            assertThat(page.select("[data-category-select]")).as("form %s", path).hasSize(1);
            assertThat(page.select("[data-category-select] [data-category-option]")).hasSize(1);
            assertThat(form)
                    .contains("category-filter :: filters")
                    .contains("data-category-types=${categoryTypeTags.get(cat.id)}")
                    // 기존 검색과 binding 은 그대로
                    .contains("data-category-search")
                    .contains("*{categoryIds}")
                    .contains("th:value=\"${cat.id}\"")
                    // 전체 목록을 한 번만 렌더링한다
                    .contains("th:each=\"cat : ${categories}\"");
            assertThat(form.split("th:each=\"cat : \\$\\{categories}\"", -1).length - 1)
                    .as("categories 렌더링 횟수 %s", path).isEqualTo(1);
        }
    }

    @Test
    void categorySelectDeclaresTheDefaultFilterAndStaysIndependentFromAmenities() throws IOException {
        for (String path : FORMS) {
            Document page = Jsoup.parse(resource(path));

            // 기본 필터는 현재 여행지 유형에서 JS 가 정하므로 컨테이너가 기준점을 갖는다
            assertThat(page.select("[data-category-select][data-category-mode]"))
                    .as("form %s", path).hasSize(1);
            // 카테고리 영역 안에 편의시설 필터가 섞이지 않는다
            assertThat(page.select("[data-category-select] [data-amenity-filter]")).isEmpty();
            assertThat(page.select("[data-amenity-field] [data-category-filter]")).isEmpty();
        }
    }

    @Test
    void categoryScriptCombinesTypeFilterAndSearchWithoutTouchingChecked() throws IOException {
        String script = resource("/static/js/admin-destination-category-select.js");

        assertThat(script)
                .contains("data-category-filter")
                .contains("dataset.categoryTypes")
                .contains("data-category-search")
                // 유형 + 검색어 AND 조건을 한 곳에서 계산한다
                .contains("option.hidden =")
                .contains("keyword")
                // 여행지 유형이 바뀌면 기본 필터로 전환
                .contains("select[name=\"type\"]")
                .contains("aria-pressed")
                // 이름 기반 하드코딩 금지
                .doesNotContain("맛집")
                .doesNotContain("호텔")
                // checked 를 바꾸는 곳은 기존 칩 해제 버튼 한 곳뿐이다
                .doesNotContain("checked = true");
        assertThat(script.split("checked = false", -1).length - 1)
                .as("checked 해제 지점 수").isEqualTo(1);

        for (String path : FORMS) {
            assertThat(resource(path)).as("form %s", path)
                    .contains("admin-destination-category-select.js?v=");
        }
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
