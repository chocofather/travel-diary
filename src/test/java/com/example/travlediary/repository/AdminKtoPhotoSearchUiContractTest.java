package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdminKtoPhotoSearchUiContractTest {

    /** 선택 계약이 바뀌었으므로 두 화면 모두 새 스크립트를 받아야 한다. */
    private static final String KTO_PHOTO_SCRIPT_VERSION = "20260821-4";

    @Test
    void createAndImageManagementUseTheSameKtoPhotoSearchUiWhileEditStaysInformationOnly() throws IOException {
        String create = resource("/templates/admin/destinations/create.html");
        String edit = resource("/templates/admin/destinations/edit.html");
        String imageManagement = resource("/templates/admin/destinations/image-upload.html");
        String fragment = resource("/templates/admin/destinations/fragments/kto-photo-search.html");

        assertThat(create)
                .contains("data-destination-korean-name")
                .contains("admin/destinations/fragments/kto-photo-search")
                .contains("/js/admin-kto-photo-search.js");
        assertThat(edit)
                .doesNotContain(
                        "admin/destinations/fragments/kto-photo-search",
                        "/js/admin-kto-photo-search.js",
                        "name=\"ktoSelectedPhotosJson\"");
        assertThat(imageManagement)
                .contains("data-destination-korean-name")
                .contains("admin/destinations/fragments/kto-photo-search")
                .contains("/js/admin-kto-photo-search.js")
                .contains("data-kto-photo-submit");
        assertThat(fragment)
                .contains("data-kto-photo-search")
                .contains("data-kto-photo-keyword")
                .contains("data-kto-photo-search-button")
                .contains("data-kto-photo-status")
                .contains("data-kto-photo-results")
                .contains("data-kto-photo-more")
                .contains("type=\"button\"")
                .contains("type=\"hidden\"")
                .contains("name=\"ktoSelectedPhotosJson\"")
                .contains("data-kto-selected-photos-json")
                .doesNotContain("type=\"checkbox\"");
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
    void sharedUiSerializesSelectedPhotosIntoTheDestinationForm() throws IOException {
        String fragment = resource("/templates/admin/destinations/fragments/kto-photo-search.html");
        String script = resource("/static/js/admin-kto-photo-search.js");
        String css = resource("/static/css/destination-create.css");

        assertThat(fragment)
                .contains("data-kto-photo-selected-area")
                .contains("data-kto-photo-selected-count")
                .contains("data-kto-photo-main-status")
                .contains("data-kto-photo-selected-list")
                .contains("type=\"hidden\"")
                .contains("name=\"ktoSelectedPhotosJson\"")
                .contains("value=\"[]\"")
                .doesNotContain("name=\"selected");

        assertThat(script)
                .contains("data-kto-photo-selected-area")
                .contains("data-kto-photo-selected-count")
                .contains("data-kto-photo-main-status")
                .contains("data-kto-photo-selected-list")
                .contains("data-kto-selected-photos-json")
                .contains("function serializeKtoSelectedPhotos")
                .contains("externalContentId:")
                .contains("imageUrl:")
                .contains("title:")
                .contains("photographer:")
                .contains("isMain: Boolean(isMain)")
                .contains("JSON.stringify(serializeKtoSelectedPhotos(selectionState.entries()))")
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
                // 서버 parser 는 두 값을 모두 요구한다 (@NotBlank externalContentId, imageUrl)
                .contains("if (!externalContentId || !imageUrl) return null")
                .doesNotContain("if (!externalContentId && !imageUrl) return null")
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

    @Test
    void selectionStopsAtTheSameThirtyPhotoLimitTheServerParserEnforces() throws IOException {
        String script = resource("/static/js/admin-kto-photo-search.js");

        assertThat(script)
                // 서버 parser 의 MAX_SELECTED_PHOTOS 와 같은 값
                .contains("const MAX_KTO_SELECTED_PHOTOS = 30")
                // 새로 추가할 때만 막고, 이미 선택된 사진 해제는 항상 허용한다
                .contains("if (selectedItems.has(key)) {")
                .contains("if (selectedItems.size >= MAX_KTO_SELECTED_PHOTOS) return \"limit\"")
                .contains("KTO 사진은 최대 30장까지 선택할 수 있습니다.")
                // 제한에 걸리면 선택/렌더 상태를 그대로 둔다
                .contains("if (result === \"limit\")");

        // 서버 계약은 그대로 유지된다
        assertThat(readFile("src/main/java/com/example/travlediary/service/kto/"
                + "KtoSelectedPhotoRequestParser.java"))
                .contains("MAX_SELECTED_PHOTOS = 30");
    }

    @Test
    void bothKtoScreensLoadTheSameUpdatedSelectionScript() throws IOException {
        String create = resource("/templates/admin/destinations/create.html");
        String imageManagement = resource("/templates/admin/destinations/image-upload.html");

        assertThat(create).contains("/js/admin-kto-photo-search.js?v=" + KTO_PHOTO_SCRIPT_VERSION);
        assertThat(imageManagement)
                .contains("/js/admin-kto-photo-search.js?v=" + KTO_PHOTO_SCRIPT_VERSION);
    }

    private String readFile(String path) throws IOException {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path)),
                StandardCharsets.UTF_8);
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
