package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTravelInfoUiContractTest {

    @Test
    void menuAndDashboardSeparateTravelInfoAndFestivals() throws IOException {
        assertThat(resource("/templates/fragments/admin/sidebar.html"))
                .contains("th:href=\"@{/admin/travel-info}\">여행정보</a>")
                .contains("activeMenu == 'travel-info'")
                .contains("th:href=\"@{/admin/festivals}\">축제·행사</a>")
                .contains("activeMenu == 'festivals'")
                .doesNotContain("여행정보</span>\n        <span class=\"admin-nav-badge\">준비 중");
        assertThat(resource("/templates/admin/index.html"))
                .contains("th:href=\"@{/admin/travel-info}\">여행정보</a>")
                .contains("th:href=\"@{/admin/festivals}\">축제·행사</a>");
    }

    @Test
    void listUsesGetFiltersAndPostDeleteWithoutImageFeatures() throws IOException {
        String list = resource("/templates/admin/travel-info/list.html");

        assertThat(list)
                .contains("th:action=\"@{/admin/travel-info}\" method=\"get\"")
                .contains("name=\"scope\"")
                .contains("name=\"categoryId\"")
                .contains("th:action=\"@{/admin/travel-info/{id}/delete(id=${info.id})}\"")
                .contains("method=\"post\"")
                // 일반 여행정보와 여행가이드만 고른다. 축제·행사는 별도 관리 화면이다.
                .contains("name=\"contentType\"", "value=\"GUIDE\"")
                .doesNotContain("value=\"FESTIVAL\"")
                .doesNotContain("info_images", "image-upload", "대표 이미지");
    }

    @Test
    void formUsesSingleFormQuillEditorAndIndexedPeriods() throws IOException {
        String form = resource("/templates/admin/travel-info/form.html");
        String quillInitializer = resource("/static/js/quill-editor-init.js");
        String quillCss = resource("/static/css/quill-content.css");
        String travelInfoCss = resource("/static/css/admin-travel-info.css");
        String travelInfoJs = resource("/static/js/admin-travel-info-form.js");

        assertThat(form)
                .containsOnlyOnce("<form id=\"travel-info-form\"")
                .contains("enctype=\"multipart/form-data\"")
                .contains("<h2>썸네일</h2>")
                .contains("id=\"travel-info-thumbnail-preview\"")
                .contains("data-current-thumbnail-url=${currentThumbnailUrl}")
                .contains("name=\"thumbnailFile\"")
                .contains("accept=\".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp\"")
                .contains("th:field=\"*{removeThumbnail}\"")
                .contains("class=\"admin-travel-info-editor-shell quill-editor-shell\"")
                .contains("id=\"travel-info-editor\"")
                .contains("class=\"admin-travel-info-editor quill-editor rich-text-image-layout\"")
                .contains("th:field=\"*{content}\"")
                .contains("id=\"travel-info-initial-content\"")
                .contains("hidden th:text=\"*{content}\"")
                .contains("th:field=\"*{periods[__${periodStat.index}__].startDate}\"")
                .contains("th:field=\"*{periods[__${periodStat.index}__].endDate}\"")
                .contains("data-content-type=${category.contentType}")
                .contains("th:hidden=\"${category.contentType != travelInfoForm.contentType}\"")
                .contains("th:disabled=\"${category.contentType != travelInfoForm.contentType}\"")
                .contains("<option value=\"GUIDE\">여행가이드</option>")
                .contains("quill@2.0.3/dist/quill.snow.css")
                .contains("quill@2.0.3/dist/quill.js")
                .contains("quill-resize-module@2.1.3/dist/resize.css")
                .contains("quill-resize-module@2.1.3/dist/resize.js")
                .contains("pretendard@v1.3.9")
                .contains("@fontsource/noto-sans-kr@5.3.0/400.css")
                .contains("@fontsource/noto-serif-kr@5.3.0/400.css")
                .contains("/css/quill-content.css")
                .contains("/js/quill-editor-init.js")
                .contains("/js/admin-travel-info-form.js")
                .doesNotContain(
                        "info_images", "mainIdx", "orderIndex",
                        "toastui", "uicdn.toast.com", "/js/editor-init.js", "latest"
                );
        assertThat(form.indexOf("<h2>썸네일</h2>"))
                .isLessThan(form.indexOf("<h2>본문</h2>"));
        assertThat(form.indexOf("quill@2.0.3/dist/quill.js"))
                .isLessThan(form.indexOf("quill-resize-module@2.1.3/dist/resize.js"));
        assertThat(form.indexOf("quill-resize-module@2.1.3/dist/resize.js"))
                .isLessThan(form.indexOf("/js/quill-editor-init.js"));

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
                .contains("const Font = Quill.import('formats/font')")
                .contains("'pretendard'", "'noto-sans-kr'", "'noto-serif-kr'")
                .contains("'nanum-human'", "'school-safe-bareonbatang'")
                .contains("'cafe24-dongdong'", "'gangwon-saeeum'")
                .contains("Quill.register(Font, true)")
                .contains("const fontFormatsByClass = new Map(")
                .contains("quill.clipboard.addMatcher(Node.ELEMENT_NODE")
                .contains("new Delta().retain(delta.length(), {font: fontFormat})")
                .contains("function groupToolbarFormats(toolbar)")
                .contains("group.querySelector('.ql-blockquote')")
                .contains("quill-toolbar-group-set is-text-format")
                .contains("quill-toolbar-group-set is-block-format")
                .contains("const registeredResizeModule = Quill.import('modules/resize')")
                .contains("typeof registeredResizeModule === 'function'")
                .doesNotContain("Quill.register('modules/resize', window.QuillResize")
                .contains("modules: ['Resize']")
                .contains("embedTags: []")
                .contains("attribute: ['width']")
                .contains("minWidth: 120")
                .contains("maxWidth: 1200")
                .contains("'굵게'", "'기울임'", "'밑줄'", "'취소선'")
                .contains("'글자색'", "'배경색'", "'번호 목록'", "'글머리 목록'")
                .contains("{list: 'check'}", "'체크리스트'")
                .contains("{indent: '-1'}", "{indent: '+1'}")
                .contains("'내어쓰기'", "'들여쓰기'")
                .contains("['undo', 'redo']", "history: true")
                .contains("this.quill.history.undo()", "this.quill.history.redo()")
                .contains("'실행 취소'", "'다시 실행'")
                .contains("'링크'", "'이미지'", "'서식 지우기'")
                .doesNotContain("quill.root.innerHTML", "FileReader");

        assertThat(quillCss)
                .contains(".quill-editor-shell")
                .contains(".quill-toolbar-group-set")
                .contains(".quill-editor.ql-container.ql-snow")
                .contains("display: flex;")
                .contains("flex-wrap: wrap;")
                .contains("display: contents;")
                .contains("flex: 0 0 auto;")
                .contains("flex-wrap: nowrap;")
                .contains("float: none;")
                .contains("padding: 12px 16px;")
                .contains("height: auto;")
                .contains("--quill-editor-min-height, 440px")
                .contains("box-sizing: border-box;")
                .contains("content: \"본문\";")
                .contains("content: \"기본\";")
                .contains("content: \"보통\";")
                .contains("content: \"아주 크게\";")
                .contains(".ql-picker-item[data-value=\"1\"]::before")
                .contains(".ql-picker-item[data-value=\"monospace\"]::before")
                .contains(".ql-picker-item[data-value=\"pretendard\"]::before")
                .contains(".ql-picker-item[data-value=\"noto-sans-kr\"]::before")
                .contains(".ql-picker-item[data-value=\"noto-serif-kr\"]::before")
                .contains(".ql-picker-item[data-value=\"nanum-human\"]::before")
                .contains(".ql-picker-item[data-value=\"school-safe-bareonbatang\"]::before")
                .contains(".ql-picker-item[data-value=\"cafe24-dongdong\"]::before")
                .contains(".ql-picker-item[data-value=\"gangwon-saeeum\"]::before")
                .contains(".ql-picker-item[data-value=\"small\"]::before")
                .contains(".ql-picker-item[data-value=\"large\"]::before")
                .contains(".ql-picker-item[data-value=\"huge\"]::before")
                .contains("projectnoonnu/2501-1@1.1/NanumHumanTTFRegular.woff2")
                .contains("projectnoonnu/noonfonts_2307-2@1.0/HakgyoansimBareonbatangR.woff2")
                .contains("projectnoonnu/noonfonts_twelve@1.1/Cafe24Dongdong.woff")
                .contains("fonts-archive/GangwonEduSaeeum@886cd32897e795db4a8a160867e6478b0b1bc149/")
                .doesNotContain(".ql-formats + .ql-formats::before");

        assertThat(travelInfoCss)
                .contains(".admin-travel-info-form .admin-travel-info-section")
                .contains(".admin-thumbnail-layout")
                .contains(".admin-thumbnail-preview")
                .contains(".admin-thumbnail-remove-control")
                .contains("max-width: 1120px;")
                .doesNotContain("@font-face", ".rich-text-content");

        assertThat(travelInfoJs)
                .contains("travel-info-thumbnail-file")
                .contains("travel-info-remove-thumbnail")
                .contains("URL.createObjectURL(selectedFile)")
                .contains("URL.revokeObjectURL(objectUrl)")
                .contains("removeThumbnail.checked = false")
                .contains("removeThumbnail.disabled = true")
                .contains("travel-info-category")
                .contains("syncCategoryOptions")
                .contains("option.dataset.contentType")
                .contains("window.initQuillEditor(");
    }

    @Test
    void travelInfoImagesUseQuillAlignAndPresetSizeClassesInEditorAndDetail() throws IOException {
        String quillInitializer = resource("/static/js/quill-editor-init.js");
        String quillCss = resource("/static/css/quill-content.css");
        String translationTabs = resource("/templates/fragments/admin/travel-info-translation-tabs.html");
        String festivalDetail = resource("/templates/festivals/detail.html");

        // 크기는 img class, 정렬은 문단의 Quill 기본 align 으로 저장한다. px 드래그와 섞지 않는다.
        assertThat(quillInitializer)
                .contains("class PresetSizedImage extends BaseImage")
                .contains("`ql-image-size-${value}`")
                .contains("editorElement.classList.contains('rich-text-image-layout')")
                .contains("resizeModuleAvailable && !imageLayoutEnabled")
                .contains("quillEditor.formatLine(index, 1, 'align', value || false, 'user')")
                .contains("quillEditor.addContainer('quill-image-layout')");
        assertThat(translationTabs)
                .contains("class=\"admin-travel-info-editor quill-editor rich-text-image-layout\"");
        assertThat(festivalDetail)
                .contains("festival-detail-content rich-text-content rich-text-image-layout");
        // 상세에서도 이미지가 문단 정렬을 따르고, 어떤 크기든 본문 폭을 넘지 않는다.
        assertThat(quillCss)
                .contains(".rich-text-image-layout img {\n    display: inline-block;\n    max-width: 100%;")
                .contains(".rich-text-image-layout img.ql-image-size-25")
                .contains(".rich-text-image-layout img.ql-image-size-100");
    }

    @Test
    void detailRendersSanitizedHtmlWithoutToastUiDependency() throws IOException {
        String detail = resource("/templates/admin/travel-info/detail.html");
        String quillCss = resource("/static/css/quill-content.css");

        assertThat(detail)
                .contains("class=\"admin-travel-info-content rich-text-content rich-text-image-layout\"")
                .contains("th:utext=\"${travelInfo.content}\"")
                .contains("pretendard@v1.3.9")
                .contains("@fontsource/noto-sans-kr@5.3.0/400.css")
                .contains("@fontsource/noto-serif-kr@5.3.0/400.css")
                .contains("/css/quill-content.css")
                .doesNotContain("toastui-editor-contents", "uicdn.toast.com");

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
                .contains(".rich-text-content img")
                .contains("max-width: 100%")
                .contains("height: auto");
    }

    @Test
    void structuredEditorIsWiredNextToTheQuillEditorWithoutReplacingIt() throws IOException {
        String form = resource("/templates/admin/travel-info/form.html");
        String translations = resource("/templates/fragments/admin/travel-info-translation-tabs.html");
        String editor = resource("/static/js/admin-structured-editor.js");
        String formScript = resource("/static/js/admin-travel-info-form.js");
        String quillInitializer = resource("/static/js/quill-editor-init.js");

        // 작성 방식: 등록은 radio, 수정은 hidden 으로 고정. 두 편집기는 함께 있고 한쪽만 보인다.
        assertThat(form)
                .contains("<fieldset id=\"travel-info-content-format\" class=\"admin-content-format\" th:if=\"${!editMode}\">")
                .contains("th:field=\"*{contentFormat}\" value=\"QUILL\"")
                .contains("th:field=\"*{contentFormat}\" value=\"STRUCTURED\"")
                .contains("<input type=\"hidden\" id=\"travel-info-content-format-fixed\" th:field=\"*{contentFormat}\">")
                .contains("id=\"travel-info-quill-body\"")
                // 바꾼 JS 는 버전을 올려 캐시에 남은 예전 판과 섞이지 않게 한다.
                .contains("/js/quill-editor-init.js?v=20261004-format-check")
                .contains("/js/admin-travel-info-form.js?v=20261004-format-check")
                .doesNotContain("data-quill-inactive")
                .contains("id=\"travel-info-editor\"")
                .contains("data-structured-editor")
                .contains("<input type=\"hidden\" id=\"travel-info-structured-content\" th:field=\"*{structuredContent}\">")
                .contains("/css/admin-structured-editor.css")
                .contains("id=\"travel-info-structured-translation-notice\"");
        // 에디터가 먼저 만들어져야 폼 스크립트가 작성 방식 전환에 쓸 수 있다.
        assertThat(form.indexOf("/js/admin-structured-editor.js"))
                .isPositive()
                .isLessThan(form.indexOf("/js/admin-travel-info-form.js"));
        // 구조화 글의 번역 글 JSON 은 화면이 없어도 수정 저장에서 지워지지 않게 그대로 오간다.
        assertThat(translations)
                .contains("th:field=\"*{translations[__${slot.index}__].structuredText}\"")
                .contains("data-translation-body");

        assertThat(editor)
                .contains("const UPLOAD_URL = '/admin/api/travel-info/content-images';")
                .contains("body.append('image', file);")
                .contains("meta[name=\"_csrf\"]")
                .contains("credentials: 'same-origin'")
                .contains("JSON.stringify({version: 1, blocks: state.blocks.map(writeBlock)})")
                .contains("input.value = serialize();")
                .contains("window.confirm(")
                .doesNotContain(".innerHTML", "draggable");
        // Quill 본문 검사는 저장하는 순간의 작성 방식이 QUILL 일 때만 한다. (화면 속성에 기대지 않는다)
        assertThat(formScript)
                .contains("input[type=\"radio\"][name=\"contentFormat\"]")
                .contains("{isActive: () => submittedContentFormat() === 'QUILL'}")
                .contains("input[type=\"radio\"][name=\"contentFormat\"]:checked")
                .contains("input[type=\"hidden\"][name=\"contentFormat\"]")
                .contains("structuredRadio.disabled = festival")
                .contains("syncFormatAvailability(selectedType);")
                .doesNotContain("data-quill-inactive");
        assertThat(quillInitializer)
                .contains("const isActive = typeof options.isActive === 'function' ? options.isActive : () => true;")
                .contains("if (!isActive()) return;")
                .contains("alert('본문을 입력해 주세요.');")
                .doesNotContain("data-quill-inactive");
        // 이미지 설명(alt)은 선택 입력이다. 화면 검사가 alt 를 이유로 저장을 막지 않는다.
        assertThat(editor).doesNotContain("이미지 설명(alt)을 입력해 주세요.");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
