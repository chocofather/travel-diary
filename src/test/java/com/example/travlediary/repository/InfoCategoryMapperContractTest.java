package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class InfoCategoryMapperContractTest {

    @Test
    void listUsesInfoCategoriesWithStableDisplayOrder() throws IOException {
        String mapper = resource("/mapper/InfoCategoryMapper.xml");
        String query = between(mapper, "<select id=\"findAll\"", "</select>");

        assertThat(query)
                .contains("FROM info_categories")
                .contains("ORDER BY display_order ASC, id ASC");
    }

    @Test
    void publicListUsesOnlyVisibleCategoriesWithStableDisplayOrder() throws IOException {
        String mapper = resource("/mapper/InfoCategoryMapper.xml");
        String query = between(mapper, "<select id=\"findVisible\"", "</select>");

        assertThat(query)
                .contains("FROM info_categories")
                .contains("WHERE is_visible = 1")
                .contains("ORDER BY display_order ASC, id ASC");
    }

    @Test
    void insertAndUpdatePersistAllManagedFields() throws IOException {
        String mapper = resource("/mapper/InfoCategoryMapper.xml");
        String insert = between(mapper, "<insert id=\"insert\"", "</insert>");
        String update = between(mapper, "<update id=\"update\"", "</update>");

        assertThat(insert)
                .contains("INSERT INTO info_categories (name, display_order, is_visible)")
                .contains("#{name}", "#{displayOrder}", "#{isVisible}");
        assertThat(update)
                .contains("UPDATE info_categories")
                .contains("name = #{name}")
                .contains("display_order = #{displayOrder}")
                .contains("is_visible = #{isVisible}")
                .contains("WHERE id = #{id}");
    }

    @Test
    void duplicateCheckCanExcludeCurrentCategory() throws IOException {
        String mapper = resource("/mapper/InfoCategoryMapper.xml");
        String query = between(mapper, "<select id=\"countByNameExcludingId\"", "</select>");

        assertThat(query)
                .contains("WHERE name = #{name}")
                .contains("<if test=\"excludeId != null\">")
                .contains("AND id &lt;&gt; #{excludeId}");
    }

    @Test
    void deleteChecksTravelInfoReferencesAndTargetsOnlyTheCategory() throws IOException {
        String mapper = resource("/mapper/InfoCategoryMapper.xml");
        String usageCheck = between(mapper, "<select id=\"countTravelInfoByCategoryId\"", "</select>");
        String delete = between(mapper, "<delete id=\"deleteById\"", "</delete>");

        assertThat(usageCheck)
                .contains("SELECT COUNT(*)")
                .contains("FROM travel_info")
                .contains("WHERE category_id = #{categoryId}");
        assertThat(delete)
                .contains("DELETE FROM info_categories")
                .contains("WHERE id = #{id}")
                .doesNotContain("travel_info");
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
        assertThat(startIndex).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
