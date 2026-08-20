package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdminKtoPhotoSearchUiContractTest {

    @Test
    void createAndEditUseTheSameKtoPhotoSearchUi() throws IOException {
        String create = resource("/templates/admin/destinations/create.html");
        String edit = resource("/templates/admin/destinations/edit.html");
        String fragment = resource("/templates/admin/destinations/fragments/kto-photo-search.html");

        assertThat(create)
                .contains("data-destination-korean-name")
                .contains("admin/destinations/fragments/kto-photo-search")
                .contains("/js/admin-kto-photo-search.js");
        assertThat(edit)
                .contains("data-destination-korean-name")
                .contains("admin/destinations/fragments/kto-photo-search")
                .contains("/js/admin-kto-photo-search.js");
        assertThat(fragment)
                .contains("data-kto-photo-search")
                .contains("data-kto-photo-keyword")
                .contains("data-kto-photo-search-button")
                .contains("data-kto-photo-status")
                .contains("data-kto-photo-results")
                .contains("data-kto-photo-more")
                .contains("type=\"button\"")
                .doesNotContain("type=\"checkbox\"", "name=\"kto");
    }

    @Test
    void sharedScriptSearchesAndPaginatesWithoutJoiningTheDestinationForm() throws IOException {
        String script = resource("/static/js/admin-kto-photo-search.js");
        String css = resource("/static/css/destination-create.css");

        assertThat(script)
                .contains("/admin/api/kto/photos/search")
                .contains("URLSearchParams")
                .contains("pageNo")
                .contains("numOfRows")
                .contains("data-destination-korean-name")
                .contains("fetch(")
                .contains("response.json()")
                .contains("response.ok")
                .contains("totalCount")
                .contains("replaceChildren()")
                .doesNotContain("translations[0]", "FormData", "checkbox");

        assertThat(css)
                .contains(".admin-kto-photo-grid")
                .contains("grid-template-columns: repeat(4, minmax(0, 1fr))")
                .contains("object-fit: cover")
                .contains("grid-template-columns: repeat(2, minmax(0, 1fr))")
                .contains("grid-template-columns: 1fr");
    }

    @Test
    void sharedScriptStablyRanksAllLoadedPhotosByTheCurrentKeyword() throws IOException {
        String script = resource("/static/js/admin-kto-photo-search.js");

        assertThat(script)
                .contains("function ktoPhotoRelevanceRank")
                .contains("title === normalizedKeyword")
                .contains("title.includes(normalizedKeyword)")
                .contains("searchKeyword.includes(normalizedKeyword)")
                .contains("function stablySortKtoPhotos")
                .contains("originalIndex")
                .contains("left.rank - right.rank || left.originalIndex - right.originalIndex")
                .contains("loadedItems.push(...payload.items)")
                .contains("stablySortKtoPhotos(loadedItems, currentKeyword)")
                .doesNotContain("results.append(createCard(item))");
    }

    @Test
    void sharedUiKeepsSelectedPhotosOutsideTheDestinationFormSubmission() throws IOException {
        String fragment = resource("/templates/admin/destinations/fragments/kto-photo-search.html");
        String script = resource("/static/js/admin-kto-photo-search.js");
        String css = resource("/static/css/destination-create.css");

        assertThat(fragment)
                .contains("data-kto-photo-selected-area")
                .contains("data-kto-photo-selected-count")
                .contains("data-kto-photo-main-status")
                .contains("data-kto-photo-selected-list")
                .doesNotContain("type=\"hidden\"", "name=\"selected", "name=\"kto");

        assertThat(script)
                .contains("data-kto-photo-selected-area")
                .contains("data-kto-photo-selected-count")
                .contains("data-kto-photo-main-status")
                .contains("data-kto-photo-selected-list")
                .contains("aria-pressed")
                .doesNotContain("FormData", "localStorage", "sessionStorage");

        assertThat(css)
                .contains(".admin-kto-photo-card.is-selected")
                .contains(".admin-kto-photo-preview")
                .contains(".admin-kto-photo-selected-area")
                .contains(".admin-kto-photo-main-badge");
    }

    @Test
    void entireResultCardIsTheAccessibleSelectionControlWithoutASeparateSelectButton() throws IOException {
        String script = resource("/static/js/admin-kto-photo-search.js");
        String css = resource("/static/css/destination-create.css");

        assertThat(script)
                .contains("card.setAttribute(\"role\", \"button\")")
                .contains("card.tabIndex = 0")
                .contains("card.setAttribute(\"aria-pressed\", String(isSelected))")
                .contains("card.addEventListener(\"click\"")
                .contains("card.addEventListener(\"keydown\"")
                .contains("event.key !== \"Enter\"")
                .contains("event.key !== \" \"")
                .contains("event.preventDefault()")
                .contains("selectionState.toggle(item)")
                .contains("admin-kto-photo-selected-check")
                .doesNotContain(
                        "admin-kto-photo-select-button",
                        "preview.type = \"button\"",
                        "preview.addEventListener(\"click\"",
                        "✓ 선택됨",
                        ">선택<"
                );

        assertThat(css)
                .contains(".admin-kto-photo-card:not([aria-disabled=\"true\"]):hover")
                .contains("cursor: pointer")
                .contains(".admin-kto-photo-selected-check")
                .doesNotContain(".admin-kto-photo-select-button");
    }

    @Test
    void selectionStateDeduplicatesPhotosAndKeepsAtMostOneMainPhoto() throws IOException {
        String script = resource("/static/js/admin-kto-photo-search.js");

        assertThat(script)
                .contains("function ktoPhotoSelectionKey")
                .contains("JSON.stringify([externalContentId, imageUrl])")
                .contains("if (!externalContentId && !imageUrl) return null")
                .contains("function createKtoPhotoSelectionState")
                .contains("const selectedItems = new Map()")
                .contains("selectedItems.has(key)")
                .contains("selectedItems.set(key, item)")
                .contains("selectedItems.delete(key)")
                .contains("mainSelectionKey = null")
                .contains("mainSelectionKey = key")
                .contains("renderLoadedPhotos()")
                .contains("renderSelectedPhotos()")
                .doesNotContain("title + createdTime", "createdTime + title");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
