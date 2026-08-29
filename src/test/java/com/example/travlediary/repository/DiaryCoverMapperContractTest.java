package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 커스텀 표지 저장소의 약속.
 *
 * <p>보관함 원본과 실제 적용본은 같은 모양의 요소를 담지만 서로 이어져 있지 않고,
 * 어느 쪽이든 남의 것을 건드릴 수 없어야 한다. SQL 에도 소유 조건이 함께 들어간다.
 */
class DiaryCoverMapperContractTest {

    @Test
    void everyElementQueryKeepsTheStackingOrder() throws IOException {
        // 겹침 순서를 그대로 복원해야 화면이 저장할 때와 같아진다
        assertThat(between(resource("/mapper/DiaryCoverDesignElementMapper.xml"),
                "<select id=\"findAllByDesignId\"", "</select>"))
                .contains("ORDER BY z_index ASC, id ASC");
        assertThat(between(resource("/mapper/DiaryCoverElementMapper.xml"),
                "<select id=\"findAllByCoverId\"", "</select>"))
                .contains("ORDER BY z_index ASC, id ASC");
    }

    /**
     * 보관함 목록은 요소를 한 번에 읽는다.
     *
     * <p>카드마다 따로 물으면 디자인 수만큼 질의가 늘어난다.
     * 남의 디자인이 섞이지 않도록 디자인 쪽과 이어 붙여 소유자도 함께 확인한다.
     */
    @Test
    void theShelfReadsEveryDesignsElementsInOneGo() throws IOException {
        String batch = between(resource("/mapper/DiaryCoverDesignElementMapper.xml"),
                "<select id=\"findAllByDesignIds\"", "</select>");

        assertThat(batch).contains("e.design_id IN").contains("<foreach");
        assertThat(batch).contains("JOIN diary_cover_designs d ON d.id = e.design_id")
                .contains("d.user_id = #{userId}");
        // 디자인별로 겹침 순서를 그대로 복원할 수 있게 함께 정렬한다
        assertThat(batch).contains("ORDER BY e.design_id, e.z_index ASC, e.id ASC");
    }

    @Test
    void aDesignCanOnlyBeReachedByItsOwner() throws IOException {
        String mapper = resource("/mapper/DiaryCoverDesignMapper.xml");

        for (String id : new String[]{"findByIdAndUserId", "findAllByUserId"}) {
            assertThat(between(mapper, "id=\"" + id + "\"", "</select>"))
                    .as("%s", id).contains("user_id = #{userId}");
        }
        // 수정/삭제도 SQL 조건으로 한 번 더 막는다 (서비스 검증과 이중 방어)
        assertThat(between(mapper, "<update id=\"update\"", "</update>"))
                .contains("user_id = #{userId}");
        assertThat(between(mapper, "<delete id=\"deleteByIdAndUserId\"", "</delete>"))
                .contains("user_id = #{userId}");
    }

    /** 표지 행에는 소유자 칸이 없다. 그래서 다이어리와 이어 붙여 확인한다. */
    @Test
    void aCoverBorrowsItsOwnerFromTheDiary() throws IOException {
        String mapper = resource("/mapper/DiaryCoverMapper.xml");

        for (String block : new String[]{
                between(mapper, "<select id=\"findByDiaryIdAndUserId\"", "</select>"),
                between(mapper, "<update id=\"update\"", "</update>"),
                between(mapper, "<delete id=\"deleteByDiaryIdAndUserId\"", "</delete>")}) {
            assertThat(block)
                    .contains("JOIN diaries d ON d.id = c.diary_id")
                    .contains("d.user_id = #{userId}");
        }
        // 소유권을 보지 않는 조회는 따로 있다. (서비스가 확인한 뒤에만 쓴다)
        assertThat(between(mapper, "<select id=\"findByDiaryId\"", "</select>"))
                .doesNotContain("user_id");
    }

    /** 요소는 부모(디자인/표지)를 벗어나 다뤄지지 않는다. */
    @Test
    void anElementNeverLeavesItsParent() throws IOException {
        String design = resource("/mapper/DiaryCoverDesignElementMapper.xml");
        String cover = resource("/mapper/DiaryCoverElementMapper.xml");

        for (String id : new String[]{"findById", "updatePosition", "updateSize",
                "updateRotation", "updateLayer", "updateText", "deleteById"}) {
            assertThat(design).as("design %s", id).contains("id=\"" + id + "\"");
            assertThat(cover).as("cover %s", id).contains("id=\"" + id + "\"");
        }
        assertThat(design.split("AND design_id = #\\{designId\\}", -1).length - 1)
                .as("디자인 요소 SQL 의 소속 조건 수").isGreaterThanOrEqualTo(7);
        assertThat(cover.split("AND cover_id = #\\{coverId\\}", -1).length - 1)
                .as("표지 요소 SQL 의 소속 조건 수").isGreaterThanOrEqualTo(7);
        // 유형은 등록할 때 정해지고 그 뒤로 바뀌지 않는다
        assertThat(design).doesNotContain("element_type = #{elementType},");
        assertThat(cover).doesNotContain("element_type = #{elementType},");
    }

    /**
     * 사진의 모습은 그 칸 하나만 바꾼다.
     *
     * <p>자리/크기/각도/겹침 순서가 함께 바뀌면 모양만 바꾼다는 약속이 깨진다.
     * 원본과 적용본이 같은 칸을 쓰므로 두 저장소에 같은 쿼리를 둔다.
     */
    @Test
    void changingHowAPhotoLooksTouchesNothingElse() throws IOException {
        String design = resource("/mapper/DiaryCoverDesignElementMapper.xml");
        String cover = resource("/mapper/DiaryCoverElementMapper.xml");

        for (String mapper : new String[]{design, cover}) {
            String update = between(mapper, "<update id=\"updatePhotoStyle\"", "</update>");
            assertThat(update).contains("photo_style = #{photoStyle}");
            for (String untouched : new String[]{
                    "position_x", "position_y", "width", "height", "rotation", "z_index"}) {
                assertThat(update).as("%s", untouched).doesNotContain(untouched + " =");
            }
            // 새로 붙일 때도 이 칸을 함께 저장한다
            assertThat(between(mapper, "<insert id=\"insert\"", "</insert>"))
                    .contains("photo_style").contains("#{photoStyle}");
            assertThat(mapper).contains("property=\"photoStyle\"").contains("column=\"photo_style\"");
        }
    }

    /** 원본과 적용본은 FK 로 이어져 있지 않다. (값을 복사해서 만든다) */
    @Test
    void theSavedDesignAndTheAppliedCoverAreNotLinked() throws IOException {
        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
        String covers = between(schema, "CREATE TABLE `diary_covers`", "ENGINE=");

        assertThat(covers).doesNotContain("source_design_id").doesNotContain("cover_kind");
        // 한 다이어리에 표지 하나
        assertThat(covers).contains("UNIQUE KEY `uq_diary_covers_diary` (`diary_id`)");
        // 적용본의 요소도 표지에만 매달린다
        assertThat(between(schema, "CREATE TABLE `diary_cover_elements`", "ENGINE="))
                .contains("REFERENCES `diary_covers` (`id`) ON DELETE CASCADE")
                .doesNotContain("diary_cover_designs");
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
