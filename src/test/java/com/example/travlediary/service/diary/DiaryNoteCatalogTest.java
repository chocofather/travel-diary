package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryNoteColor;
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
    void theListFollowsTheManifestOrder() {
        assertThat(catalog.getStyles())
                .extracting(DiaryNoteStyle::code)
                .containsExactly(
                        "DATE_LABEL", "TITLE_LABEL", "TICKET_LABEL", "BORDER_LABEL",
                        "DASHED_LABEL", "TAG_LABEL", "CHECK_LABEL",
                        "VINTAGE_LABEL", "TORN_LABEL", "FLORAL_LABEL",
                        "HEART_LABEL", "RIBBON_LABEL",
                        "MEMO_SQUARE", "MEMO_ROUND", "MEMO_LINED", "MEMO_GRID",
                        "MEMO_DOT", "MEMO_CHECKLIST", "MEMO_TODO",
                        "MEMO_TORN", "MEMO_TAPED", "MEMO_VINTAGE",
                        "MEMO_PLANNER", "MEMO_FLORAL", "MEMO_HEART");
    }

    @Test
    void everyDesignOnTheListIsAllowedToBeSaved() {
        assertThat(catalog.getStyles()).isNotEmpty().allSatisfy(style ->
                assertThat(catalog.find(style.code())).as("%s", style.code()).isPresent());
        // 처음 넷은 그대로 남아 있다 (이미 붙여 둔 요소가 그 값을 들고 있다)
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
                .containsExactly("DATE_LABEL", "TITLE_LABEL", "TICKET_LABEL",
                        "BORDER_LABEL", "DASHED_LABEL", "TAG_LABEL", "CHECK_LABEL",
                        "VINTAGE_LABEL", "TORN_LABEL", "FLORAL_LABEL",
                        "HEART_LABEL", "RIBBON_LABEL");
        assertThat(catalog.getStyles(DiaryNoteStyle.CATEGORY_MEMO))
                .extracting(DiaryNoteStyle::code)
                .containsExactly("MEMO_SQUARE", "MEMO_ROUND", "MEMO_LINED",
                        "MEMO_GRID", "MEMO_DOT", "MEMO_CHECKLIST", "MEMO_TODO",
                        "MEMO_TORN", "MEMO_TAPED", "MEMO_VINTAGE",
                        "MEMO_PLANNER", "MEMO_FLORAL", "MEMO_HEART");
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
          모양도 색도 화면(CSS)이 code 로 정한다.
          실제 색상값·너비·글자 크기를 여기 적기 시작하면 디자인이 두 곳으로 갈린다.
          (colors 는 고를 수 있는 색의 이름표일 뿐 색상값이 아니다)
        */
        assertThat(manifest)
                .doesNotContain("#")
                .doesNotContain("px")
                .doesNotContain("fontSize")
                .doesNotContain("imageUrl");
    }

    @Test
    void theColoursAreTheirOwnListNotPartOfTheShapeNames() throws Exception {
        // 같은 모양의 색만 다른 값을 모양 목록에 따로 만들지 않는다
        assertThat(catalog.getColors())
                .extracting(DiaryNoteColor::code)
                .containsExactly("IVORY", "PINK", "SAGE", "SKY");
        assertThat(catalog.getStyles())
                .allSatisfy(style -> assertThat(style.code())
                        .doesNotContain("IVORY").doesNotContain("PINK")
                        .doesNotContain("SAGE").doesNotContain("SKY"));
    }

    @Test
    void onlyAColourOnTheListCanBeUsed() {
        for (String code : new String[]{"IVORY", "PINK", "SAGE", "SKY"}) {
            assertThat(catalog.findColor(code)).as("%s", code).isPresent();
        }
        assertThat(catalog.findColor("NEON")).isEmpty();
        assertThat(catalog.findColor("sage")).isEmpty();
        assertThat(catalog.findColor(null)).isEmpty();
    }

    @Test
    void everyShapeNamesAColourItFallsBackTo() {
        // 색을 고르지 않고 붙였을 때 쓸 색. 목록에 있는 색이어야 한다
        assertThat(catalog.getStyles()).allSatisfy(style ->
                assertThat(catalog.findColor(style.defaultColor()))
                        .as("%s", style.code()).isPresent());
    }

    @Test
    void aColourBecomesASafeClassName() {
        assertThat(catalog.findColor("SAGE")).get()
                .extracting(DiaryNoteColor::cssClass).isEqualTo("diary-note-color-sage");
        // 목록 밖의 값은 class 가 되지 않는다
        assertThat(DiaryNoteColor.cssClassOf("SAGE; content:'x'")).isNull();
        assertThat(DiaryNoteColor.cssClassOf(null)).isNull();
    }
}
