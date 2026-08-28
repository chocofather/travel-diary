package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryNoteStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 라벨/떡메모지 디자인 목록은 resources/json/diary_notes.json 한 곳이 기준이다.
 * 여기에 없는 값은 저장할 수 없다. (스티커 목록과 같은 방식)
 */
class DiaryNoteCatalogTest {

    private DiaryNoteCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new DiaryNoteCatalog();
        catalog.load();
    }

    @Test
    void theFirstFourDesignsAreTwoLabelsAndTwoMemos() {
        assertThat(catalog.getStyles())
                .extracting(DiaryNoteStyle::code)
                .containsExactly("DATE_LABEL", "TITLE_LABEL", "MEMO_SQUARE", "MEMO_ROUND");
    }

    @Test
    void everyDesignOnTheListIsAllowedToBeSaved() {
        for (String code : new String[]{
                "DATE_LABEL", "TITLE_LABEL", "MEMO_SQUARE", "MEMO_ROUND"}) {
            assertThat(catalog.find(code)).as("%s", code).isPresent();
        }
    }

    @Test
    void aDesignThatIsNotOnTheListIsNotAllowed() {
        // 화면이 보낸 값을 그대로 믿지 않는다. 모르는 값은 그릴 모양이 없다
        assertThat(catalog.find("MEMO_TRIANGLE")).isEmpty();
        assertThat(catalog.find("")).isEmpty();
        assertThat(catalog.find(null)).isEmpty();
        // 대소문자까지 그대로 맞아야 한다 (저장되는 값이 곧 이 code 다)
        assertThat(catalog.find("memo_square")).isEmpty();
    }

    @Test
    void aDesignIsEitherALabelOrAMemo() {
        assertThat(catalog.getStyles(DiaryNoteStyle.CATEGORY_LABEL))
                .extracting(DiaryNoteStyle::code)
                .containsExactly("DATE_LABEL", "TITLE_LABEL");
        assertThat(catalog.getStyles(DiaryNoteStyle.CATEGORY_MEMO))
                .extracting(DiaryNoteStyle::code)
                .containsExactly("MEMO_SQUARE", "MEMO_ROUND");
        // 둘로 나누면 전체가 된다. 어느 쪽에도 없는 디자인은 없다
        assertThat(catalog.getStyles(DiaryNoteStyle.CATEGORY_LABEL).size()
                + catalog.getStyles(DiaryNoteStyle.CATEGORY_MEMO).size())
                .isEqualTo(catalog.getStyles().size());
    }

    @Test
    void everyDesignCarriesAReadableName() {
        // 나중에 고르는 자리에 그대로 나올 이름이다
        assertThat(catalog.getStyles())
                .allSatisfy(style -> assertThat(style.label()).isNotBlank());
        assertThat(catalog.find("DATE_LABEL")).get()
                .extracting(DiaryNoteStyle::label).isEqualTo("날짜 라벨");
    }

    @Test
    void theListCarriesNoDrawingDetails() throws Exception {
        String manifest = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/json/diary_notes.json"));

        /*
          모양은 화면(CSS)이 code 로 정한다.
          색·너비·글자 크기를 여기 적기 시작하면 디자인이 두 곳으로 갈린다.
        */
        assertThat(manifest)
                .doesNotContain("color")
                .doesNotContain("width")
                .doesNotContain("fontSize")
                .doesNotContain("imageUrl");
    }
}
