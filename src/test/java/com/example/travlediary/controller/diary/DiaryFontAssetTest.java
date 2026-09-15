package com.example.travlediary.controller.diary;

import com.example.travlediary.service.diary.DiaryContentSanitizer;
import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 다이어리 글꼴은 JS(Quill whitelist) / CSS / 서버 Sanitizer 세 곳에서 같은 값을 써야 한다.
 */
class DiaryFontAssetTest {

    private static final Path EDITOR_SCRIPT =
            Path.of("src/main/resources/static/js/diary-editor.js");
    private static final Path FONT_CSS =
            Path.of("src/main/resources/static/css/diary-fonts.css");

    /** 툴바에 보여주는 이름 (기본 제외) */
    private static final List<String> FONT_LABELS = List.of(
            "그리운 프롬솔", "나눔스퀘어", "부크크 명조", "하이커체", "카페24 써라운드",
            "이서윤체", "꾸불림체", "그리운 국한박 오춘기 김작가", "조선궁서체",
            "군함이말문트였체", "둥근모꼴+ Fixedsys", "밑미 폰트", "윤초록우산어린이 만세",
            "인천교육자람체", "온글잎 박다현체"
    );
    /** 기본 + 웹폰트 15종 */
    private static final int TOTAL_FONT_COUNT = 16;

    @Test
    void editorScriptListsTheDiaryFontsAndDropsTheOldOnes() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);

        assertThat(script).contains("{value: '', label: '기본'}");
        FONT_LABELS.forEach(label -> assertThat(script).contains("label: '" + label + "'"));
        // 기본 + 15종 = 16종
        assertThat(script.split("label: '", -1).length - 1).isEqualTo(TOTAL_FONT_COUNT);
        // 예전 다이어리 글꼴 목록은 남아 있지 않다
        assertThat(script).doesNotContain("'pretendard'")
                .doesNotContain("'noto-sans-kr'")
                .doesNotContain("'noto-serif-kr'")
                .doesNotContain("'nanum-human'");
    }

    @Test
    void fontTriggerStateIsDeclaredBeforeTheToolbarSyncReadsIt() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);

        // 툴바 동기화가 초기화보다 먼저 fontTrigger 를 읽으므로 선언이 뒤에 오면 안 된다.
        int declaration = script.indexOf("let fontTrigger = null;");
        int firstSetupCall = script.indexOf("    setupToolbar();");
        assertThat(declaration).isPositive();
        assertThat(firstSetupCall).isPositive();
        assertThat(declaration).isLessThan(firstSetupCall);
        // 선언은 한 곳에만 있어야 한다
        assertThat(script.indexOf("let fontTrigger", declaration + 1)).isEqualTo(-1);
    }

    @Test
    void toolbarDisplayFollowsTheRealCursorFormat() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);

        // 표시는 실제 커서 위치의 서식만 읽는다 (기억해 둔 lastRange 로 덮어쓰지 않는다)
        assertThat(script).contains("function selectionFormats()");
        assertThat(script).contains("const formats = selectionFormats();");
        int bodyStart = script.indexOf("function selectionFormats() {");
        assertThat(script.substring(bodyStart, script.indexOf("\n    }", bodyStart)))
                .doesNotContain("lastRange");
        // 서식 적용에는 lastRange 복원을 그대로 쓴다
        assertThat(script).contains("function formatsForEditing()");
        assertThat(script).contains("activePage.quill.getSelection() || activePage.lastRange");
        // 입력(Enter 포함)으로 커서가 옮겨진 경우에도 동기화한다
        assertThat(script).containsPattern(
                "(?s)quill\\.on\\('text-change'.*?syncToolbar\\(\\);");
    }

    @Test
    void choosingAFontReturnsFocusToThePaper() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);

        String applyFormat = script.substring(script.indexOf("function applyFormat(name, value) {"));
        applyFormat = applyFormat.substring(0, applyFormat.indexOf("\n    }"));
        // focus 복귀 → range 복원 → 마지막에 서식 적용 순서여야 커서 서식이 유지된다
        assertThat(applyFormat.indexOf("quill.focus();"))
                .isLessThan(applyFormat.indexOf("quill.setSelection("));
        assertThat(applyFormat.indexOf("quill.setSelection("))
                .isLessThan(applyFormat.indexOf("quill.format(name, value, 'user');"));
        // 글꼴을 고른 뒤 툴바 버튼으로 포커스를 되돌리지 않는다
        String optionClick = script.substring(script.indexOf("applyFormat('font', font.value || false);"));
        assertThat(optionClick.substring(0, optionClick.indexOf("});")))
                .doesNotContain("trigger.focus()");
    }

    /**
     * Enter 직전 활성 inline format은 Quill 기본 줄바꿈이 끝난 뒤의 빈 커서에도 남아야 한다.
     * 기본 글꼴처럼 값이 없던 format은 새로 만들지 않고, 이미 승계된 값도 다시 쓰지 않는다.
     */
    @Test
    void enterKeepsTheActiveCustomFontAndInlineFormatsForTheNextLine() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);
        int preserveStart = script.indexOf("function preserveActiveInlineFormatsOnEnter");
        assertThat(preserveStart).as("Enter inline format 보존 함수").isNotNegative();
        String preserve = script.substring(preserveStart);
        preserve = preserve.substring(0, preserve.indexOf("\n    }"));

        assertThat(script).contains(
                "const INLINE_FORMATS = ['font', 'size', 'bold', 'italic', 'underline', 'color', 'background'];");
        assertThat(preserve)
                // 실제 Enter 직전 collapsed selection의 활성값만 기억한다.
                .contains("const beforeFormats = quill.getFormat(range);")
                .contains("beforeFormats[name] !== undefined")
                .contains("beforeFormats[name] !== false")
                // 기본 Enter를 막지 않고 끝난 직후의 새 커서에서만 복원한다.
                .contains("Promise.resolve().then(() =>")
                .contains("selection.length !== 0")
                .contains("selection.index !== range.index + 1")
                // Quill이 이미 정상 승계한 값은 다시 적용하지 않는다.
                .contains("if (afterFormats[name] === value) return;")
                .contains("quill.format(name, value, 'silent');")
                .contains("return true;")
                // 기본 Enter 구현과 문서 전체를 직접 대체하지 않는다.
                .doesNotContain("insertText", "updateContents", "setContents", "innerHTML");

        String createPage = script.substring(script.indexOf("function createPage(element)"));
        createPage = createPage.substring(0, createPage.indexOf("\n    function setActivePage"));
        assertThat(createPage)
                .contains("keyboard: {bindings: {")
                .contains("handler: preserveActiveInlineFormatsOnEnter");
    }

    /**
     * 기존 문장 중간의 Enter는 커서 서식이 아니라 뒤쪽 기존 텍스트가 원래 가진 run별 서식을 보존한다.
     * 따라서 custom font 조합은 유지되고, 기본 글꼴 run과 문장 끝에는 새 서식을 만들지 않는다.
     */
    @Test
    void midLineEnterPreservesEachTrailingTextsOwnInlineFormats() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);

        assertThat(script)
                // 기본 Enter 전에 현재 줄의 뒤쪽만 캡처한다. 원래 줄바꿈은 제외한다.
                .contains("const trailingInlineRuns = captureTrailingInlineRuns(quill, range);")
                .contains("const [line, offset] = quill.getLine(range.index);")
                .contains("const trailingLength = line.length() - offset - 1;")
                .contains("if (trailingLength <= 0) return [];")
                // text 자체가 아니라 각 Delta run의 길이와 실제 inline attributes만 보존한다.
                .contains("quill.getContents(range.index, trailingLength).ops")
                .contains("length: typeof operation.insert === 'string'")
                .contains("formats: pickExistingInlineFormats(operation.attributes)")
                // 기본 Enter 후 이동한 동일 run에, 실제로 유실된 값만 되돌린다.
                .contains("restoreTrailingInlineRuns(quill, range.index + 1, trailingInlineRuns);")
                .contains("Object.entries(run.formats).forEach(([name, value]) =>")
                .contains("if (currentFormats[name] === value) return;")
                .contains("quill.formatText(index, run.length, name, value, 'user');")
                // font/size/bold/color 등을 caret format 하나로 trailing 전체에 덮어쓰지 않는다.
                .doesNotContain("formatText(range.index + 1, trailingLength");

        assertThat(script).contains(
                "const INLINE_FORMATS = ['font', 'size', 'bold', 'italic', 'underline', 'color', 'background'];");
    }

    @Test
    void openingOnePopoverDoesNotCloseItself() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);

        assertThat(script).contains("if (other.panel !== panel) other.close();");
        assertThat(script).contains("trigger.addEventListener('click', () => toggle(list.hidden));");
    }

    /**
     * .diary-font-{키} 는 diary.css 의 font-family 와 우선순위가 같으므로 뒤에 와야 실제로 적용된다.
     * (순서가 바뀌면 한 줄 메모/글꼴 버튼의 글꼴 미리보기가 조용히 무시된다)
     */
    @Test
    void fontStylesheetIsLoadedAfterTheDiaryStylesheet() throws IOException {
        String template = Files.readString(
                Path.of("src/main/resources/templates/diary/detail.html"));

        assertThat(template.indexOf("/css/diary.css"))
                .isLessThan(template.indexOf("/css/diary-fonts.css"));
    }

    @Test
    void everyFontValueHasAWebfontAndStyleRule() throws IOException {
        String css = Files.readString(FONT_CSS);

        for (String value : fontValues()) {
            assertThat(css)
                    .as("본문 글꼴 규칙: " + value)
                    .contains(".diary-editor .ql-font-" + value + ",");
            assertThat(css)
                    .as("드롭다운 미리보기 규칙: " + value)
                    .contains(".diary-font-" + value + " {");
        }
        // 초기 렌더링을 막지 않도록 모든 웹폰트가 swap 을 쓴다
        int fontFaces = css.split("@font-face", -1).length - 1;
        int swaps = css.split("font-display: swap;", -1).length - 1;
        assertThat(fontFaces).isEqualTo(FONT_LABELS.size());
        assertThat(swaps).isEqualTo(fontFaces);
    }

    @Test
    void serverAllowsExactlyTheFontClassesTheEditorCanProduce() throws IOException {
        Set<String> expected = fontValues().stream()
                .map(value -> "ql-font-" + value)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(DiaryContentSanitizer.DIARY_FONT_CLASSES)
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    /**
     * Quill 2의 기본 clipboard 파서는 ql-font-park-dahyun 같은 하이픈 값을
     * ql-font-park 키로 잘못 나눠 초기 HTML의 font format을 버린다.
     * 초기 HTML을 읽는 생성자에 다이어리 글꼴 matcher가 먼저 전달되어야 한다.
     */
    @Test
    void storedCustomFontClassesAreMatchedWhileQuillParsesInitialHtml() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);

        int whitelist = script.indexOf("Font.whitelist = FONT_VALUES;");
        int registration = script.indexOf("Quill.register(Font, true);");
        int construction = script.indexOf("new Quill(element");
        assertThat(whitelist).isPositive().isLessThan(registration);
        assertThat(registration).isLessThan(construction);

        String createPage = script.substring(script.indexOf("function createPage(element)"));
        createPage = createPage.substring(0, createPage.indexOf("\n    function setActivePage"));
        assertThat(createPage)
                .contains("clipboard: {matchers: [")
                .contains("['span[class*=\"ql-font-\"]', restoreDiaryFontFormat]");

        String matcher = script.substring(script.indexOf("function restoreDiaryFontFormat"));
        matcher = matcher.substring(0, matcher.indexOf("\n    }"));
        // 목록을 따로 복제하지 않고 현재 지원 글꼴 전체에 같은 복원 규칙을 쓴다.
        assertThat(matcher)
                .contains("FONT_VALUES.find")
                .contains("node.classList.contains(`ql-font-${value}`)")
                .contains("new Delta().retain(delta.length(), {font})");
    }

    @Test
    void savedFontClassSurvivesSanitizeSoReadModeKeepsIt() {
        DiaryContentSanitizer sanitizer = new DiaryContentSanitizer(new PostContentSanitizer());

        String saved = sanitizer.sanitize(
                "<p><span class=\"ql-font-park-dahyun\">읽기 모드에서도 이 글꼴</span></p>");

        assertThat(saved).contains("ql-font-park-dahyun").contains("읽기 모드에서도 이 글꼴");
    }

    /**
     * 이전 자동저장이 끝나기 전에 글꼴을 바꾸고 편집을 마쳐도 마지막 HTML까지 저장해야 한다.
     * 진행 중인 요청만 기다리고 화면을 떠나면 새 글꼴 클래스가 DB에 도달하지 못한다.
     */
    @Test
    void aFontChangeDuringAutosaveIsQueuedBeforeLeavingEditMode() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);
        String savePage = script.substring(script.indexOf("function savePage(page)"));
        savePage = savePage.substring(0, savePage.indexOf("\n    /**", 1));

        assertThat(savePage)
                .contains("return page.saving.then(saved =>")
                .contains("return savePage(page);");
    }

    /** 박다현체는 작은 실제 글자 면적만 보정하고 줄 높이는 종이의 공통 리듬을 물려받는다. */
    @Test
    void parkDahyunUsesAnOpticalSizeCorrectionWithoutChangingLineRhythm() throws IOException {
        String css = Files.readString(FONT_CSS);
        String selector = ".diary-editor .ql-font-park-dahyun {";
        int start = css.indexOf(selector);

        assertThat(start).isNotNegative();
        String rule = css.substring(start, css.indexOf('}', start));
        assertThat(rule).contains("font-size: 1.12em;").contains("line-height: inherit;");
    }

    @Test
    void newlyAddedFontClassesSurviveSanitizeButUnknownOnesDoNot() {
        DiaryContentSanitizer sanitizer = new DiaryContentSanitizer(new PostContentSanitizer());

        String saved = sanitizer.sanitize(
                "<p><span class=\"ql-font-fromsol\">프롬솔</span>"
                        + "<span class=\"ql-font-hiker\">하이커</span>"
                        + "<span class=\"ql-font-ohchungi\">오춘기</span>"
                        + "<span class=\"ql-font-gunham\">군함이</span>"
                        + "<span class=\"ql-font-not-registered\">미등록</span></p>");

        assertThat(saved)
                .contains("ql-font-fromsol")
                .contains("ql-font-hiker")
                .contains("ql-font-ohchungi")
                .contains("ql-font-gunham")
                .doesNotContain("ql-font-not-registered");
    }

    @Test
    void travelInfoSanitizerDoesNotAllowTheNewDiaryFonts() {
        PostContentSanitizer travelInfoSanitizer = new PostContentSanitizer();

        String cleaned = travelInfoSanitizer.sanitize(
                "<span class=\"ql-font-fromsol\">프롬솔</span>"
                        + "<span class=\"ql-font-gunham\">군함이</span>");

        assertThat(cleaned).doesNotContain("ql-font-fromsol").doesNotContain("ql-font-gunham");
    }

    private Set<String> fontValues() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);
        Matcher matcher = Pattern.compile("\\{value: '([a-z0-9-]+)', label:").matcher(script);
        Set<String> values = new LinkedHashSet<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        assertThat(values).hasSize(FONT_LABELS.size());
        return values;
    }
}
