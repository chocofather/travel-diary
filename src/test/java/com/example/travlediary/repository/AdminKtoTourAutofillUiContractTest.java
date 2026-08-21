package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdminKtoTourAutofillUiContractTest {

    @Test
    void onlyCreateLoadsTheMinimalTourApiAutofillUi() throws IOException {
        String create = resource("/templates/admin/destinations/create.html");
        String edit = resource("/templates/admin/destinations/edit.html");

        assertThat(create)
                .contains("/js/admin-kto-tour-autofill.js?v=20260821-2")
                .contains("/js/region-selector.js?v=20260821-3")
                .contains("data-kto-tour-search-button")
                .contains("data-kto-tour-results")
                .contains("data-kto-tour-status")
                .contains("data-destination-korean-name")
                .contains("data-kto-tour-overview")
                .contains("data-kto-tour-latitude")
                .contains("data-kto-tour-longitude");
        assertThat(create)
                .contains("data-kto-tour-english-name")
                .contains("data-kto-tour-english-overview")
                .contains("data-kto-tour-english-status")
                .doesNotContain("data-kto-tour-english-results");
        assertThat(edit).doesNotContain(
                "/js/admin-kto-tour-autofill.js",
                "data-kto-tour-search-button",
                "data-kto-tour-results",
                "data-kto-tour-english-name");
        assertThat(edit).contains("/js/region-selector.js?v=20260821-3");
    }

    @Test
    void scriptSearchesSelectsAndOnlyFillsEmptyExistingFields() throws IOException {
        String script = resource("/static/js/admin-kto-tour-autofill.js");

        assertThat(script)
                .contains("/admin/api/kto/tour/search")
                .contains("/admin/api/kto/tour/detail")
                .contains("/admin/api/kto/tour/english-match")
                .contains("/admin/api/kto/tour/english-detail")
                .contains("URLSearchParams")
                .contains("contentId")
                .contains("contentTypeId")
                .contains("function fillIfEmpty")
                .contains("if (!element || element.value.trim()) return")
                .contains("data-destination-korean-name")
                .contains("data-kto-tour-overview")
                .contains("data-kto-tour-latitude")
                .contains("data-kto-tour-longitude")
                .contains("data-kto-tour-english-name")
                .contains("data-kto-tour-english-overview")
                .contains("matchEnglishTour")
                .contains("loadEnglishDetail")
                .contains("title: koreanTitle")
                .contains("await loadEnglishDetail(payload.matched, requestGeneration)")
                .contains("englishRequestGeneration")
                .contains("requestGeneration !== englishRequestGeneration")
                .contains("textContent")
                .contains("response.ok")
                .doesNotContain(
                        "data-kto-tour-english-results",
                        "renderEnglishCandidates",
                        "payload.status === \"CANDIDATES\"",
                        "translations[0]",
                        "translations[1]",
                        "ktoSelectedPhotosJson",
                        "data-kto-selected-photos-json",
                        "innerHTML");
    }

    @Test
    void changingTourContentClearsEveryTourManagedFieldBeforeApplyingTheNewPlace()
            throws IOException {
        String script = resource("/static/js/admin-kto-tour-autofill.js");

        assertThat(script)
                .contains("let lastSelectedContentId = null")
                .contains("contentChanged")
                .contains("lastSelectedContentId !== item.contentId")
                .contains("clearTourApiManagedFields")
                .contains("clearEnglishAutofill")
                .contains("document.querySelectorAll(\"[data-kto-tour-field]\")")
                .contains("element.value = \"\"")
                .contains("lastSelectedContentId = item.contentId")
                .contains("++englishRequestGeneration")
                .doesNotContain("autoFilledValues")
                .doesNotContain("ktoSelectedPhotosJson", "data-kto-selected-photos-json");
    }

    @Test
    void koreanDetailLoadingOwnershipIsIndependentFromEnglishCandidateRequests() throws IOException {
        String script = resource("/static/js/admin-kto-tour-autofill.js");

        assertThat(script)
                .contains("let koreanDetailRequestGeneration = 0")
                .contains("const loadingGeneration = ++koreanDetailRequestGeneration")
                .contains("if (loadingGeneration === koreanDetailRequestGeneration) {")
                .contains("searchButton.disabled = false;");
    }

    @Test
    void createKeepsPhotoSelectionContractIndependent() throws IOException {
        String create = resource("/templates/admin/destinations/create.html");

        assertThat(create)
                .contains("/js/admin-kto-photo-search.js")
                .contains("admin/destinations/fragments/kto-photo-search")
                .contains("/js/admin-kto-tour-autofill.js?v=20260821-2");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
