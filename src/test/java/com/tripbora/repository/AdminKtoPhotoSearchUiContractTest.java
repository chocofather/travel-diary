package com.tripbora.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdminKtoPhotoSearchUiContractTest {

    /** 선택 계약이 바뀌었으므로 두 화면 모두 새 스크립트를 받아야 한다. */
    private static final String KTO_PHOTO_SCRIPT_VERSION = "20261009-3";

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
                .contains("search(true)");
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
                .contains("data-kto-photo-submit")
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
    void sharedScriptSortsEachFetchedPageAndAppendsMorePagesWithoutReordering() throws IOException {
        String script = resource("/static/js/admin-kto-photo-search.js");

        assertThat(script)
                .contains("function ktoPhotoRelevanceRank")
                .contains("title === normalizedKeyword")
                .contains("title.includes(normalizedKeyword)")
                .contains("searchKeyword.includes(normalizedKeyword)")
                .contains("function stablySortKtoPhotos")
                // 1순위 공공누리 유형(제1 → 제3 → 그 밖), 2순위 촬영월 최신순(없으면 유형 뒤),
                // 관련도와 받은 순서는 같은 유형·같은 촬영월일 때만 쓰는 tie-breaker
                .contains("left.licenseRank - right.licenseRank\n"
                        + "            || compareKtoPhotoPhotographyMonth(left.photographyMonth, right.photographyMonth)\n"
                        + "            || left.rank - right.rank\n"
                        + "            || left.originalIndex - right.originalIndex")
                // 유형은 서버가 정한 licenseType 으로만 판별하고 화면 라벨을 파싱하지 않는다
                .contains("function ktoPhotoLicenseRank")
                .contains("String(item?.licenseType ?? \"\").trim()")
                .contains("if (licenseType === \"KOGL_TYPE_1\") return 0")
                .contains("if (licenseType === \"KOGL_TYPE_3\") return 1")
                .doesNotContain("licenseLabel.includes(", "item.licenseLabel ?? \"\").trim() ===")
                .contains("if (left === null) return 1")
                .contains("if (right === null) return -1")
                .contains("return right - left")
                // 촬영월은 관광사진 API 응답 값(YYYYMM)만 쓰고 등록일·수정일로 대신하지 않는다
                .contains("String(item?.photographyMonth ?? \"\").trim()")
                // 중복은 화면·보관 중인 사진 기준으로 새 결과에서만 뺀다
                .contains("function excludeLoadedKtoPhotos")
                .contains("excludeLoadedKtoPhotos(payload.items, knownItems())")
                .doesNotContain(
                        "stablySortKtoPhotos(displayedItems, currentKeyword)",
                        "displayedItems.push(...payload.items)",
                        // 촬영 정보가 없으면 문구를 띄우지 않고 생략한다
                        "촬영 정보 없음");
    }

    /** 제1유형 단계 → 제3유형 단계. 더보기는 항상 맨 뒤에만 붙이고 기존 카드 사이에 끼워 넣지 않는다. */
    @Test
    void morePhotosLoadInTwoStagesAndAlwaysAppendToTheEnd() throws IOException {
        String script = resource("/static/js/admin-kto-photo-search.js");

        assertThat(script)
                // 1단계: 촬영월 있는 제1유형만 바로 보여주고, 나머지는 따로 보관한다
                .contains("return ktoPhotoLicenseRank(item) === 0 && ktoPhotoPhotographyMonthKey(item) !== null")
                .contains("stablySortKtoPhotos(newItems.filter(isImmediateKtoPhoto), currentKeyword)")
                .contains("deferredUndatedPrimaryItems.push(")
                .contains("deferredTrailingItems.push(...newItems.filter(isTrailingLicenseKtoPhoto))")
                // 관광사진 페이지 소진 판단과 2단계 전환
                .contains("galleryExhausted = payload.items.length === 0 || requestPage * pageSize >= totalCount")
                .contains("if (shownItems.length === 0) shownItems = takeDeferredBatch()")
                .contains("if (append && galleryExhausted) {")
                // 2단계 대기열: 촬영월 없는 제1유형 → 제3유형 등, 제1유형이 남아 있으면 제3유형을 섞지 않는다
                .contains("deferredUndatedPrimaryItems.concat(deferredTrailingItems)")
                .contains("const groupEnd = trailingStart > 0 ? trailingStart : deferredQueue.length")
                .contains("moreButton.hidden = galleryExhausted && (deferredQueue?.length ?? 0) === 0")
                // 화면에 그린 순서는 맨 뒤에만 붙이고, 라벨은 방금 붙인 묶음 앞·첫 제3유형 앞에 둔다
                .contains("displayedItems.push(...items)")
                .contains("results.append(fragment)")
                .contains("if (markAsNewBatch) latestBatchStartItem = items[0]")
                .contains("if (item === latestBatchStartItem) fragment.append(createNewBatchMarker())")
                .contains("if (item === firstTrailingItem) fragment.append(createLicenseDivider())")
                .contains("\"공공누리 제3유형 사진\"")
                .doesNotContain(
                        "results.insertBefore(",
                        "data-kto-photo-trailing",
                        "appendLoadedPhotos(newPrimaryItems");

        assertThat(resource("/static/css/destination-create.css"))
                .contains(".admin-kto-photo-license-divider");
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
        assertThat(readFile("src/main/java/com/tripbora/service/kto/"
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

    /** 공공누리 제3유형(변경금지)은 관리자 미리보기와 공개 상세에서 잘라 보이지 않게 원본 비율로 그린다. */
    @Test
    void noDerivativesPhotosAreShownWholeAndTheLicenseIsNeverAssumed() throws IOException {
        String script = resource("/static/js/admin-kto-photo-search.js");
        assertThat(script)
                .contains("\"KOGL_TYPE_3\"")
                .contains("\"is-no-derivatives\"")
                // 서버가 준 라벨이 없을 때 1유형으로 가정하지 않는다
                .doesNotContain("?? \"공공누리 제1유형\"");
        assertThat(resource("/static/css/destination-create.css"))
                .contains(".admin-kto-photo-card.is-no-derivatives .admin-kto-photo-preview img")
                .contains("object-fit: contain");

        assertThat(resource("/templates/destination/detail.html"))
                .contains("img.noDerivatives ? 'is-no-derivatives'");
        assertThat(resource("/static/css/destination-detail-mobile.css"))
                .contains(".carousel .slide.is-no-derivatives img { object-fit: contain; }");
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
