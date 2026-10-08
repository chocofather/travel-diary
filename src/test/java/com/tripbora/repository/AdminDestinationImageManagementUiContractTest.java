package com.tripbora.repository;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdminDestinationImageManagementUiContractTest {

    @Test
    void editFormContainsOnlyAnImageManagementLinkAndNoImageSubmitControls() throws IOException {
        String source = resource("/templates/admin/destinations/edit.html");
        Document edit = Jsoup.parse(source);

        assertThat(edit.select("input[type=file][name=images]")).isEmpty();
        assertThat(edit.select("input[name=main], input[name=slide], input[name=ktoSelectedPhotosJson]")).isEmpty();
        assertThat(edit.select("[th\\:replace*=kto-photo-search]")).isEmpty();
        assertThat(source)
                .contains("@{/admin/destinations/{id}/images")
                .contains("이미지 관리로 이동");
    }

    @Test
    void createFormKeepsDirectUploadAndKtoSelection() throws IOException {
        String source = resource("/templates/admin/destinations/create.html");
        Document create = Jsoup.parse(source);

        assertThat(create.select("input[type=file][name=images][multiple]")).hasSize(1);
        assertThat(source)
                .contains("th:field=\"*{main}\"")
                .contains("th:field=\"*{slide}\"")
                .contains("admin/destinations/fragments/kto-photo-search");
    }

    @Test
    void imageManagementUsesUploadAndImageActionCardsWithoutNumericIndexes() throws IOException {
        String source = resource("/templates/admin/destinations/image-upload.html");
        Document page = Jsoup.parse(source);

        assertThat(page.select("input[type=file][name=files][multiple]")).hasSize(1);
        assertThat(page.select("input[name=mainIdx], input[name=slideIdx]")).isEmpty();
        assertThat(page.select(".admin-destination-image-grid .admin-destination-image-card")).hasSize(1);
        assertThat(source)
                .contains("/main(imageId=${img.id})")
                .contains("/slide(imageId=${img.id})")
                .contains("/delete(id=${img.id})")
                .contains("/metadata(imageId=${img.id})")
                .contains("img.sourceType == 'KTO_PHOTO_GALLERY'");
        assertThat(page.select("input[name=sourceName], input[name=photographer], "
                + "select[name=licenseType], input[name=licenseDetail], input[name=sourceUrl]")).hasSize(5);
        assertThat(source)
                .contains("data-image-license-detail")
                .contains("data-image-license-unknown-warning");
        assertThat(resource("/static/js/admin-destination-image-upload-preview.js"))
                .contains("CREATIVE_COMMONS", "PERMISSION", "OTHER");
    }

    /**
     * 등록된 사진 카드는 출처 입력을 접어 두고(details), 대표·슬라이드·삭제와 공통 출처 대상 선택은
     * 접힌 상태에서도 바로 쓸 수 있게 바깥에 둔다. 상태·요약·순서는 카드에서 늘 보인다.
     */
    @Test
    void registeredImageCardsFoldTheSourceFormButKeepActionsOutside() throws IOException {
        Document page = Jsoup.parse(resource("/templates/admin/destinations/image-upload.html"));
        var card = page.selectFirst(".admin-destination-image-grid .admin-destination-image-card");

        assertThat(card.select("details[data-image-source-details] form.admin-destination-image-metadata-form"))
                .hasSize(1);
        assertThat(card.select("details[data-image-source-details] button[type=submit]").text())
                .isEqualTo("출처 정보 저장");
        assertThat(card.select("details [action*=/main], details [action*=/slide], details [action*=/delete], "
                + "details [data-image-bulk-select]")).isEmpty();
        assertThat(card.select(".admin-destination-image-actions form")).hasSize(3);
        assertThat(card.select("[data-image-bulk-select]")).hasSize(1);
        assertThat(card.select(".admin-image-source-status, .admin-image-source-summary, .admin-image-order-badge"))
                .hasSize(3);
        assertThat(card.select("details summary").text()).contains("출처 정보 접기");
        assertThat(resource("/static/js/admin-destination-image-cards.js"))
                .contains("document.addEventListener(\"invalid\"", "details.open = true");
    }

    /**
     * 순서 편집 모드는 사진과 번호, 위치 이동 조작만 둔다(출처 입력 없음).
     * 번호를 사진마다 직접 입력하지 않고 목록에서 고르거나 한 칸씩 옮기며, 저장은 순서 API 로 한 번에 보낸다.
     */
    @Test
    void imageOrderEditorShowsOnlyPhotosNumbersAndMoveControls() throws IOException {
        Document page = Jsoup.parse(resource("/templates/admin/destinations/image-upload.html"));
        var editor = page.selectFirst("[data-image-order-editor][hidden]");

        // '순서 편집'은 사진 격자 바로 위 머리말(등록된 이미지 · 총 N장)에 있다.
        // 위의 공통 출처 일괄 수정 영역 아래, 편집 영역·격자보다 앞이다.
        String source = resource("/templates/admin/destinations/image-upload.html");
        assertThat(page.select("[data-image-order-bar] [data-image-order-open]")).hasSize(1);
        assertThat(page.select("[data-image-order-bar] h2").text()).contains("등록된 이미지");
        assertThat(source.indexOf("data-image-bulk=\"existing\""))
                .isLessThan(source.indexOf("data-image-order-bar"));
        assertThat(source.indexOf("data-image-order-bar"))
                .isLessThan(source.indexOf("data-image-order-editor"))
                .isLessThan(source.indexOf("class=\"admin-destination-image-grid\""));
        assertThat(editor.attr("th:attr")).contains("/images/order");
        var item = editor.selectFirst("[data-image-order-list] > li[th:each]");
        assertThat(item.select("img, [data-image-order-number], [data-image-order-handle], select[data-image-order-move], "
                + "[data-image-order-step=-1], [data-image-order-step=1]")).hasSize(6);
        assertThat(editor.select("input, textarea, form")).isEmpty();
        assertThat(editor.select("[data-image-order-save][disabled], [data-image-order-cancel]")).hasSize(2);
        assertThat(resource("/static/js/admin-destination-image-order.js"))
                .contains("JSON.stringify({imageIds: ids})", "if (saving ||");
    }

    @Test
    void imageManagementPostsSelectedKtoPhotosForTheCurrentDestination() throws IOException {
        String source = resource("/templates/admin/destinations/image-upload.html");
        Document page = Jsoup.parse(source);
        Document search = Jsoup.parse(resource("/templates/admin/destinations/fragments/kto-photo-search.html"));

        assertThat(source)
                .contains("/images/kto(id=${destinationId})")
                .contains("admin/destinations/fragments/kto-photo-search :: search(true)");
        assertThat(page.select(".admin-image-add-kto button[type=submit][data-kto-photo-submit]")).isEmpty();
        assertThat(search.select(".admin-kto-photo-results-area button[type=submit][data-kto-photo-submit][disabled]"))
                .hasSize(1);
        assertThat(resource("/static/js/admin-kto-photo-search.js"))
                .contains("submitButton.disabled = submitting || selections.length === 0")
                // 빠른 두 번 클릭으로 같은 사진이 두 번 저장되지 않게 첫 제출 즉시 잠근다
                .contains("if (submitting || selectionState.count() === 0)")
                .contains("submitting = true;");
    }

    /** 카드의 체크박스 하나를 선택 삭제와 출처 일괄 적용이 같이 쓴다. 관리는 '등록된 이미지' 머리말에서 한다. */
    @Test
    void registeredImagesShareOneSelectionForBulkDeleteAndBulkSource() throws IOException {
        Document page = Jsoup.parse(resource("/templates/admin/destinations/image-upload.html"));

        assertThat(page.select("form#image-bulk-delete-form[data-image-bulk-delete][method=post]"))
                .singleElement()
                .satisfies(form -> assertThat(form.attr("th:action")).contains("/images/delete/bulk"));
        var card = page.selectFirst(".admin-destination-image-grid .admin-destination-image-card");
        assertThat(card.select("input[type=checkbox]")).singleElement()
                .satisfies(choice -> assertThat(choice.hasAttr("data-image-delete-select")).isTrue())
                .satisfies(choice -> assertThat(choice.hasAttr("data-image-bulk-select")).isTrue())
                .satisfies(choice -> assertThat(choice.attr("name")).isEqualTo("imageIds"))
                .satisfies(choice -> assertThat(choice.attr("form")).isEqualTo("image-bulk-delete-form"))
                .satisfies(choice -> assertThat(choice.attr("th:attr")).contains("data-image-commons=${img.commonsImage}"));
        assertThat(card.select(".admin-image-card-select").text()).isEqualTo("선택");
        assertThat(card.text()).doesNotContain("삭제 선택", "일괄 적용");
        // 단건 삭제는 그대로 둔다
        assertThat(card.select(".admin-destination-image-actions form[th:action*=/delete] button").text())
                .isEqualTo("삭제");

        var bar = page.selectFirst("[data-image-order-bar] form[data-image-bulk-delete]");
        assertThat(bar.select("[data-image-bulk-delete-all], [data-image-bulk-delete-clear], "
                + "[data-image-selection-count], [data-image-bulk-source-open], [data-image-bulk-delete-submit]"))
                .hasSize(5);
        assertThat(bar.selectFirst("[data-image-bulk-source-open]").text()).isEqualTo("출처 일괄 적용");
        // 등록된 사진 출처 영역은 머리말의 공통 선택을 쓰므로 자체 전체 선택·해제를 두지 않는다
        assertThat(resource("/templates/admin/destinations/image-upload.html"))
                .contains("image-bulk-source :: fields(false)", "image-bulk-source :: fields(true)");
        assertThat(resource("/templates/admin/destinations/fragments/image-bulk-source.html"))
                .contains("th:fragment=\"fields(selectionControls)\"", "th:if=\"${selectionControls}\"");

        assertThat(resource("/static/js/admin-destination-image-bulk-delete.js"))
                .contains("선택한 이미지 ${targets.length}장을 삭제하시겠습니까?")
                .contains("if (submitting || targets.length === 0")
                .contains("countLabel.textContent = `${count}장 선택`")
                .contains("choice.dataset.imageCommons !== \"true\"")
                .contains("choice.dispatchEvent(new Event(\"change\", {bubbles: true}))")
                .contains("classList.toggle(\"is-selected\", choice.checked)")
                // 사진 영역만 눌러도 토글하고, 체크박스·버튼·링크를 누른 경우는 건드리지 않는다
                .contains("closest?.(\".admin-destination-image-grid .admin-destination-image-preview\")")
                .contains("event.target.closest(\"label, a, button, input, select\")")
                .contains("choice.checked = !choice.checked");
        // 대표·슬라이드·삭제·출처 영역은 사진 영역 밖에 있어 사진 클릭 토글과 겹치지 않는다
        assertThat(card.select(".admin-destination-image-preview form, .admin-destination-image-preview details, "
                + ".admin-destination-image-preview button")).isEmpty();
        assertThat(resource("/static/js/admin-destination-image-bulk-source.js"))
                .contains("choice(card)?.dataset.imageCommons === \"true\"")
                .contains("panel.querySelector(\"[data-bulk-select-all]\")?.addEventListener");
    }

    @Test
    void addingImagesSeparatesUploadAndKtoActions() throws IOException {
        String source = resource("/templates/admin/destinations/image-upload.html");
        Document page = Jsoup.parse(source);

        assertThat(page.select(".admin-image-add-stack")).hasSize(1);
        assertThat(page.select(".admin-image-add-upload input[type=file][name=files][multiple]"))
                .hasSize(1);
        assertThat(page.select(".admin-image-add-upload [data-destination-upload-preview]"))
                .hasSize(1);
        assertThat(page.select(".admin-image-add-upload [data-image-bulk=upload]"))
                .hasSize(1);
        assertThat(page.select(".admin-image-add-upload button[type=submit]"))
                .hasSize(1);
        assertThat(page.select(".admin-image-add-kto .admin-kto-photo-management-form"))
                .hasSize(1);
        assertThat(page.select(".admin-image-add-upload .admin-alert")).hasSize(1);
        assertThat(source).contains("${imageError}");
    }

    @Test
    void imageAddChannelsKeepTheirOwnSpacing() throws IOException {
        String css = resource("/static/css/admin-destination-images.css");

        assertThat(css)
                .contains(".admin-image-add-stack")
                .contains(".admin-image-add-upload")
                .contains(".admin-image-add-kto")
                .contains(".admin-kto-photo-results-area")
                .contains("@media");
    }

    @Test
    void directUploadShowsSelectedFilesBeforeUploading() throws IOException {
        String source = resource("/templates/admin/destinations/image-upload.html");
        Document page = Jsoup.parse(source);

        assertThat(page.select("[data-destination-upload-preview]")).hasSize(1);
        assertThat(page.select(".admin-image-add-upload [data-destination-upload-preview]")).hasSize(1);
        assertThat(page.select("[data-destination-upload-preview-count]")).hasSize(1);
        assertThat(page.select("[data-destination-upload-preview-grid]")).hasSize(1);
        // 선택 전에는 숨긴 상태
        assertThat(page.select("[data-destination-upload-preview][hidden]")).hasSize(1);
        // 기존 업로드 계약 유지
        assertThat(page.select("input[type=file][name=files][multiple][id=destination-image-files]"))
                .hasSize(1);
        assertThat(source)
                .contains("/images(id=${destinationId})")
                .contains("enctype=\"multipart/form-data\"")
                .contains("admin-destination-image-upload-preview.js");
    }

    @Test
    void uploadPreviewAndBulkFieldsBelongToTheUploadForm() throws IOException {
        String source = resource("/templates/admin/destinations/image-upload.html");
        Document page = Jsoup.parse(source);

        assertThat(page.select(".admin-image-add-upload input[type=file]")).hasSize(1);
        assertThat(page.select(".admin-image-add-upload button[type=submit]")).hasSize(1);
        assertThat(page.select(".admin-image-add-upload [data-destination-upload-preview-grid]"))
                .hasSize(1);

        // DOM 순서: 파일 선택 → 사진별 입력·공통 출처 → 해당 폼의 업로드 버튼
        assertThat(source.indexOf("data-destination-upload-preview"))
                .isGreaterThan(source.indexOf("name=\"files\""));
        assertThat(source.indexOf("data-image-bulk=\"upload\""))
                .isGreaterThan(source.indexOf("data-destination-upload-preview"));
        assertThat(source.indexOf("이미지 업로드</button>"))
                .isGreaterThan(source.indexOf("data-image-bulk=\"upload\""));
        assertThat(Jsoup.parse(resource(
                "/templates/admin/destinations/fragments/kto-photo-search.html"))
                .select("[data-kto-photo-results]")).hasSize(1);

        // 썸네일 그리드 모양은 등록 폼과 공용 CSS 에 있다
        assertThat(resource("/static/css/destination-create.css"))
                .contains(".admin-upload-preview-grid")
                .contains("repeat(auto-fill, minmax(");
        assertThat(source).contains("admin/destinations/fragments/kto-photo-search");
    }

    @Test
    void uploadPreviewRendersEachFileLocallyAndReleasesItsObjectUrls() throws IOException {
        String script = resource("/static/js/admin-destination-image-upload-preview.js");

        // 파일 input 은 화면 템플릿이 id 로 지정하고, 스크립트는 공용이다
        assertThat(resource("/templates/admin/destinations/image-upload.html"))
                .contains("data-destination-upload-preview=\"destination-image-files\"");
        assertThat(script)
                .contains("addEventListener(\"change\"")
                // 로컬 미리보기만 사용하고 서버에 올리지 않는다
                .contains("URL.createObjectURL")
                .contains("URL.revokeObjectURL")
                .doesNotContain("fetch(")
                .doesNotContain("XMLHttpRequest")
                .doesNotContain("form.submit")
                // 여러 장을 순서대로 렌더링하고, 재선택 시 이전 미리보기를 지운다
                .contains("input.files")
                .contains("forEach")
                .contains("replaceChildren")
                // 0장이면 숨기고, 1장 이상이면 개수를 보여준다
                .contains("hidden = true")
                .contains("선택한 이미지")
                // 개별 파일 실패는 해당 카드만 fallback
                .contains("미리보기를 불러올 수 없습니다.");
    }

    @Test
    void bulkSourceControlsKeepCommonAndWorkPagesSeparateOnBothScreens() throws IOException {
        String create = resource("/templates/admin/destinations/create.html");
        String management = resource("/templates/admin/destinations/image-upload.html");
        String metadata = resource("/templates/admin/destinations/fragments/image-metadata.html");
        String bulk = resource("/templates/admin/destinations/fragments/image-bulk-source.html");

        assertThat(create).contains("data-image-bulk=\"upload\"");
        assertThat(management).contains("data-image-bulk=\"upload\"", "data-image-bulk=\"existing\"")
                .contains("images/sources/bulk")
                .contains("data-image-bulk-select")
                .contains("name=\"workPageUrl\"")
                .contains("name=\"commonSourceUrl\"");
        assertThat(metadata).contains("data-image-metadata-field=\"commonSourceUrl\"")
                .contains("data-image-metadata-field=\"workPageUrl\"");
        assertThat(bulk).contains("data-bulk-preview-button", "data-bulk-license-confirm")
                .doesNotContain("data-bulk-field=\"workPageUrl\"")
                .doesNotContain("data-bulk-field=\"sourceUrl\"");
        String script = resource("/static/js/admin-destination-image-bulk-source.js");
        assertThat(script).contains(".admin-upload-preview-card, .admin-destination-image-card")
                .contains("document.addEventListener(\"input\", invalidateForCardEdit)")
                .contains("result.hidden = true");
    }

    @Test
    void commonAndWorkPageUrlsShareNormalizationWithoutTouchingLegacyUrls() throws IOException {
        String create = resource("/templates/admin/destinations/create.html");
        String management = resource("/templates/admin/destinations/image-upload.html");
        Document metadata = Jsoup.parse(resource(
                "/templates/admin/destinations/fragments/image-metadata.html"));
        Document bulk = Jsoup.parse(resource(
                "/templates/admin/destinations/fragments/image-bulk-source.html"));
        Document page = Jsoup.parse(management);

        assertThat(create).contains("admin-destination-image-bulk-source.js?v=20261009-1");
        assertThat(management).contains("admin-destination-image-bulk-source.js?v=20261009-1");
        assertThat(metadata.select("[data-image-metadata-field=commonSourceUrl][data-image-source-url=common], "
                + "[data-image-metadata-field=workPageUrl][data-image-source-url=work]")).hasSize(2);
        assertThat(bulk.select("[data-bulk-field=commonSourceUrl][data-image-source-url=common]"))
                .hasSize(1);
        assertThat(page.select("input[name=commonSourceUrl][data-image-source-url=common], "
                + "input[name=workPageUrl][data-image-source-url=work]")).hasSize(2);
        assertThat(metadata.select("[data-image-metadata-field=sourceUrl][data-image-source-url]"))
                .isEmpty();
        assertThat(page.select("input[name=sourceUrl][data-image-source-url]")).isEmpty();
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
