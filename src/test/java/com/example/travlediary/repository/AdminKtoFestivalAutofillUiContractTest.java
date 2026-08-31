package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdminKtoFestivalAutofillUiContractTest {

    @Test
    void createFormDeclaresTheFestivalOnlySearchAndCandidateUi() throws IOException {
        String form = resource("/templates/admin/travel-info/form.html");

        assertThat(form)
                .contains("th:if=\"${!editMode}\" src=\"/js/admin-travel-info-festival-autofill.js\"")
                .contains("th:if=\"${!editMode}\"")
                .contains("data-kto-festival-autofill")
                .contains("data-kto-festival-search-start-date")
                .contains("data-kto-festival-search-end-date")
                .contains("data-kto-festival-search-button")
                .contains("data-kto-festival-status")
                .contains("data-kto-festival-results")
                .contains("id=\"travel-info-content-type\"")
                .contains("id=\"travel-info-category\"")
                .contains("data-content-type=${category.contentType}");
    }

    @Test
    void dedicatedScriptUsesFestivalEndpointsAndDataBackedCategoryResolution() throws IOException {
        String script = resource("/static/js/admin-travel-info-festival-autofill.js");

        assertThat(script)
                .contains("/admin/api/kto/festivals/search")
                .contains("/admin/api/kto/festivals/detail")
                .contains("new URLSearchParams")
                .contains("eventStartDate")
                .contains("eventEndDate")
                .contains("contentId")
                .contains("option.dataset.contentType === FESTIVAL_TYPE")
                .contains("option.textContent")
                .contains("detail.categoryName")
                .doesNotContain("categoryId: 1", "categoryId: 2", "categoryId: 3");
    }

    @Test
    void scriptPreservesManualValuesAndCanReplaceItsOwnPreviousAutofill() throws IOException {
        String script = resource("/static/js/admin-travel-info-festival-autofill.js");

        assertThat(script)
                .contains("const managedValues = new Map()")
                .contains("managedValues.has(fieldKey)")
                .contains("currentValue === managedValues.get(fieldKey)")
                .contains("let lastSelectedContentId = null")
                .contains("lastSelectedContentId = item.contentId")
                .contains("quill.getSemanticHTML()")
                .contains("quill.clipboard.convert({")
                .contains("quill.setContents(delta, 'silent')")
                .contains("detail.eventStartDate")
                .contains("detail.eventEndDate");
    }

    @Test
    void scriptBuildsNonBlankFestivalHtmlAndDeduplicatesContactNumbers() throws IOException {
        String script = resource("/static/js/admin-travel-info-festival-autofill.js");

        assertThat(script)
                .contains("detail.overview")
                .contains("detail.eventPlace")
                .contains("detail.address")
                .contains("detail.playTime")
                .contains("detail.useTimeFestival")
                .contains("detail.sponsor1")
                .contains("detail.sponsor2")
                .contains("detail.tel")
                .contains("detail.eventHomepage || detail.homepage")
                .contains("const seenContacts = new Set()")
                .contains("if (!hasText(value)) return")
                .contains("document.createElement")
                .contains("textContent")
                .contains("container.innerHTML");
    }

    @Test
    void scriptRendersCandidateImagesAndExplicitAsyncStates() throws IOException {
        String script = resource("/static/js/admin-travel-info-festival-autofill.js");

        assertThat(script)
                .contains("item.firstImage || item.firstImage2")
                .contains("검색하고 있습니다")
                .contains("검색 결과가 없습니다")
                .contains("검색하지 못했습니다")
                .contains("상세 정보를 불러오고 있습니다")
                .contains("상세 정보를 불러오지 못했습니다")
                .contains("panel.hidden = contentType.value !== FESTIVAL_TYPE")
                .doesNotContain("info_images", "detailImage2", "detailInfo2");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
