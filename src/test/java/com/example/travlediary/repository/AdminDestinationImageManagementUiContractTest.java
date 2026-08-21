package com.example.travlediary.repository;

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
                .contains("img.sourceType == 'KTO_PHOTO_GALLERY'");
    }

    @Test
    void imageManagementPostsSelectedKtoPhotosForTheCurrentDestination() throws IOException {
        String source = resource("/templates/admin/destinations/image-upload.html");
        Document page = Jsoup.parse(source);

        assertThat(source)
                .contains("/images/kto(id=${destinationId})")
                .contains("admin/destinations/fragments/kto-photo-search");
        assertThat(page.select("button[type=submit][data-kto-photo-submit]")).hasSize(1);
    }

    @Test
    void addingImagesUsesOneSectionWithUploadAndKtoSearchSideBySide() throws IOException {
        String source = resource("/templates/admin/destinations/image-upload.html");
        Document page = Jsoup.parse(source);

        // 직접 업로드와 KTO 검색이 하나의 '이미지 추가' 영역 안에 함께 있다
        assertThat(page.select(".admin-image-add-grid")).hasSize(1);
        assertThat(page.select(".admin-image-add-grid form[enctype=multipart/form-data]"
                + " input[type=file][name=files][multiple]")).hasSize(1);
        assertThat(page.select(".admin-image-add-grid .admin-kto-photo-management-form")).hasSize(1);
        // 잘못된 이미지 안내는 직접 업로드 영역 안에서 보여준다
        assertThat(page.select(".admin-image-add-upload .admin-alert")).hasSize(1);
        assertThat(source).contains("${imageError}");
        // 업로드 버튼과 KTO 검색 버튼은 그대로 유지된다
        assertThat(page.select(".admin-image-add-grid button[type=submit]")).isNotEmpty();
    }

    @Test
    void ktoSearchResultsAndRegisteredImagesKeepTheirFullWidthLayout() throws IOException {
        String css = resource("/static/css/admin-destination-images.css");

        assertThat(css)
                // PC 2열 (직접 업로드 35% / KTO 검색 65%)
                .contains(".admin-image-add-grid")
                .contains("35fr 65fr")
                // 검색 결과·선택 목록·추가 버튼은 전체 폭
                .contains("grid-column: 1 / -1")
                .contains(".admin-kto-photo-grid")
                // 결과가 없으면 빈 영역이 자리를 차지하지 않는다
                .contains(".admin-kto-photo-grid:empty")
                // 좁은 화면에서는 1열로 쌓인다
                .contains("@media");
    }

    @Test
    void directUploadShowsSelectedFilesBeforeUploading() throws IOException {
        String source = resource("/templates/admin/destinations/image-upload.html");
        Document page = Jsoup.parse(source);

        // 미리보기는 상단 입력 열 안이 아니라 전체 폭 영역에 있다 (상단 row 높이를 늘리지 않는다)
        assertThat(page.select("[data-destination-upload-preview]")).hasSize(1);
        assertThat(page.select(".admin-image-add-upload [data-destination-upload-preview]")).isEmpty();
        assertThat(page.select(".admin-image-add-grid > [data-destination-upload-preview]")).hasSize(1);
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
    void topInputRowStaysCompactWhilePreviewAndKtoResultsUseTheFullWidth() throws IOException {
        String source = resource("/templates/admin/destinations/image-upload.html");
        Document page = Jsoup.parse(source);

        // 상단 2열에는 입력 컨트롤만 남는다
        assertThat(page.select(".admin-image-add-upload input[type=file]")).hasSize(1);
        assertThat(page.select(".admin-image-add-upload button[type=submit]")).hasSize(1);
        assertThat(page.select(".admin-image-add-upload [data-destination-upload-preview-grid]"))
                .isEmpty();

        // DOM 순서: 상단 입력 → 직접 업로드 미리보기 → KTO 검색 결과(프래그먼트)
        assertThat(source.indexOf("data-destination-upload-preview"))
                .isGreaterThan(source.indexOf("name=\"files\""));
        assertThat(source.indexOf("kto-photo-search :: search"))
                .isGreaterThan(source.indexOf("data-destination-upload-preview"));
        assertThat(Jsoup.parse(resource(
                "/templates/admin/destinations/fragments/kto-photo-search.html"))
                .select("[data-kto-photo-results]")).hasSize(1);

        String css = resource("/static/css/admin-destination-images.css");
        assertThat(css)
                // 미리보기와 KTO 결과 모두 상단 그리드에서 전체 폭을 쓴다
                .contains(".admin-upload-preview")
                .contains("grid-column: 1 / -1")
                // 전체 폭에서는 더 촘촘한 썸네일 그리드를 쓴다
                .contains("repeat(auto-fill, minmax(");
        assertThat(source).contains("admin/destinations/fragments/kto-photo-search");
    }

    @Test
    void uploadPreviewRendersEachFileLocallyAndReleasesItsObjectUrls() throws IOException {
        String script = resource("/static/js/admin-destination-image-upload-preview.js");

        assertThat(script)
                .contains("destination-image-files")
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

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
