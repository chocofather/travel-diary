package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TravelInfoPublicUiContractTest {

    @Test
    void listUsesPublicLayoutAndUnifiedAccessiblePillFiltersWithoutApplyForm()
            throws IOException {
        String template = resource("/templates/travel-info/list.html");

        assertThat(template)
                .contains("layout/main :: layout")
                .contains("/css/travel-info.css")
                .contains("/js/travel-info-list.js")
                .contains("contentType=${contentType}", "categoryId=${categoryIds}")
                .contains("scope='DOMESTIC'", "scope='INTERNATIONAL'", "size=${pageSize}")
                .contains("data-filter-name=\"scope\"")
                .contains("data-filter-name=\"categoryId\"")
                .contains("th:each=\"category : ${categories}\"")
                .contains("travel-info-filter-pill", "travel-info-category-pills")
                .contains("<span class=\"travel-info-filter-label\">주제</span>")
                .contains("type=\"button\"")
                .contains("aria-current=", "aria-pressed=", "data-travel-info-reset")
                .contains("#lists.isEmpty(categoryIds)", "#lists.contains(categoryIds, category.id)")
                .contains("travel-info/fragments/list-results :: results")
                .doesNotContain("정보 유형", "data-filter-name=\"contentType\"")
                .doesNotContain("contentType='GENERAL'", "contentType='FESTIVAL'")
                .doesNotContain("<select", "type=\"submit\"", ">적용<", ">카테고리<");
    }

    @Test
    void resultsFragmentKeepsCardsPlaceholderPeriodPaginationAndNoDeadDetailLink()
            throws IOException {
        String fragment = resource("/templates/travel-info/fragments/list-results.html");

        assertThat(fragment)
                .contains("th:fragment=\"results\"")
                .contains("id=\"travel-info-results\"")
                .contains("travel-info-grid", "travel-info-card", "travel-info-thumbnail")
                .contains("travel-info-thumbnail-placeholder", "등록된 이미지가 없습니다")
                .contains("travel-info-period", "info.startDate", "info.endDate")
                .contains("travel-info-pagination", "page=${pageNumber}")
                .doesNotContain("@{/travel-info/{id}", "href=\"/travel-info/10\"");
    }

    @Test
    void cssProvidesFourThreeTwoOneColumnGridAndSixteenByNineImages() throws IOException {
        String css = resource("/static/css/travel-info.css");

        assertThat(css)
                .contains("grid-template-columns: repeat(4, minmax(0, 1fr))")
                .contains("grid-template-columns: repeat(3, minmax(0, 1fr))")
                .contains("grid-template-columns: repeat(2, minmax(0, 1fr))")
                .contains("grid-template-columns: minmax(0, 1fr)")
                .contains("@media (max-width: 1100px)")
                .contains("@media (max-width: 880px)")
                .contains("@media (max-width: 620px)")
                .contains("aspect-ratio: 16 / 9")
                .contains("object-fit: cover")
                .contains(".travel-info-thumbnail-placeholder")
                .contains(".travel-info-results.is-loading")
                .contains(".travel-info-category-pills .travel-info-filter-pill.is-active::after")
                .doesNotContain("/images/default.png");
    }

    @Test
    void javascriptFetchesFragmentsSynchronizesHistoryCancelsStaleRequestsAndFallsBack()
            throws IOException {
        String javascript = resource("/static/js/travel-info-list.js");

        assertThat(javascript)
                .contains("const SINGLE_FILTER_NAMES = ['scope']")
                .contains("const CATEGORY_FILTER_NAME = 'categoryId'")
                .contains("const url = new URL(selectedUrl.href)")
                .contains("new URL('/travel-info', window.location.origin)")
                .contains("searchParams.getAll(CATEGORY_FILTER_NAME)")
                .contains("searchParams.delete(CATEGORY_FILTER_NAME)")
                .contains("searchParams.append(CATEGORY_FILTER_NAME, categoryId)")
                .contains("selectedCategoryIds.has(value)")
                .contains("selectedCategoryIds.delete(value)")
                .contains("selectedCategoryIds.clear()")
                .contains("pill.setAttribute('aria-pressed', String(isActive))")
                .contains("fetch(")
                .contains("'X-Requested-With': 'XMLHttpRequest'")
                .contains("window.history.pushState")
                .contains("window.addEventListener('popstate'")
                .contains("new AbortController()")
                .contains("activeController.abort()")
                .contains(".travel-info-pagination a")
                .contains("replaceResults(await response.text())")
                .contains("window.location.assign(url.href)");
    }

    @Test
    void hiddenContentTypeIsPreservedByScopeCategoryAndPaginationLinks() throws IOException {
        String template = resource("/templates/travel-info/list.html");
        String fragment = resource("/templates/travel-info/fragments/list-results.html");

        assertThat(template)
                .contains("@{/travel-info(contentType=${contentType},categoryId=${categoryIds},size=${pageSize})}")
                .contains("categoryId=${categoryIds}");
        assertThat(fragment)
                .contains("scope=${scope},contentType=${contentType},categoryId=${categoryIds}")
                .contains("page=${currentPage - 1}")
                .contains("page=${pageNumber}")
                .contains("page=${currentPage + 1}");
    }

    @Test
    void headerLinksTravelInfoMainAndPublicFilterShortcuts() throws IOException {
        String header = resource("/templates/fragments/header.html");

        assertThat(header)
                .contains("href=\"/travel-info\">여행정보</a>")
                .contains("href=\"/travel-info\">전체</a>")
                .contains("href=\"/travel-info?scope=DOMESTIC\"")
                .contains("href=\"/travel-info?scope=INTERNATIONAL\"")
                .contains("href=\"/travel-info?contentType=FESTIVAL\"");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
