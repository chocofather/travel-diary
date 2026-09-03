package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceTranslationMapperContractTest {

    @Test
    void countryCategoryTranslationsUseOneSafeBatchQuery() throws IOException {
        String mapper = read("src/main/resources/mapper/CountryCategoryMapper.xml");
        String select = between(mapper,
                "<select id=\"findTranslationsByCountryCategoryIds\"", "</select>");

        assertThat(select)
                .contains("FROM country_category_translations")
                .contains("country_category_id IN")
                .contains("<foreach collection=\"countryCategoryIds\"")
                .contains("#{countryCategoryId}")
                .contains("<otherwise>")
                .contains("WHERE 1 = 0")
                .contains("ORDER BY country_category_id, language_code, id")
                .doesNotContain("${");
    }

    @Test
    void categoryBaseRowsAndTranslationsUseBatchQueries() throws IOException {
        String mapper = read("src/main/resources/mapper/CategoryMapper.xml");
        String bases = between(mapper, "<select id=\"findByIds\"", "</select>");
        String translations = between(mapper,
                "<select id=\"findTranslationsByCategoryIds\"", "</select>");

        assertThat(bases)
                .contains("FROM categories")
                .contains("id IN")
                .contains("<foreach collection=\"categoryIds\"")
                .contains("#{categoryId}")
                .contains("WHERE 1 = 0")
                .doesNotContain("${");
        assertThat(translations)
                .contains("FROM category_translations")
                .contains("category_id IN")
                .contains("<foreach collection=\"categoryIds\"")
                .contains("#{categoryId}")
                .contains("WHERE 1 = 0")
                .contains("ORDER BY category_id, language_code, id")
                .doesNotContain("${");
    }

    @Test
    void schemaReferenceContainsBothTranslationTables() throws IOException {
        String schema = read("docs/db/travel_diary_schema_reference.md");

        assertTranslationTable(schema, "country_category_translations", "country_category_id",
                "country_categories");
        assertTranslationTable(schema, "category_translations", "category_id", "categories");
    }

    private void assertTranslationTable(String schema, String table, String foreignKey,
                                        String parentTable) {
        String ddl = between(schema, "CREATE TABLE `" + table + "`", ") ENGINE=InnoDB");
        assertThat(ddl)
                .contains("`id` bigint NOT NULL AUTO_INCREMENT")
                .contains("`" + foreignKey + "` bigint NOT NULL")
                .contains("`language_code` varchar(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL")
                .contains("`name` varchar(100) NOT NULL")
                .contains("(`" + foreignKey + "`,`language_code`)")
                .contains("(`language_code`,`" + foreignKey + "`)")
                .contains("REFERENCES `" + parentTable + "` (`id`) ON DELETE CASCADE");
        assertThat(schema.substring(schema.indexOf(ddl), schema.indexOf(ddl) + ddl.length() + 100))
                .contains("DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    }

    private String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
