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
                .contains("/js/admin-kto-tour-autofill.js?v=20260822-3")
                .contains("/js/region-selector.js?v=20260822-3")
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
        assertThat(edit).contains("/js/region-selector.js?v=20260822-3");
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
    void createBindsRestaurantAndCafeAutofillFieldsToTheSharedTypeSection() throws IOException {
        String create = resource("/templates/admin/destinations/create.html");

        assertThat(create)
                .contains("data-kto-tour-field=\"mainMenu\" data-kto-tour-type=\"RESTAURANTS CAFE\"")
                .contains("data-kto-tour-field=\"openingHours\" data-kto-tour-type=\"RESTAURANTS CAFE\"")
                .contains("data-kto-tour-field=\"closedDays\" data-kto-tour-type=\"RESTAURANTS CAFE\"")
                .contains("data-kto-tour-field=\"contactNumber\" data-kto-tour-type=\"RESTAURANTS CAFE\"")
                .contains("data-kto-tour-field=\"homepageUrl\" data-kto-tour-type=\"RESTAURANTS CAFE\"");
        assertThat(create)
                .contains("data-kto-tour-field=\"closedDays\" data-kto-tour-type=\"ATTRACTION\"")
                .contains("data-kto-tour-field=\"openingHours\" data-kto-tour-type=\"ACTIVITY\"")
                .contains("data-kto-tour-field=\"homepageUrl\" data-kto-tour-type=\"SHOP\"");
        assertThat(create).doesNotContain(
                "data-kto-tour-field=\"priceRange\"",
                "data-kto-tour-field=\"breakTime\"",
                "data-kto-tour-field=\"seatCount\"");
    }

    @Test
    void createBindsAccommodationAutofillFieldsToTheAccommodationTypeSection() throws IOException {
        String create = resource("/templates/admin/destinations/create.html");

        assertThat(create)
                .contains("data-kto-tour-field=\"checkinTime\" data-kto-tour-type=\"ACCOMMODATION\"")
                .contains("data-kto-tour-field=\"checkoutTime\" data-kto-tour-type=\"ACCOMMODATION\"")
                .contains("data-kto-tour-field=\"roomCount\" data-kto-tour-type=\"ACCOMMODATION\"")
                .contains("data-kto-tour-field=\"roomType\" data-kto-tour-type=\"ACCOMMODATION\"")
                .contains("data-kto-tour-field=\"contactNumber\" data-kto-tour-type=\"ACCOMMODATION\"")
                .contains("data-kto-tour-field=\"homepageUrl\" data-kto-tour-type=\"ACCOMMODATION\"");
        assertThat(create).doesNotContain(
                "data-kto-tour-field=\"starRating\"",
                "data-kto-tour-field=\"breakfastIncluded\"",
                "data-kto-tour-field=\"petAllowed\"");
    }

    @Test
    void createBindsTheNormalizedBooleanFlagsToTheirTypeCheckboxes() throws IOException {
        String create = resource("/templates/admin/destinations/create.html");

        assertThat(create)
                .contains("data-kto-tour-field=\"parkingAvailable\" data-kto-tour-type=\"RESTAURANTS CAFE\"")
                .contains("data-kto-tour-field=\"takeoutAvailable\" data-kto-tour-type=\"RESTAURANTS CAFE\"")
                .contains("data-kto-tour-field=\"reservation\" data-kto-tour-type=\"RESTAURANTS CAFE\"")
                .contains("data-kto-tour-field=\"parkingAvailable\" data-kto-tour-type=\"ACCOMMODATION\"");
        // 이번 단계에서 연결하지 않는 체크박스/입력칸은 계약을 갖지 않는다
        assertThat(create).doesNotContain(
                "data-kto-tour-field=\"deliveryAvailable\"",
                "data-kto-tour-field=\"petAllowed\"",
                "data-kto-tour-field=\"breakfastIncluded\"");
    }

    @Test
    void scriptAppliesNullableBooleansToCheckboxesWithoutClearingUnknownValues() throws IOException {
        String script = resource("/static/js/admin-kto-tour-autofill.js");

        assertThat(script)
                .contains("fillTypeField(\"parkingAvailable\", currentType, detail.parkingAvailable)")
                .contains("fillTypeField(\"takeoutAvailable\", currentType, detail.takeoutAvailable)")
                .contains("fillTypeField(\"reservation\", currentType, detail.reservation)")
                .contains("function setCheckboxIfKnown")
                // null/undefined 는 기존 체크 상태를 그대로 둔다
                .contains("if (value !== true && value !== false) return")
                .contains("element.checked = value")
                // 체크박스는 value 가 제출값이므로 초기화 시에도 비우지 않는다
                .contains("if (element.type === \"checkbox\") {")
                .contains("element.checked = false")
                // 문자열 재해석 금지 - 판정은 서버가 끝냈다
                .doesNotContain("가능", "불가");
    }

    @Test
    void scriptFillsSharedTypeFieldsFromTheSelectedDestinationType() throws IOException {
        String script = resource("/static/js/admin-kto-tour-autofill.js");

        assertThat(script)
                .contains("fillTypeField(\"checkinTime\", currentType, detail.checkinTime)")
                .contains("fillTypeField(\"checkoutTime\", currentType, detail.checkoutTime)")
                .contains("fillTypeField(\"roomCount\", currentType, detail.roomCount)")
                .contains("fillTypeField(\"roomType\", currentType, detail.roomType)")
                .contains("fillTypeField(\"mainMenu\", currentType, detail.mainMenu)")
                .contains("fillTypeField(\"openingHours\", currentType, detail.openingHours)")
                .contains("fillTypeField(\"closedDays\", currentType, detail.closedDays)")
                .contains("fillTypeField(\"contactNumber\", currentType, detail.contactNumber)")
                .contains("fillTypeField(\"homepageUrl\", currentType, detail.homepageUrl)")
                .contains("(candidate.dataset.ktoTourType || \"\").split(/\\s+/).includes(type)")
                .doesNotContain(
                        "detail.breakTime",
                        "detail.priceRange",
                        "detail.starRating",
                        "parkinglodging",
                        "39",
                        "32");
    }

    @Test
    void createKeepsPhotoSelectionContractIndependent() throws IOException {
        String create = resource("/templates/admin/destinations/create.html");

        assertThat(create)
                .contains("/js/admin-kto-photo-search.js")
                .contains("admin/destinations/fragments/kto-photo-search")
                .contains("/js/admin-kto-tour-autofill.js?v=20260822-3");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
