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
        String categoryFilter = resource("/templates/travel-info/fragments/category-filter.html");

        assertThat(template)
                .contains("layout/main :: layout")
                .contains("/css/travel-info.css")
                .contains("/js/travel-info-list.js")
                .contains("/js/travel-info-bookmark.js")
                .contains("method=\"get\"", "action=\"/travel-info\"")
                .contains("role=\"search\"", "data-travel-info-search")
                .contains("type=\"search\"", "name=\"keyword\"", "maxlength=\"100\"")
                .contains("th:value=\"${keyword}\"")
                .contains("data-travel-info-search-input", "data-travel-info-search-clear")
                .contains("aria-label=\"검색어 지우기\"", "type=\"submit\"")
                .contains("name=\"scope\"", "name=\"contentType\"", "name=\"categoryId\"")
                .contains("contentType=${contentType}", "categoryId=${categoryIds}")
                .contains("scope='DOMESTIC'", "scope='INTERNATIONAL'", "size=${pageSize}")
                .contains("data-filter-name=\"primary\"")
                .contains("data-filter-value=\"FESTIVAL\"")
                .contains("contentType='GENERAL'")
                .contains("contentType='FESTIVAL'")
                .contains("travel-info-filter-pill", "aria-current=", "data-travel-info-reset")
                .contains("travel-info/fragments/list-results :: results")
                .doesNotContain("정보 유형", "data-filter-name=\"contentType\"")
                .doesNotContain("<select", ">적용<", ">카테고리<");
        assertThat(categoryFilter)
                .contains("id=\"travel-info-category-filter\"")
                .contains("data-filter-name=\"categoryId\"")
                .contains("th:each=\"category : ${categories}\"")
                .contains("travel-info-filter-pill", "travel-info-category-pills")
                .contains("'축제·행사 분류' : '주제'")
                .contains("type=\"button\"")
                .contains("aria-pressed=")
                .contains("#lists.isEmpty(categoryIds)", "#lists.contains(categoryIds, category.id)");
    }

    @Test
    void resultsFragmentKeepsCardsAndAddsAccessibleDetailLinkWithServerReturnUrl()
            throws IOException {
        String fragment = resource("/templates/travel-info/fragments/list-results.html");
        String asyncFragment = resource("/templates/travel-info/fragments/list-async.html");

        assertThat(fragment)
                .contains("th:fragment=\"results\"")
                .contains("id=\"travel-info-results\"")
                .contains("travel-info-grid", "travel-info-card", "travel-info-thumbnail")
                .contains("travel-info-thumbnail-placeholder", "등록된 이미지가 없습니다")
                .contains("travel-info-period", "info.startDate", "info.endDate")
                .contains("travel-info-pagination", "keyword=${keyword}", "page=${pageNumber}")
                .contains("class=\"travel-info-sort\"", "data-travel-info-sort")
                .contains("aria-label=\"여행정보 정렬\"")
                .contains("data-sort-value=\"latest\"", "최신순")
                .contains("data-sort-value=\"views\"", "조회순")
                .contains("th:classappend=\"${sort == 'latest'} ? ' is-active'\"")
                .contains("th:classappend=\"${sort == 'views'} ? ' is-active'\"")
                .contains("aria-current=${sort == 'latest'}")
                .contains("aria-current=${sort == 'views'}")
                .contains("sort=${sort == 'views' ? sort : null}")
                .contains("th:if=\"${keyword != null}\"")
                .contains("th:text=\"|‘${keyword}’|\"")
                .doesNotContain("th:utext=\"${keyword}\"")
                .contains("class=\"travel-info-card-link\"")
                .contains("data-travel-info-bookmark")
                .contains("data-bookmark-url", "info.bookmarked")
                .contains("aria-pressed", "여행정보 저장 취소")
                .contains("travel-info-bookmark-icon")
                .contains("${info.contentType.name() == 'FESTIVAL'}")
                .contains("@{/festivals/{id}(id=${info.id},returnUrl=${listUrl})}")
                .contains("@{/travel-info/{id}(id=${info.id},returnUrl=${listUrl})}")
                .doesNotContain("♡", "♥", "<select", "travel-info-sort-select");
        assertThat(asyncFragment)
                .contains("th:fragment=\"response\"")
                .contains("id=\"travel-info-category-filter-template\"")
                .contains("travel-info/fragments/category-filter :: filter")
                .contains("travel-info/fragments/list-results :: results");
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
                .contains(".travel-info-card-bookmark")
                .contains(".travel-info-sort", ".travel-info-sort-option")
                .contains(".travel-info-sort-option.is-active")
                .contains("url('/uploads/icons/bookmark.png')")
                .contains("url('/uploads/icons/bookmark2.png')")
                .contains("z-index: 2")
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
                .contains("/js/travel-info-bookmark.js")
                .contains("travel-info-detail-bookmark", "travelInfo.bookmarked")
                .contains("저장됨", "저장")
                .doesNotContain("thumbnail", "info_images", "userId", "categoryId", "♡", "♥");
        assertThat(css)
                .contains("width: min(960px, calc(100% - 40px))")
                .contains("overflow-wrap: anywhere")
                .contains(".travel-info-detail-content img")
                .contains("max-width: 100%", "height: auto")
                .contains("url('/uploads/icons/bookmark.png')")
                .contains("url('/uploads/icons/bookmark2.png')")
                .contains("@media (max-width: 720px)")
                .contains("@media (max-width: 430px)");
    }

    @Test
    void travelInfoBookmarkJavascriptUsesDelegationCsrfAndPessimisticStateUpdate()
            throws IOException {
        String javascript = resource("/static/js/travel-info-bookmark.js");

        assertThat(javascript)
                .contains("document.addEventListener('click'")
                .contains("event.target.closest(BOOKMARK_SELECTOR)")
                .contains("event.preventDefault()", "event.stopPropagation()")
                .contains("window.location.assign(LOGIN_URL)")
                .contains("const LOGIN_URL = '/login'")
                .doesNotContain("redirect=")
                .contains("button.disabled = true", "button.disabled = false")
                .contains("bookmarked ? 'DELETE' : 'POST'")
                .contains("meta[name=\"_csrf\"]", "meta[name=\"_csrf_header\"]")
                .contains("[csrfHeader]: csrfToken")
                .contains("if (!response.ok)")
                .contains("updateButton(button, !bookmarked)")
                .contains("aria-pressed", "aria-label")
                .doesNotContain("♡", "♥", "icon.textContent")
                .doesNotContain("AbortController");
    }

    @Test
    void javascriptFetchesFragmentsSynchronizesHistoryCancelsStaleRequestsAndFallsBack()
            throws IOException {
        String javascript = resource("/static/js/travel-info-list.js");

        assertThat(javascript)
                .contains("const PRIMARY_FILTER_NAME = 'primary'")
                .contains("const CATEGORY_FILTER_NAME = 'categoryId'")
                .contains("const CONTENT_TYPE_PARAMETER_NAME = 'contentType'")
                .contains("const KEYWORD_PARAMETER_NAME = 'keyword'")
                .contains("const SORT_PARAMETER_NAME = 'sort'")
                .contains("const SORT_VIEWS = 'views'")
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
                .contains("primaryFilterUrl")
                .contains("syncPrimaryFilterUi")
                .contains("url.searchParams.set(CONTENT_TYPE_PARAMETER_NAME, GENERAL_CONTENT_TYPE)")
                .contains("const sortOption = event.target.closest(SORT_SELECTOR)")
                .contains("loadResults(sortUrl(sortOption), 'push')")
                .contains("control.dataset.sortValue")
                .contains("url.searchParams.delete('page')")
                .contains("url.searchParams.delete(SORT_PARAMETER_NAME)")
                .contains("url.searchParams.set(SORT_PARAMETER_NAME, SORT_VIEWS)")
                .contains("syncSortUi(url)")
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
                .contains("CATEGORY_FILTER_TEMPLATE_SELECTOR")
                .contains("replaceWith(nextCategoryFilter)")
                .contains("window.location.assign(url.href)");
        assertThat(javascript).doesNotContain("document.addEventListener('change'");
    }

    @Test
    void hiddenContentTypeIsPreservedByScopeCategoryAndPaginationLinks() throws IOException {
        String template = resource("/templates/travel-info/list.html");
        String fragment = resource("/templates/travel-info/fragments/list-results.html");

        assertThat(template)
                .contains("sort=${sort == 'views' ? sort : null}")
                .contains("categoryId=${categoryIds}");
        assertThat(fragment)
                .contains("keyword=${keyword},scope=${scope},contentType=${contentType},categoryId=${categoryIds}")
                .contains("sort=${sort == 'views' ? sort : null}")
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
