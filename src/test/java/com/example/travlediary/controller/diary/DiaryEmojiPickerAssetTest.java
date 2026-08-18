package com.example.travlediary.controller.diary;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이모지 picker 는 JS 로 그려지므로, 최소한 자산(데이터/스타일)의 계약만 확인한다.
 */
class DiaryEmojiPickerAssetTest {

    private static final Path EMOJI_DATA =
            Path.of("src/main/resources/static/js/diary-emoji-data.js");
    private static final Path EDITOR_SCRIPT =
            Path.of("src/main/resources/static/js/diary-editor.js");
    private static final Path DIARY_CSS =
            Path.of("src/main/resources/static/css/diary.css");

    @Test
    void pickerIsClosedByDefaultEvenThoughItHasADisplayRule() throws IOException {
        String css = Files.readString(DIARY_CSS);

        // display 를 지정한 뒤에도 hidden 이 이겨야 진입 시 열려 보이지 않는다.
        assertThat(css).contains(".diary-emoji-popover[hidden] {");
        assertThat(css).containsPattern(
                "\\.diary-emoji-popover\\[hidden]\\s*\\{\\s*display:\\s*none;");
        // 종류가 많아 스크롤과 크기 제한이 필요하다
        assertThat(css).contains("max-height: 260px;");
        assertThat(css).contains(".diary-emoji-grid {");
        assertThat(css).contains("overflow-y: auto;");
    }

    @Test
    void toggleKeepsTheAriaExpandedStateInSync() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);

        // 툴바 팝오버(글꼴/이모지)는 공통 처리로 열고 닫는다
        assertThat(script).contains("panel.hidden = !open;");
        assertThat(script).contains("trigger.setAttribute('aria-expanded', open ? 'true' : 'false')");
        // 하나를 열면 자기 자신 말고 나머지가 닫힌다
        assertThat(script).contains("if (other.panel !== panel) other.close();");
        // 버튼 다시 클릭 / 바깥 클릭 / Esc 로 닫힌다
        assertThat(script).contains("button.addEventListener('click', () => togglePopover(popover.hidden))");
        assertThat(script).contains("!panel.contains(event.target)");
        assertThat(script).contains("event.key === 'Escape'");
    }

    @Test
    void emojiDataHasEnoughUniqueEmojisAcrossCategories() throws IOException {
        String data = Files.readString(EMOJI_DATA);

        Set<String> emojis = new LinkedHashSet<>();
        int total = 0;
        Matcher arrays = Pattern.compile("emojis:\\s*\\[(.*?)]", Pattern.DOTALL).matcher(data);
        int categories = 0;
        while (arrays.find()) {
            categories++;
            Matcher items = Pattern.compile("'([^']+)'").matcher(arrays.group(1));
            while (items.find()) {
                total++;
                emojis.add(items.group(1));
            }
        }

        assertThat(categories).isGreaterThanOrEqualTo(8);
        assertThat(emojis).hasSizeGreaterThanOrEqualTo(250);
        // 카테고리 사이에 중복이 없다
        assertThat(emojis).hasSize(total);
    }
}
