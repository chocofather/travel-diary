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

    @Test
    void openingOnePopoverDoesNotCloseItself() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);

        assertThat(script).contains("if (other.panel !== panel) other.close();");
        assertThat(script).contains("trigger.addEventListener('click', () => toggle(list.hidden));");
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

    @Test
    void savedFontClassSurvivesSanitizeSoReadModeKeepsIt() {
        DiaryContentSanitizer sanitizer = new DiaryContentSanitizer(new PostContentSanitizer());

        String saved = sanitizer.sanitize(
                "<p><span class=\"ql-font-park-dahyun\">읽기 모드에서도 이 글꼴</span></p>");

        assertThat(saved).contains("ql-font-park-dahyun").contains("읽기 모드에서도 이 글꼴");
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
