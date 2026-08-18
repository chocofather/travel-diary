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
    void recentTabIsKeptInLocalStorageWithoutDuplicates() throws IOException {
        String script = Files.readString(EDITOR_SCRIPT);

        assertThat(script).contains("const RECENT_EMOJI_KEY = 'travelDiaryRecentEmojis';");
        assertThat(script).contains("const RECENT_EMOJI_LIMIT = 30;");
        assertThat(script).contains("name: '최근', icon: '🕘'");
        // 고른 이모지를 맨 앞으로 올리고 같은 값은 한 번만 남긴다
        assertThat(script).contains(
                "[emoji, ...readRecentEmojis().filter(item => item !== emoji)]");
        assertThat(script).contains(".slice(0, RECENT_EMOJI_LIMIT)");
        // 최근 탭은 맨 앞이고, 처음 보여주는 카테고리는 기존 그대로다
        assertThat(script).contains("[RECENT_EMOJI_CATEGORY, ...EMOJI_CATEGORIES]");
        assertThat(script).contains("let shownCategory = EMOJI_CATEGORIES[0];");
        // 빈 상태 문구
        assertThat(script).contains("아직 사용한 이모지가 없어요.");
        // localStorage 가 막혀도 이모지 기능은 그대로 동작한다
        assertThat(script).containsPattern("(?s)function readRecentEmojis\\(\\).*?catch \\(error\\)");
        assertThat(script).containsPattern("(?s)function rememberRecentEmoji\\(emoji\\).*?catch \\(error\\)");
    }

    @Test
    void emojiButtonsAreBigEnoughToTap() throws IOException {
        String css = Files.readString(DIARY_CSS);

        String item = css.substring(css.indexOf(".diary-emoji-item {"));
        item = item.substring(0, item.indexOf('}'));
        assertThat(item).contains("width: 36px;").contains("height: 36px;").contains("font-size: 21px;");
        // 크기를 키워도 picker 는 스크롤 구조를 유지한다
        assertThat(css).contains("max-height: 280px;");
        assertThat(css).contains(".diary-emoji-empty {");
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
