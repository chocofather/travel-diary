package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 라벨/떡메모지(NOTE)를 담기 위해 요소 저장소에 늘어난 칸 하나.
 *
 * <p>NOTE 는 글(text_content)과 디자인(style_type)을 함께 들고 다닌다.
 * 그 밖의 유형은 style_type 이 늘 비어 있다.
 */
class DiaryNoteElementMapperContractTest {

    @Test
    void theDesignColumnIsReadBackIntoTheElement() throws IOException {
        String mapper = elementMapper();

        assertThat(between(mapper, "<resultMap id=\"DiaryElementMap\"", "</resultMap>"))
                .contains("property=\"styleType\"")
                .contains("column=\"style_type\"");
        // 조회 컬럼 목록에도 함께 있어야 값이 실려 온다
        assertThat(between(mapper, "<sql id=\"DiaryElementColumns\">", "</sql>"))
                .contains("style_type");
    }

    @Test
    void theDesignIsSavedWithTheElementAndChangedWithIt() throws IOException {
        String mapper = elementMapper();

        String insert = between(mapper, "<insert id=\"insert\"", "</insert>");
        assertThat(insert)
                .contains("style_type")
                .contains("#{styleType}");

        String update = between(mapper, "<update id=\"update\"", "</update>");
        assertThat(update).contains("style_type = #{styleType}");
    }

    @Test
    void theColourTravelsWithTheElementJustLikeTheShape() throws IOException {
        String mapper = elementMapper();

        // 모양과 색은 서로 다른 칸이라 각각 실린다
        assertThat(between(mapper, "<resultMap id=\"DiaryElementMap\"", "</resultMap>"))
                .contains("property=\"colorType\"")
                .contains("column=\"color_type\"");
        assertThat(between(mapper, "<sql id=\"DiaryElementColumns\">", "</sql>"))
                .contains("color_type");
        assertThat(between(mapper, "<insert id=\"insert\"", "</insert>"))
                .contains("color_type")
                .contains("#{colorType}");
        assertThat(between(mapper, "<update id=\"update\"", "</update>"))
                .contains("color_type = #{colorType}");
    }

    @Test
    void theKindOfElementIsStillFixedWhenItIsFirstCreated() throws IOException {
        String update = between(elementMapper(), "<update id=\"update\"", "</update>");

        // 스티커가 메모지가 되거나 그 반대가 되지 않는다
        assertThat(update).doesNotContain("element_type =");
    }

    @Test
    void nothingElseAboutTheElementStoreChanged() throws IOException {
        String mapper = elementMapper();

        // 자리·크기·회전·겹침 순서는 예전 그대로다. NOTE 도 같은 칸을 쓴다
        assertThat(between(mapper, "<sql id=\"DiaryElementColumns\">", "</sql>"))
                .contains("position_x, position_y, width, height, rotation, z_index");
        assertThat(between(mapper, "<select id=\"findByPageId\"", "</select>"))
                .contains("ORDER BY z_index, id");
    }

    @Test
    void theDatabaseKnowsAboutTheNewKindOfElement() throws IOException {
        String table = diaryElementsTable();

        assertThat(table).contains("`style_type` varchar(30) DEFAULT NULL");
        assertThat(between(table, "`chk_diary_elements_type`", "),"))
                .contains("_utf8mb4'NOTE'")
                // 기존 세 가지도 그대로 남아 있어야 한다
                .contains("_utf8mb4'TEXT'")
                .contains("_utf8mb4'PHOTO'")
                .contains("_utf8mb4'STICKER'");
    }

    @Test
    void theDatabaseKeepsTheColourInItsOwnColumn() throws IOException {
        String table = diaryElementsTable();

        assertThat(table).contains("`color_type` varchar(20) DEFAULT NULL");
        // 모양 바로 다음 자리다 (같은 NOTE 의 두 축이 붙어 있다)
        assertThat(table.indexOf("`color_type`"))
                .isGreaterThan(table.indexOf("`style_type`"))
                .isLessThan(table.indexOf("`position_x`"));
    }

    @Test
    void aNoteWithoutAColourIsStillAllowed() throws IOException {
        String table = diaryElementsTable();

        /*
          색은 없어도 된다. 그때는 그 모양의 기본색으로 그린다.
          payload CHECK 가 색을 보기 시작하면 색 칸이 생기기 전에 만든 행이 모두 걸린다.
        */
        assertThat(between(table, "`chk_diary_elements_payload`", "),\n"))
                .doesNotContain("color_type");
    }

    @Test
    void addingTheColourChangedNothingElseAboutTheTable() throws IOException {
        String table = diaryElementsTable();

        // 컬럼 하나만 늘었다. 제약·인덱스·FK 는 그대로다
        assertThat(table)
                .contains("`chk_diary_elements_type`")
                .contains("`chk_diary_elements_payload`")
                .contains("`chk_diary_elements_position`")
                .contains("`chk_diary_elements_size`")
                .contains("`chk_diary_elements_rotation`")
                .contains("`chk_diary_elements_z_index`")
                .contains("KEY `idx_diary_elements_page` (`page_id`,`z_index`,`id`)")
                .contains("`fk_diary_elements_page` FOREIGN KEY (`page_id`)"
                        + " REFERENCES `diary_pages` (`id`) ON DELETE CASCADE");
    }

    @Test
    void theColoursTheCodeAllowsAreTheOnesTheSchemaDescribes() throws IOException {
        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
        String manifest = Files.readString(
                Path.of("src/main/resources/json/diary_notes.json"), StandardCharsets.UTF_8);

        // 문서에 적어 둔 색과 실제 허용 목록이 어긋나지 않게 한다
        for (String color : new String[]{"IVORY", "PINK", "SAGE", "SKY"}) {
            assertThat(manifest).as("%s", color).contains("\"" + color + "\"");
        }
        assertThat(schema).contains("현재 IVORY / PINK / SAGE / SKY");
        // NULL 의 뜻도 코드와 같은 말로 적어 둔다
        assertThat(schema).contains("NULL 이면 그 style 의 기본색으로 그린다");
    }

    @Test
    void eachKindOfElementFillsOnlyItsOwnColumns() throws IOException {
        String payload = between(diaryElementsTable(), "`chk_diary_elements_payload`", "),\n");

        // NOTE: 글과 디자인을 함께 갖고 그림은 갖지 않는다
        assertThat(payload).contains("(`element_type` = _utf8mb4'NOTE')"
                + " and (`text_content` is not null)"
                + " and (`image_url` is null)"
                + " and (`style_type` is not null)");
        // TEXT / PHOTO / STICKER 는 예전 규칙 그대로이고, 디자인 칸은 비어 있다
        assertThat(payload).contains("(`element_type` = _utf8mb4'TEXT')"
                + " and (`text_content` is not null)"
                + " and (`image_url` is null)"
                + " and (`style_type` is null)");
        assertThat(payload).contains("(`element_type` in (_utf8mb4'PHOTO',_utf8mb4'STICKER'))"
                + " and (`image_url` is not null)"
                + " and (`text_content` is null)"
                + " and (`style_type` is null)");
    }

    @Test
    void anEmptyNoteIsAllowedButAMissingOneIsNot() throws IOException {
        String payload = between(diaryElementsTable(), "`chk_diary_elements_payload`", "),\n");

        /*
          붙인 직후에는 아직 적은 글이 없다.
          IS NOT NULL 이라 빈 문자열('')은 지나가고 NULL 만 막힌다.
          길이를 재는 조건을 걸면 글을 쓰기 전에는 라벨을 붙일 수 없게 된다.
        */
        assertThat(payload).doesNotContain("length(");
        assertThat(payload).doesNotContain("<> _utf8mb4''");
    }

    @Test
    void theOtherRulesOfTheElementTableAreUntouched() throws IOException {
        String table = diaryElementsTable();

        // 자리·크기·회전·겹침 순서 규칙과 인덱스는 그대로다
        assertThat(table)
                .contains("`chk_diary_elements_position`")
                .contains("`chk_diary_elements_size`")
                .contains("`chk_diary_elements_rotation`")
                .contains("`chk_diary_elements_z_index`")
                .contains("KEY `idx_diary_elements_page` (`page_id`,`z_index`,`id`)")
                .contains("`fk_diary_elements_page` FOREIGN KEY (`page_id`)"
                        + " REFERENCES `diary_pages` (`id`) ON DELETE CASCADE");
    }

    /** 문서에 옮겨 적어 둔 실제 DB 구조. (스키마 기준은 언제나 이 파일이다) */
    private String diaryElementsTable() throws IOException {
        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
        return between(schema, "CREATE TABLE `diary_elements`", "ENGINE=");
    }

    private String elementMapper() throws IOException {
        return resource("/mapper/DiaryElementMapper.xml");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("end %s", end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
