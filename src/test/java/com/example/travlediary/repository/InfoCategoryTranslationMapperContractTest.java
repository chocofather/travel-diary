package com.example.travlediary.repository;

import com.example.travlediary.model.InfoCategoryTranslation;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 정보 카테고리 번역 Mapper 계약.
 *
 * <p>여행정보(GENERAL)와 축제·행사(FESTIVAL) 카테고리가 같은 테이블을 함께 쓴다.
 */
class InfoCategoryTranslationMapperContractTest {

    private static final String NAMESPACE =
            "com.example.travlediary.repository.category.InfoCategoryMapper";

    @Test
    void translationResultMapBindsEveryLiveColumnToTheModel() throws IOException {
        ResultMap resultMap = mapperConfiguration()
                .getResultMap(NAMESPACE + ".InfoCategoryTranslationResultMap");
        Map<String, String> columns = resultMap.getResultMappings().stream()
                .collect(Collectors.toMap(mapping -> mapping.getProperty(),
                        mapping -> mapping.getColumn()));

        assertThat(resultMap.getType()).isEqualTo(InfoCategoryTranslation.class);
        assertThat(columns)
                .containsEntry("id", "id")
                .containsEntry("infoCategoryId", "info_category_id")
                .containsEntry("languageCode", "language_code")
                .containsEntry("name", "name");
    }

    @Test
    void singleCategoryTranslationsAreReadInDeterministicOrder() throws IOException {
        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".findTranslationsByCategoryId")
                .getBoundSql(3L);

        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "SELECT id, info_category_id, language_code, name "
                        + "FROM info_category_translations WHERE info_category_id = ? "
                        + "ORDER BY language_code ASC, id ASC");
        assertThat(boundSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("infoCategoryId");
    }

    @Test
    void manyCategoryTranslationsAreReadInOneQueryWithoutNPlusOne() throws IOException {
        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".findTranslationsByCategoryIds")
                .getBoundSql(Map.of("infoCategoryIds", List.of(3L, 4L, 5L)));

        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "SELECT id, info_category_id, language_code, name "
                        + "FROM info_category_translations WHERE info_category_id IN (? , ? , ?) "
                        + "ORDER BY info_category_id ASC, language_code ASC, id ASC");
        assertThat(boundSql.getParameterMappings()).hasSize(3);
    }

    @Test
    void emptyCategoryIdListNeverWidensIntoAFullTableRead() throws IOException {
        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".findTranslationsByCategoryIds")
                .getBoundSql(Map.of("infoCategoryIds", List.of()));

        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "SELECT id, info_category_id, language_code, name "
                        + "FROM info_category_translations WHERE 1 = 0 "
                        + "ORDER BY info_category_id ASC, language_code ASC, id ASC");
        assertThat(boundSql.getParameterMappings()).isEmpty();
    }

    @Test
    void insertWritesOneLanguageRowForTheGivenCategory() throws IOException {
        InfoCategoryTranslation translation = new InfoCategoryTranslation();
        translation.setInfoCategoryId(3L);
        translation.setLanguageCode("en");
        translation.setName("Seasonal travel");

        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".insertTranslation")
                .getBoundSql(translation);

        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "INSERT INTO info_category_translations "
                        + "(info_category_id, language_code, name) VALUES (?, ?, ?)");
        assertThat(boundSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("infoCategoryId", "languageCode", "name");
    }

    @Test
    void updateAndDeleteTouchOnlyTheRequestedLanguageRow() throws IOException {
        Configuration configuration = mapperConfiguration();
        InfoCategoryTranslation translation = new InfoCategoryTranslation();
        translation.setInfoCategoryId(3L);
        translation.setLanguageCode("ja");
        translation.setName("季節の旅");

        BoundSql update = configuration
                .getMappedStatement(NAMESPACE + ".updateTranslation").getBoundSql(translation);
        BoundSql delete = configuration
                .getMappedStatement(NAMESPACE + ".deleteTranslation")
                .getBoundSql(Map.of("infoCategoryId", 3L, "languageCode", "zh-CN"));

        // 언어 코드는 조건일 뿐 갱신 대상이 아니다. 바꾸면 다른 언어 줄을 덮어쓰게 된다.
        assertThat(normalize(update.getSql())).isEqualTo(
                "UPDATE info_category_translations SET name = ? "
                        + "WHERE info_category_id = ? AND language_code = ?");
        assertThat(update.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("name", "infoCategoryId", "languageCode");
        assertThat(normalize(delete.getSql())).isEqualTo(
                "DELETE FROM info_category_translations "
                        + "WHERE info_category_id = ? AND language_code = ?");
        assertThat(delete.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("infoCategoryId", "languageCode");
    }

    @Test
    void addingTranslationStatementsDoesNotChangeBaseCategoryStatements() throws IOException {
        Configuration configuration = mapperConfiguration();
        BoundSql findAll = configuration
                .getMappedStatement(NAMESPACE + ".findAll").getBoundSql(null);
        BoundSql visibleByType = configuration
                .getMappedStatement(NAMESPACE + ".findVisibleByContentType")
                .getBoundSql(Map.of("contentType", "FESTIVAL"));

        assertThat(normalize(findAll.getSql())).isEqualTo(
                "SELECT id, name, content_type, display_order, is_visible "
                        + "FROM info_categories ORDER BY display_order ASC, id ASC");
        assertThat(normalize(visibleByType.getSql()))
                .contains("FROM info_categories")
                .doesNotContain("info_category_translations");
    }

    @Test
    void translationTableMatchesTheLiveDatabaseContract() throws IOException {
        String translations = between(schema(),
                "CREATE TABLE `info_category_translations`",
                "/*!40101 SET character_set_client = @saved_cs_client */;");

        assertThat(translations)
                .contains("`id` bigint NOT NULL AUTO_INCREMENT")
                .contains("`info_category_id` bigint NOT NULL")
                .contains("`language_code` varchar(10) CHARACTER SET ascii "
                        + "COLLATE ascii_bin NOT NULL")
                .contains("`name` varchar(100)")
                // 한 카테고리에 같은 언어 줄이 둘 생기지 않게 막는 키.
                .contains("UNIQUE KEY `uk_info_category_translation` "
                        + "(`info_category_id`,`language_code`)")
                .contains("KEY `idx_info_category_translation_locale` "
                        + "(`language_code`,`info_category_id`)")
                .contains("CONSTRAINT `fk_info_category_translation` "
                        + "FOREIGN KEY (`info_category_id`) REFERENCES `info_categories` (`id`) "
                        + "ON DELETE CASCADE");
    }

    private Configuration mapperConfiguration() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream input = getClass().getResourceAsStream("/mapper/InfoCategoryMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, "mapper/InfoCategoryMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String schema() throws IOException {
        return Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ")
                .replaceAll("\\( ", "(")
                .replaceAll(" \\)", ")")
                .trim();
    }
}
