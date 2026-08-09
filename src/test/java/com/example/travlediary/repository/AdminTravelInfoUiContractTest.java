package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTravelInfoUiContractTest {

    @Test
    void menuAndDashboardLinkToTravelInfo() throws IOException {
        assertThat(resource("/templates/fragments/admin/sidebar.html"))
                .contains("th:href=\"@{/admin/travel-info}\">여행정보</a>")
                .contains("activeMenu == 'travel-info'")
                .doesNotContain("여행정보</span>\n        <span class=\"admin-nav-badge\">준비 중");
        assertThat(resource("/templates/admin/index.html"))
                .contains("th:href=\"@{/admin/travel-info}\">여행정보</a>");
    }

    @Test
    void listUsesGetFiltersAndPostDeleteWithoutImageFeatures() throws IOException {
        String list = resource("/templates/admin/travel-info/list.html");

        assertThat(list)
                .contains("th:action=\"@{/admin/travel-info}\" method=\"get\"")
                .contains("name=\"scope\"")
                .contains("name=\"contentType\"")
                .contains("name=\"categoryId\"")
                .contains("th:action=\"@{/admin/travel-info/{id}/delete(id=${info.id})}\"")
                .contains("method=\"post\"")
                .doesNotContain("info_images", "image-upload", "대표 이미지");
    }

    @Test
    void formUsesSingleFormQuillEditorAndIndexedPeriods() throws IOException {
        String form = resource("/templates/admin/travel-info/form.html");
        String quillInitializer = resource("/static/js/quill-editor-init.js");
        String travelInfoCss = resource("/static/css/admin-travel-info.css");

        assertThat(form)
                .containsOnlyOnce("<form id=\"travel-info-form\"")
                .contains("class=\"admin-travel-info-editor-shell\"")
                .contains("id=\"travel-info-editor\"")
                .contains("th:field=\"*{content}\"")
                .contains("id=\"travel-info-initial-content\"")
                .contains("th:field=\"*{periods[__${periodStat.index}__].startDate}\"")
                .contains("th:field=\"*{periods[__${periodStat.index}__].endDate}\"")
                .contains("quill@2.0.3/dist/quill.snow.css")
                .contains("quill@2.0.3/dist/quill.js")
                .contains("/js/quill-editor-init.js")
                .contains("/js/admin-travel-info-form.js")
                .doesNotContain(
                        "multipart/form-data", "info_images", "mainIdx", "orderIndex",
                        "toastui", "uicdn.toast.com", "/js/editor-init.js", "latest"
                );

        assertThat(quillInitializer)
                .contains("new Quill(editorElement")
                .contains("theme: 'snow'")
                .contains("quill.getSemanticHTML()")
                .contains("quill.clipboard.convert({")
                .contains("quill.setContents(delta, 'silent')")
                .contains("fetch('/api/upload/editor-image'")
                .contains("formData.append('image', image)")
                .contains("insertEmbed(range.index, 'image', data.url, 'user')")
                .contains("const localhost = url.match(")
                .contains("const ipv4 = url.match(")
                .contains("`http://${url}`")
                .contains("`https://${url}`")
                .contains("/^https?:\\/\\//i")
                .contains("/^mailto:")
                .contains("/^tel:")
                .contains("javascript|data|vbscript")
                .contains("this.quill.formatText(")
                .contains("range.length")
                .contains("this.quill.insertText(range.index, linkText, 'link', normalizedUrl, 'user')")
                .contains("'굵게'", "'기울임'", "'밑줄'", "'취소선'")
                .contains("'글자색'", "'배경색'", "'번호 목록'", "'글머리 목록'")
                .contains("'링크'", "'이미지'", "'서식 지우기'")
                .doesNotContain("quill.root.innerHTML", "FileReader");

        assertThat(travelInfoCss)
                .contains(".admin-travel-info-editor-shell")
                .contains(".admin-travel-info-editor.ql-container.ql-snow")
                .contains("height: auto;")
                .contains("min-height: 350px;")
                .contains("box-sizing: border-box;")
                .contains("content: \"본문\";")
                .contains("content: \"기본\";")
                .contains("content: \"보통\";")
                .contains("content: \"아주 크게\";")
                .contains(".ql-picker-item[data-value=\"1\"]::before")
                .contains(".ql-picker-item[data-value=\"serif\"]::before")
                .contains(".ql-picker-item[data-value=\"monospace\"]::before")
                .contains(".ql-picker-item[data-value=\"small\"]::before")
                .contains(".ql-picker-item[data-value=\"large\"]::before")
                .contains(".ql-picker-item[data-value=\"huge\"]::before");
    }

    @Test
    void detailRendersSanitizedHtmlWithoutToastUiDependency() throws IOException {
        assertThat(resource("/templates/admin/travel-info/detail.html"))
                .contains("class=\"admin-travel-info-content rich-text-content\"")
                .contains("th:utext=\"${travelInfo.content}\"")
                .doesNotContain("toastui-editor-contents", "uicdn.toast.com");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
