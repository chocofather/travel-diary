package com.example.travlediary.repository;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 신규 등록 폼의 직접 업로드 미리보기 계약.
 * 이미지 관리 화면과 같은 공용 스크립트를 쓰고, 업로드/검증 계약은 건드리지 않는다.
 */
class AdminDestinationCreateImagePreviewUiContractTest {

    @Test
    void createFormShowsSelectedFilesBeforeUploading() throws IOException {
        String create = resource("/templates/admin/destinations/create.html");
        Document page = Jsoup.parse(create);

        // 미리보기 영역 (선택 전에는 숨김)
        assertThat(page.select("[data-destination-upload-preview]")).hasSize(1);
        assertThat(page.select("[data-destination-upload-preview][hidden]")).hasSize(1);
        assertThat(page.select("[data-destination-upload-preview-count]")).hasSize(1);
        assertThat(page.select("[data-destination-upload-preview-grid]")).hasSize(1);

        // 기존 업로드 계약 유지 (name/multiple/enctype/KTO)
        assertThat(page.select("input[type=file][name=images][multiple]")).hasSize(1);
        assertThat(create)
                .contains("enctype=\"multipart/form-data\"")
                // KTO 선택 영역(hidden JSON 포함 프래그먼트)은 그대로 둔다
                .contains("kto-photo-search :: search")
                .contains("admin-destination-image-upload-preview.js");
    }

    @Test
    void bothScreensShareTheSamePreviewScriptAndInputReference() throws IOException {
        String create = resource("/templates/admin/destinations/create.html");
        String management = resource("/templates/admin/destinations/image-upload.html");

        // 미리보기 블록이 자기 파일 input 을 id 로 가리킨다 (화면마다 input 이 다름)
        assertThat(Jsoup.parse(create).select("[data-destination-upload-preview]").attr(
                "data-destination-upload-preview")).isEqualTo("destination-create-image-files");
        assertThat(Jsoup.parse(create).select("input[type=file][id=destination-create-image-files]"))
                .hasSize(1);
        assertThat(Jsoup.parse(management).select("[data-destination-upload-preview]").attr(
                "data-destination-upload-preview")).isEqualTo("destination-image-files");

        // 두 화면이 같은 공용 스크립트를 같은 버전으로 로드한다
        assertThat(create).contains("admin-destination-image-upload-preview.js?v=");
        assertThat(management).contains("admin-destination-image-upload-preview.js?v=");
    }

    @Test
    void previewScriptSupportsEveryPreviewBlockWithoutTouchingSubmit() throws IOException {
        String script = resource("/static/js/admin-destination-image-upload-preview.js");

        assertThat(script)
                // 화면별 블록을 모두 초기화한다
                .contains("querySelectorAll(\"[data-destination-upload-preview]\")")
                .contains("getElementById")
                .contains("addEventListener(\"change\"")
                // 로컬 미리보기 전용
                .contains("URL.createObjectURL")
                .contains("URL.revokeObjectURL")
                .contains("input.files")
                .contains("replaceChildren")
                .contains("선택한 이미지")
                .doesNotContain("fetch(")
                .doesNotContain("XMLHttpRequest")
                .doesNotContain("form.submit");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
