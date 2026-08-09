package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CourseQuillUiContractTest {

    @Test
    void writeAndEditUseSharedQuillAssetsAndKeepTheHtmlContentField() throws IOException {
        String write = resource("/templates/course/write.html");
        String edit = resource("/templates/course/edit.html");

        assertCourseEditorTemplate(write);
        assertCourseEditorTemplate(edit);

        assertThat(write)
                .doesNotContain("initial-content");
        assertThat(edit)
                .contains("data-initial-content-id=\"initial-content\"")
                .contains("id=\"initial-content\" hidden th:text=\"${course.content}\"");
    }

    @Test
    void courseJavascriptDelegatesHtmlHandlingToQuillAndKeepsDestinationBehavior() throws IOException {
        String courseJavascript = resource("/static/js/course-write.js");
        String quillInitializer = resource("/static/js/quill-editor-init.js");

        assertThat(courseJavascript)
                .contains("window.initQuillEditor(")
                .contains("form.dataset.initialContentId || undefined")
                .contains("async function searchDestinations()")
                .contains("function addDestination(destination)")
                .contains("selectedDestinations.some(")
                .contains("이미 선택한 여행지입니다.")
                .contains("function moveDestination(index, offset)")
                .contains("function removeDestination(index)")
                .contains("hidden.name = 'destinationIds'")
                .contains("if (selectedDestinations.length === 0)")
                .contains("여행지를 한 곳 이상 선택해 주세요.")
                .contains("if (event.defaultPrevented) return")
                .contains("submitButton.disabled = true")
                .doesNotContain(
                        "initToastEditor",
                        "getMarkdown()",
                        "getHTML()",
                        "contentInput.value"
                );

        assertThat(quillInitializer)
                .contains("quill.clipboard.convert({")
                .contains("quill.setContents(delta, 'silent')")
                .contains("const hasImage = Array.from(quill.root.querySelectorAll('img[src]'))")
                .contains("contentInput.value = quill.getSemanticHTML()")
                .contains("fetch('/api/upload/editor-image'")
                .contains("insertEmbed(range.index, 'image', data.url, 'user')")
                .contains("attribute: ['width']")
                .contains("modules: ['Resize']")
                .doesNotContain("Quill.register('modules/resize', window.QuillResize");
    }

    @Test
    void courseEditorUsesSharedResponsiveSizingAndDetailUsesRichTextStyles() throws IOException {
        String css = resource("/static/css/course-write.css");
        String detailCss = resource("/static/css/course-detail.css");
        String detail = resource("/templates/course/detail.html");

        assertThat(css)
                .contains("max-width: 1200px")
                .contains(".course-write-wrapper .write-form")
                .contains("max-width: 1120px")
                .contains(".course-editor-shell")
                .contains("--quill-editor-min-height: 440px")
                .contains("box-sizing: border-box")
                .doesNotContain(".editor-group #editor");

        assertThat(detail)
                .contains("/css/quill-content.css")
                .contains("class=\"course-introduction-content rich-text-content\"")
                .contains("th:utext=\"${course.content}\"")
                .doesNotContain("toastui-editor-contents", "uicdn.toast.com", "/js/editor-init.js");

        assertThat(detailCss)
                .contains(".course-introduction > h2")
                .doesNotContain(".course-introduction h2");
    }

    private void assertCourseEditorTemplate(String template) {
        assertThat(template)
                .contains("quill@2.0.3/dist/quill.snow.css")
                .contains("quill@2.0.3/dist/quill.js")
                .contains("quill-resize-module@2.1.3/dist/resize.css")
                .contains("quill-resize-module@2.1.3/dist/resize.js")
                .contains("/css/quill-content.css")
                .contains("/js/quill-editor-init.js")
                .contains("/js/course-write.js")
                .contains("class=\"course-editor-shell quill-editor-shell\"")
                .contains("class=\"course-editor quill-editor\"")
                .contains("type=\"hidden\" name=\"content\" id=\"content-input\"")
                .doesNotContain("toastui", "uicdn.toast.com", "/js/editor-init.js", "latest");

        assertThat(template.indexOf("quill@2.0.3/dist/quill.js"))
                .isLessThan(template.indexOf("quill-resize-module@2.1.3/dist/resize.js"));
        assertThat(template.indexOf("quill-resize-module@2.1.3/dist/resize.js"))
                .isLessThan(template.indexOf("/js/quill-editor-init.js"));
        assertThat(template.indexOf("/js/quill-editor-init.js"))
                .isLessThan(template.indexOf("/js/course-write.js"));
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
