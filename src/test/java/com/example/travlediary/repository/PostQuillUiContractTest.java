package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PostQuillUiContractTest {

    @Test
    void writeAndEditUseTheSharedQuillEditorWithoutToastUi() throws IOException {
        String write = resource("/templates/post/write.html");
        String edit = resource("/templates/post/edit.html");

        assertPostEditorAssetsAndForm(write);
        assertPostEditorAssetsAndForm(edit);

        assertThat(write)
                .contains("window.initQuillEditor('#editor', 'content-input', 'post-form');");
        assertThat(edit)
                .contains("id=\"initial-content\" hidden th:text=\"${post.content}\"")
                .contains("window.initQuillEditor('#editor', 'content-input', 'post-form', 'initial-content');");
    }

    @Test
    void sharedInitializerKeepsHtmlRoundTripUploadResizeAndFormattingContracts() throws IOException {
        String initializer = resource("/static/js/quill-editor-init.js");

        assertThat(initializer)
                .contains("new Quill(editorElement")
                .contains("theme: 'snow'")
                .contains("quill.clipboard.convert({")
                .contains("quill.setContents(delta, 'silent')")
                .contains("contentInput.value = quill.getSemanticHTML()")
                .contains("const hasImage = Array.from(quill.root.querySelectorAll('img[src]'))")
                .contains("fetch('/api/upload/editor-image'")
                .contains("formData.append('image', image)")
                .contains("insertEmbed(range.index, 'image', data.url, 'user')")
                .contains("const registeredResizeModule = Quill.import('modules/resize')")
                .contains("typeof registeredResizeModule === 'function'")
                .contains("attribute: ['width']")
                .contains("modules: ['Resize']")
                .contains("{list: 'check'}")
                .contains("{indent: '-1'}", "{indent: '+1'}")
                .contains("['undo', 'redo']", "history: true")
                .contains("this.quill.history.undo()", "this.quill.history.redo()")
                .contains("const localhost = url.match(")
                .contains("`http://${url}`", "`https://${url}`")
                .contains("javascript|data|vbscript")
                .doesNotContain(
                        "quill.root.innerHTML",
                        "FileReader",
                        "Quill.register('modules/resize', window.QuillResize"
                );
    }

    @Test
    void detailUsesSharedRichTextStylesAndCourseKeepsToastUi() throws IOException {
        String detail = resource("/templates/post/detail.html");
        String quillCss = resource("/static/css/quill-content.css");
        String courseWrite = resource("/templates/course/write.html");
        String courseEdit = resource("/templates/course/edit.html");
        String courseDetail = resource("/templates/course/detail.html");

        assertThat(detail)
                .contains("class=\"post-content rich-text-content\"")
                .contains("th:utext=\"${post.content}\"")
                .contains("/css/quill-content.css")
                .doesNotContain("toastui-editor-contents", "uicdn.toast.com", "/js/editor-init.js");

        assertThat(quillCss)
                .contains(".rich-text-content .ql-font-pretendard")
                .contains(".rich-text-content .ql-font-noto-sans-kr")
                .contains(".rich-text-content .ql-font-noto-serif-kr")
                .contains(".rich-text-content .ql-font-nanum-human")
                .contains(".rich-text-content .ql-font-school-safe-bareonbatang")
                .contains(".rich-text-content .ql-font-cafe24-dongdong")
                .contains(".rich-text-content .ql-font-gangwon-saeeum")
                .contains("li[data-list=\"checked\"]::before")
                .contains("li[data-list=\"unchecked\"]::before")
                .contains(".rich-text-content .ql-indent-1")
                .contains(".rich-text-content .ql-indent-8")
                .contains(".rich-text-content .ql-align-center")
                .contains(".rich-text-content .ql-size-huge")
                .contains(".rich-text-content img")
                .contains("max-width: 100%")
                .contains("height: auto");

        assertThat(courseWrite).contains("uicdn.toast.com/editor/latest/toastui-editor.min.css");
        assertThat(courseEdit).contains("uicdn.toast.com/editor/latest/toastui-editor.min.css");
        assertThat(courseDetail).contains("toastui-editor-contents");
    }

    private void assertPostEditorAssetsAndForm(String template) {
        assertThat(template)
                .contains("quill@2.0.3/dist/quill.snow.css")
                .contains("quill@2.0.3/dist/quill.js")
                .contains("quill-resize-module@2.1.3/dist/resize.css")
                .contains("quill-resize-module@2.1.3/dist/resize.js")
                .contains("/css/quill-content.css")
                .contains("/js/quill-editor-init.js")
                .contains("class=\"post-editor-shell quill-editor-shell\"")
                .contains("class=\"post-editor quill-editor\"")
                .contains("type=\"hidden\" name=\"content\" id=\"content-input\"")
                .doesNotContain("toastui", "uicdn.toast.com", "/js/editor-init.js", "latest");

        assertThat(template.indexOf("quill@2.0.3/dist/quill.js"))
                .isLessThan(template.indexOf("quill-resize-module@2.1.3/dist/resize.js"));
        assertThat(template.indexOf("quill-resize-module@2.1.3/dist/resize.js"))
                .isLessThan(template.indexOf("/js/quill-editor-init.js"));
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
