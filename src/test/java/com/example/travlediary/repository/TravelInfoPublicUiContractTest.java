package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TravelInfoPublicUiContractTest {

    @Test
    void listUsesPublicLayoutAccessibleTitleSearchAndUnifiedPillFilters()
            throws IOException {
        String template = resource("/templates/travel-info/list.html");

        assertThat(template)
                .contains("layout/main :: layout")
                .contains("/css/travel-info.css")
                .contains("/js/travel-info-list.js")
                .contains("method=\"get\"", "action=\"/travel-info\"")
                .contains("role=\"search\"", "data-travel-info-search")
                .contains("type=\"search\"", "name=\"keyword\"", "maxlength=\"100\"")
                .contains("th:value=\"${keyword}\"")
                .contains("data-travel-info-search-input", "data-travel-info-search-clear")
                .contains("aria-label=\"검색어 지우기\"", "type=\"submit\"")
                .contains("name=\"scope\"", "name=\"contentType\"", "name=\"categoryId\"")
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
                .doesNotContain("<select", ">적용<", ">카테고리<");
    }

    @Test
    void resultsFragmentKeepsCardsAndAddsAccessibleDetailLinkWithServerReturnUrl()
            throws IOException {
        String fragment = resource("/templates/travel-info/fragments/list-results.html");

        assertThat(fragment)
                .contains("th:fragment=\"results\"")
                .contains("id=\"travel-info-results\"")
                .contains("travel-info-grid", "travel-info-card", "travel-info-thumbnail")
                .contains("travel-info-thumbnail-placeholder", "등록된 이미지가 없습니다")
                .contains("travel-info-period", "info.startDate", "info.endDate")
                .contains("travel-info-pagination", "keyword=${keyword}", "page=${pageNumber}")
                .contains("th:if=\"${keyword != null}\"")
                .contains("th:text=\"|‘${keyword}’|\"")
                .doesNotContain("th:utext=\"${keyword}\"")
                .contains("class=\"travel-info-card-link\"")
                .contains("@{/travel-info/{id}(id=${info.id},returnUrl=${listUrl})}");
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
                .contains(".travel-info-search-control", ".travel-info-search-submit")
                .contains(".travel-info-search-clear", ".travel-info-search-control:focus-within")
                .contains(".travel-info-category-pills .travel-info-filter-pill.is-active::after")
                .contains(".travel-info-card-link::after")
                .contains("position: absolute", "inset: 0")
                .contains(".travel-info-card:focus-within")
                .doesNotContain("/images/default.png");
    }

    @Test
    void detailUsesPublicLayoutSharedQuillContentAllFestivalPeriodsAndNoThumbnailHero()
            throws IOException {
        String detail = resource("/templates/travel-info/detail.html");
        String css = resource("/static/css/travel-info-detail.css");

        assertThat(detail)
                .contains("layout/main :: layout")
                .contains("/css/quill-content.css")
                .contains("/css/travel-info-detail.css")
                .contains("travelInfo.categoryName", "travelInfo.title")
                .contains("travelInfo.scope.name()", "travelInfo.contentType.name()")
                .contains("travelInfo.createdAt", "travelInfo.views")
                .contains("th:if=\"${travelInfo.updated}\"")
                .contains("!#lists.isEmpty(travelInfo.periods)")
                .contains("th:each=\"period : ${travelInfo.periods}\"")
                .contains("period.startDate", "period.endDate")
                .contains("class=\"travel-info-detail-content rich-text-content\"")
                .contains("th:utext=\"${travelInfo.content}\"")
                .contains("th:href=\"${listUrl}\"", "목록으로")
                .doesNotContain("thumbnail", "info_images", "travelInfo.id", "userId", "categoryId");
        assertThat(css)
                .contains("width: min(960px, calc(100% - 40px))")
                .contains("overflow-wrap: anywhere")
                .contains(".travel-info-detail-content img")
                .contains("max-width: 100%", "height: auto")
                .contains("@media (max-width: 720px)")
                .contains("@media (max-width: 430px)");
    }

    @Test
    void javascriptFetchesFragmentsSynchronizesHistoryCancelsStaleRequestsAndFallsBack()
            throws IOException {
        String javascript = resource("/static/js/travel-info-list.js");

        assertThat(javascript)
                .contains("const SINGLE_FILTER_NAMES = ['scope']")
                .contains("const CATEGORY_FILTER_NAME = 'categoryId'")
                .contains("const KEYWORD_PARAMETER_NAME = 'keyword'")
                .contains("const SEARCH_DEBOUNCE_MS = 200")
                .contains("const KEYWORD_MAX_LENGTH = 100")
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
                .contains("searchParams.set(KEYWORD_PARAMETER_NAME, keyword)")
                .contains("searchParams.delete(KEYWORD_PARAMETER_NAME)")
                .contains("url.searchParams.delete('page')")
                .contains("searchForm.addEventListener('submit'")
                .contains("searchInput.addEventListener('input'")
                .contains("searchInput.addEventListener('compositionstart'")
                .contains("searchInput.addEventListener('compositionupdate'")
                .contains("searchInput.addEventListener('compositionend'")
                .doesNotContain("isComposing")
                .contains("window.setTimeout", "SEARCH_DEBOUNCE_MS")
                .contains("if (input.value !== keyword)")
                .contains("syncSearchUi(url)")
                .contains("searchInput.value = ''")
                .contains(".travel-info-pagination a")
                .contains("replaceResults(await response.text())")
                .contains("window.location.assign(url.href)");
    }

    @Test
    void hiddenContentTypeIsPreservedByScopeCategoryAndPaginationLinks() throws IOException {
        String template = resource("/templates/travel-info/list.html");
        String fragment = resource("/templates/travel-info/fragments/list-results.html");

        assertThat(template)
                .contains("@{/travel-info(keyword=${keyword},contentType=${contentType},categoryId=${categoryIds},size=${pageSize})}")
                .contains("categoryId=${categoryIds}");
        assertThat(fragment)
                .contains("keyword=${keyword},scope=${scope},contentType=${contentType},categoryId=${categoryIds}")
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
