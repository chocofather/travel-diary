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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 형광펜은 JS(Quill background 서식) / 툴바 마크업 / 서버 Sanitizer 세 곳에서
 * 같은 색 목록을 써야 한다. (허용 색만 남고 그 밖의 배경색은 계속 막힌다)
 */
class DiaryHighlightAssetTest {

    private static final Path EDITOR_SCRIPT =
            Path.of("src/main/resources/static/js/diary-editor.js");
    private static final Path DETAIL_TEMPLATE =
            Path.of("src/main/resources/templates/diary/detail.html");

    /** 파스텔 형광펜 6종 */
    private static final List<String> HIGHLIGHT_NAMES =
            List.of("노랑", "핑크", "민트", "하늘", "연보라", "살구");

    @Test
    void editorScriptOffersTheSixPastelHighlightsAndAnEraser() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);

        HIGHLIGHT_NAMES.forEach(name -> assertThat(script).contains("name: '" + name + "'"));
        assertThat(highlightValues()).hasSize(HIGHLIGHT_NAMES.size());
        // 기존 highlight 를 지우는 항목도 있어야 한다
        assertThat(script).contains("'형광펜 없음'");
        // 형광펜은 Quill 의 background 서식을 쓴다 (새 편집기/라이브러리 없음)
        assertThat(script).contains("'background'");
        assertThat(script).containsPattern(
                "const FORMATS = \\[[^\\]]*'background'[^\\]]*\\];");
    }

    @Test
    void toolbarHasAMarkerControlNextToTheTextColor() throws IOException {
        String template = Files.readString(DETAIL_TEMPLATE);

        assertThat(template).contains("diary-highlight-trigger");
        assertThat(template).contains("diary-highlight-palette");
        assertThat(template).contains("형광펜");
        // 일반 배경색 입력이 아니라 색 팔레트를 여는 버튼이다
        assertThat(template).doesNotContain("data-editor-command=\"background\"");
        // 글자 색 바로 옆에 둔다
        assertThat(template.indexOf("diary-toolbar-color"))
                .isLessThan(template.indexOf("diary-highlight-trigger"));
    }

    @Test
    void choosingAColorPicksUpTheMarkerInsteadOfPaintingImmediately() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);

        // 색을 고르면 형광펜을 든 상태가 되고, 칠하기는 그다음 드래그부터다
        assertThat(script).contains("startHighlighting({value, name: label});");
        assertThat(script).contains("let highlightMode = null;");
        assertThat(script).contains("syncHighlightTrigger(formats);");
    }

    @Test
    void draggingWithTheMarkerPaintsTheReleasedSelectionOnly() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);

        // 드래그를 놓는 순간 그 구간을 칠한다
        assertThat(script).contains("document.addEventListener('mouseup', finishMarking);");

        String paintSelection = script.substring(script.indexOf("function paintSelection(page) {"));
        paintSelection = paintSelection.substring(0, paintSelection.indexOf("\n    }"));
        // 선택 길이가 0이면 아무것도 하지 않는다
        assertThat(paintSelection).contains("range.length === 0");

        String paintRange = script.substring(script.indexOf("function paintRange(page, range) {"));
        paintRange = paintRange.substring(0, paintRange.indexOf("\n    }"));
        // 커서 서식을 건드리지 않도록 그 구간만 바꾸고, 선택/포커스를 그대로 둔다
        assertThat(paintRange).contains("formatText(range.index, range.length,");
        assertThat(paintRange).contains("'background'");
        assertThat(paintRange).contains("setSelection(range.index, range.length, 'silent')");
        // 칠하기도 기존 자동저장 흐름을 그대로 탄다
        assertThat(paintRange).contains("scheduleSave(page);");
    }

    @Test
    void theMarkerIsPutDownByTheButtonEscapeOrAnotherTool() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);

        // 형광펜 버튼을 다시 누르면 종료
        assertThat(script).containsPattern(
                "(?s)if \\(highlightMode\\) \\{\\s*\\n\\s*setHighlightMode\\(null\\);");
        // Esc 로도 종료
        assertThat(script).contains("if (event.key === 'Escape' && highlightMode) setHighlightMode(null);");

        // 다른 편집 도구(글꼴/굵게/정렬/글자색…)를 고르면 함께 종료
        String applyFormat = script.substring(script.indexOf("function applyFormat(name, value) {"));
        assertThat(applyFormat.substring(0, applyFormat.indexOf("\n    }")))
                .contains("setHighlightMode(null);");
        String insertEmoji = script.substring(script.indexOf("function insertEmoji(emoji) {"));
        assertThat(insertEmoji.substring(0, insertEmoji.indexOf("\n    }")))
                .contains("setHighlightMode(null);");
    }

    @Test
    void serverKeepsExactlyTheHighlightColorsTheEditorCanProduce() throws IOException {
        assertThat(DiaryContentSanitizer.DIARY_HIGHLIGHT_COLORS)
                .containsExactlyInAnyOrderElementsOf(highlightValues());
    }

    @Test
    void savedHighlightSurvivesSanitizeSoReadModeKeepsIt() {
        DiaryContentSanitizer sanitizer = new DiaryContentSanitizer(new PostContentSanitizer());

        String saved = sanitizer.sanitize(
                "<p><span style=\"background-color: #fff5a5;\">읽기 모드에서도 형광펜</span></p>");

        assertThat(saved).contains("background-color: #fff5a5").contains("읽기 모드에서도 형광펜");
    }

    /** 브라우저가 rgb() 로 돌려줘도 같은 색으로 알아보고, 저장 값은 한 가지 표기로 맞춘다. */
    @Test
    void highlightWrittenAsRgbIsKeptInTheSameCanonicalForm() {
        DiaryContentSanitizer sanitizer = new DiaryContentSanitizer(new PostContentSanitizer());

        String saved = sanitizer.sanitize(
                "<p><span style=\"background-color: rgb(201, 242, 227);\">민트</span></p>");

        assertThat(saved).contains("background-color: #c9f2e3").doesNotContain("rgb(");
    }

    @Test
    void backgroundColorsOutsideThePaletteAreRemoved() {
        DiaryContentSanitizer sanitizer = new DiaryContentSanitizer(new PostContentSanitizer());

        String saved = sanitizer.sanitize(
                "<p><span style=\"background-color: #ff0000;\">임의 배경</span>"
                        + "<span style=\"background-color: rgb(0, 0, 0);\">검정 배경</span>"
                        + "<span style=\"background-color: url(x);\">이상한 값</span></p>");

        assertThat(saved).doesNotContain("background-color");
        assertThat(saved).contains("임의 배경").contains("검정 배경").contains("이상한 값");
    }

    @Test
    void scriptAndArbitraryStylesAreStillBlockedAlongsideHighlights() {
        DiaryContentSanitizer sanitizer = new DiaryContentSanitizer(new PostContentSanitizer());

        String saved = sanitizer.sanitize(
                "<p><span style=\"background-color: #ffd6e4; position: fixed;\""
                        + " class=\"attacker\" onclick=\"steal()\">핑크</span>"
                        + "<script>alert(1)</script></p>");

        assertThat(saved).contains("background-color: #ffd6e4").contains("핑크");
        assertThat(saved)
                .doesNotContain("position")
                .doesNotContain("attacker")
                .doesNotContain("onclick")
                .doesNotContain("script")
                .doesNotContain("alert(1)");
    }

    /** 여행정보 정책은 그대로 둔다. (다이어리 형광펜을 위해 넓히지 않는다) */
    @Test
    void travelInfoSanitizerPolicyIsUnchanged() {
        PostContentSanitizer travelInfoSanitizer = new PostContentSanitizer();

        String cleaned = travelInfoSanitizer.sanitize(
                "<span style=\"background-color: #ff0000;\">여행정보 배경</span>");

        // 여행정보는 예전처럼 안전한 배경색을 그대로 허용한다
        assertThat(cleaned).contains("background-color: #ff0000");
    }

    private Set<String> highlightValues() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);
        String highlights = script.substring(
                script.indexOf("const HIGHLIGHTS = ["), script.indexOf("];", script.indexOf("const HIGHLIGHTS = [")));
        Matcher matcher = Pattern.compile("\\{value: '(#[0-9a-f]{6})', name:").matcher(highlights);

        Set<String> values = new LinkedHashSet<>();
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }
}
