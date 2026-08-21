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
 * 지역 선택 2차 UX 계약.
 * 첫 단계(국내 시/도, 해외 대륙)는 버튼 grid, 하위 단계는 검색 + 기존 select 이며
 * 저장 계약(hidden regionId)과 기존 selector id 는 그대로다.
 */
class AdminDestinationRegionStepUiContractTest {

    private static final String REGION_SELECTOR_ASSET = "/js/region-selector.js?v=20260822-3";

    private static final String[] FORMS = {
            "/templates/admin/destinations/create.html",
            "/templates/admin/destinations/edit.html"
    };

    @Test
    void firstStepsOfferButtonGridsAndDeeperStepsOfferSearch() throws IOException {
        for (String path : FORMS) {
            String form = resource(path);
            Document page = Jsoup.parse(form);

            // 해외 대륙(step 0) / 국내 시/도(step 1) 는 버튼 grid 를 갖는다
            List<String> chipSteps = page.select("[data-region-chips]").eachAttr("data-region-chips");
            assertThat(chipSteps).as("form %s", path).containsExactly("0", "1");

            // 국가/도시/하위 지역은 검색 입력을 갖는다
            List<String> searchSteps = page.select("[data-region-search]").eachAttr("data-region-search");
            assertThat(searchSteps).containsExactly("1", "2", "3");

            // 기존 selector / 저장 계약은 그대로
            assertThat(page.select("select#continent, select#country, select#city, select#district"))
                    .hasSize(4);
            assertThat(form)
                    .contains("id=\"regionIdHidden\"")
                    .contains("th:field=\"*{regionId}\"")
                    .contains("data-domestic-root-id=${domesticRootId}")
                    .contains("data-region-mode-button=\"domestic\"")
                    .contains("data-region-mode-button=\"overseas\"")
                    .contains(REGION_SELECTOR_ASSET);
        }
    }

    @Test
    void selectorBuildsButtonsFromLoadedOptionsAndKeepsSelectsAsTheSourceOfTruth() throws IOException {
        String script = resource("/static/js/region-selector.js");

        assertThat(script)
                // 버튼은 서버에서 받아 select 에 채운 option 으로 만든다 (이름/ID 하드코딩 없음)
                .contains("data-region-chips")
                .contains("Array.from(select.options)")
                .contains("aria-pressed")
                // 클릭하면 기존 select 값을 바꾸고 기존 change 흐름을 그대로 탄다
                .contains("select.value = ")
                .contains("handleManualChange")
                // 하위 단계 검색은 이름 부분검색이며 선택값은 유지한다
                .contains("data-region-search")
                .contains("toLocaleLowerCase")
                .contains("includes(")
                // 부모가 바뀌면 검색어도 초기화한다
                .contains("clearSearch")
                // 항목이 없는 단계는 감춘다
                .contains("hidden =")
                // 기존 계약 유지
                .contains("applyRegionPath")
                .contains("initialRegionPath")
                .contains("regionSelectionChanged")
                .contains("domesticRootId")
                // 지역 이름/숫자 ID 하드코딩 금지
                .doesNotContain("서울")
                .doesNotContain("아시아")
                .doesNotContain("== 7")
                .doesNotContain("=== 7");
    }

    @Test
    void chipGridAndSearchHaveCompactAdminStyling() throws IOException {
        String css = resource("/static/css/destination-create.css");

        assertThat(css)
                .contains(".admin-region-chips")
                .contains(".admin-region-chip")
                .contains(".admin-region-search")
                // 첫 단계는 버튼만, 하위 단계는 검색 + select 만 보여 준다
                .contains("[data-region-view=\"chips\"]")
                .contains("[data-region-view=\"select\"]")
                .contains("flex-wrap: wrap");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
