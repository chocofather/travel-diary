package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryLabelFont;
import com.example.travlediary.service.diary.DiaryContentSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 라벨기 글꼴 목록은 resources/json/diary_label_fonts.json 한 곳이 기준이다.
 * 여기에 없는 값은 저장할 수 없다. (스티커·라벨/메모지 목록과 같은 방식)
 */
class DiaryLabelFontCatalogTest {

    private static final Path DIARY_FONTS_CSS =
            Path.of("src/main/resources/static/css/diary-fonts.css");

    private DiaryLabelFontCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new DiaryLabelFontCatalog();
        catalog.load();
    }

    /**
     * 라벨기에서 고를 수 있는 글꼴은 일기 본문에서 고를 수 있는 글꼴 전부와 같다.
     * 본문 쪽 허용 목록(DiaryContentSanitizer.DIARY_FONT_KEYS)이 서버의 단일 기준이고,
     * 이 검사가 두 목록이 어긋나는 것을 막는다. (본문에 글꼴을 더하면 여기도 함께 늘어난다)
     */
    @Test
    void theListMatchesEveryFontTheDiaryBodyAllows() {
        assertThat(catalog.getFonts())
                .extracting(DiaryLabelFont::code)
                .containsExactlyInAnyOrderElementsOf(DiaryContentSanitizer.DIARY_FONT_KEYS);
    }

    @Test
    void everyFontHasANameToShowAndTheListFollowsTheManifestOrder() {
        // 맨 앞 글꼴이 고르는 자리의 처음 값이 된다
        assertThat(catalog.getFonts().get(0).code()).isEqualTo("nanum-square");
        assertThat(catalog.getFonts().get(0).label()).isEqualTo("나눔스퀘어");
        // 이름 없는 글꼴은 목록에 남지 못한다 (읽는 자리에서 끊는다)
        assertThat(catalog.getFonts()).allSatisfy(font ->
                assertThat(font.label()).isNotBlank());
    }

    @Test
    void onlyAKnownFontIsFound() {
        assertThat(catalog.find("nanum-square")).isPresent();
        // 목록 밖의 값과 빈 값은 저장 단계에서 막힌다
        assertThat(catalog.find("comic-sans")).isEmpty();
        assertThat(catalog.find(null)).isEmpty();
    }

    /**
     * code 가 그대로 화면 class 가 된다.
     * 그래서 code 를 class 로 바꾸는 변환 표를 따로 두지 않는다.
     */
    @Test
    void theCodeIsAlsoTheScreenClass() {
        assertThat(catalog.find("park-dahyun").orElseThrow().fontClass())
                .isEqualTo("diary-font-park-dahyun");
        // 목록 밖의 값은 class 자체가 붙지 않는다 (기본 글꼴로 그려진다)
        assertThat(DiaryLabelFont.cssClassOf(null)).isNull();
        assertThat(DiaryLabelFont.cssClassOf("NANUM_SQUARE")).isNull();
        assertThat(DiaryLabelFont.cssClassOf("../evil")).isNull();
    }

    /** 목록에 있는 글꼴은 실제로 화면에 붙일 규칙이 있어야 한다. */
    @Test
    void everyFontOnTheListHasAScreenRule() throws IOException {
        String css = Files.readString(DIARY_FONTS_CSS);
        for (DiaryLabelFont font : catalog.getFonts()) {
            assertThat(css)
                    .as("%s 의 글꼴 규칙이 diary-fonts.css 에 없습니다", font.code())
                    .contains("." + font.fontClass());
        }
    }
}
