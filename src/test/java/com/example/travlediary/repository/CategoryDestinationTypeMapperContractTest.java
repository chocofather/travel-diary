package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여행지 유형별 카테고리 마스터 조회 계약.
 * 사용 이력(destination_categories)이 아니라 category_destination_types 매핑을 읽어야 한다.
 */
class CategoryDestinationTypeMapperContractTest {

    @Test
    void destinationTypeQueryReadsTheMasterMappingTable() throws IOException {
        String select = between(mapperXml(), "<select id=\"findByDestinationType\"", "</select>");

        assertThat(select)
                .contains("FROM category_destination_types cdt")
                .contains("JOIN categories c ON c.id = cdt.category_id")
                .contains("WHERE cdt.destination_type = #{destinationType}")
                // 기존 전체 목록과 같은 정렬(id)
                .contains("ORDER BY c.id")
                // 동적 SQL / 사용 이력 테이블 금지
                .doesNotContain("${")
                .doesNotContain("destination_categories");
    }

    @Test
    void existingCategoryQueriesStayUntouched() throws IOException {
        String mapper = mapperXml();

        assertThat(between(mapper, "<select id=\"findAll\"", "</select>"))
                .contains("SELECT id, name FROM categories")
                .contains("ORDER BY id");
        assertThat(mapper)
                .contains("<select id=\"getCategoryNamesByDestinationId\"")
                .contains("<insert id=\"insert\"")
                .contains("<delete id=\"deleteById\"");
    }

    @Test
    void everyDeclaredMapperMethodHasAStatement() throws IOException {
        String mapper = mapperXml();
        String mapperInterface = Files.readString(
                Path.of("src/main/java/com/example/travlediary/repository/category/CategoryMapper.java"),
                StandardCharsets.UTF_8);

        // 선언만 있고 XML statement 가 없으면 호출 시 BindingException 이 난다
        for (String id : new String[]{
                "findById",
                "update",
                "countByNameExcludingId",
                "findCategoryDestinationTypesByCategoryId",
                "insertCategoryDestinationType",
                "deleteCategoryDestinationTypesByCategoryId",
                "countDestinationsByCategoryId"}) {
            assertThat(mapperInterface).as("interface declares %s", id).contains(id);
            assertThat(mapper).as("xml defines %s", id).contains("id=\"" + id + "\"");
        }
    }

    @Test
    void categoryWriteStatementsMatchTheSchema() throws IOException {
        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
        String mapper = mapperXml();

        assertThat(between(schema, "CREATE TABLE `categories`", ") ENGINE=InnoDB"))
                .contains("`name` varchar(100) NOT NULL")
                .contains("UNIQUE KEY `name_UNIQUE` (`name`)");

        assertThat(between(mapper, "<select id=\"findById\"", "</select>"))
                .contains("SELECT id, name FROM categories WHERE id = #{id}");
        // 이름만 바꾸고 id 는 조건으로만 쓴다
        assertThat(between(mapper, "<update id=\"update\"", "</update>"))
                .contains("UPDATE categories")
                .contains("SET name = #{name}")
                .contains("WHERE id = #{id}");
        // 신규 등록(excludeId = null)에서도 그대로 쓸 수 있어야 한다
        assertThat(between(mapper, "<select id=\"countByNameExcludingId\"", "</select>"))
                .contains("FROM categories")
                .contains("WHERE name = #{name}")
                .contains("<if test=\"excludeId != null\">")
                .contains("AND id &lt;&gt; #{excludeId}");
    }

    @Test
    void masterMappingCanBeWrittenAndReadPerCategory() throws IOException {
        String mapper = mapperXml();

        // enum 이름을 그대로 저장한다
        assertThat(between(mapper, "<insert id=\"insertCategoryDestinationType\"", "</insert>"))
                .contains("INSERT INTO category_destination_types (category_id, destination_type)")
                .contains("VALUES (#{categoryId}, #{destinationType})")
                .doesNotContain("${");
        // 수정은 "전체 삭제 후 선택값 재삽입" 방식이라 categoryId 기준 전체 삭제가 필요하다
        assertThat(between(mapper,
                "<delete id=\"deleteCategoryDestinationTypesByCategoryId\"", "</delete>"))
                .contains("DELETE FROM category_destination_types WHERE category_id = #{categoryId}");
        assertThat(between(mapper,
                "<select id=\"findCategoryDestinationTypesByCategoryId\"", "</select>"))
                .contains("SELECT category_id, destination_type")
                .contains("FROM category_destination_types")
                .contains("WHERE category_id = #{categoryId}")
                .doesNotContain("${");
    }

    @Test
    void usageCountReadsTheDestinationLinkTableNotTheMasterMapping() throws IOException {
        String select = between(mapperXml(),
                "<select id=\"countDestinationsByCategoryId\"", "</select>");

        // 삭제 차단의 기준은 실제 여행지 연결(destination_categories)이다
        assertThat(select)
                .contains("SELECT COUNT(*)")
                .contains("FROM destination_categories")
                .contains("WHERE category_id = #{categoryId}")
                .contains("resultType=\"int\"")
                // 마스터 매핑 건수를 세면 안 된다
                .doesNotContain("category_destination_types")
                .doesNotContain("${");
    }

    private String mapperXml() throws IOException {
        return resource("/mapper/CategoryMapper.xml");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
