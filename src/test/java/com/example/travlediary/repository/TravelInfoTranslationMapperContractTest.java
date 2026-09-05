package com.example.travlediary.repository;

import com.example.travlediary.model.TravelInfoTranslation;
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

class TravelInfoTranslationMapperContractTest {

    private static final String NAMESPACE =
            "com.example.travlediary.repository.travelinfo.TravelInfoMapper";

    @Test
    void translationResultMapBindsEveryLiveColumnToTheModel() throws IOException {
        ResultMap resultMap = mapperConfiguration()
                .getResultMap(NAMESPACE + ".TravelInfoTranslationResultMap");
        Map<String, String> columns = resultMap.getResultMappings().stream()
                .collect(Collectors.toMap(mapping -> mapping.getProperty(),
                        mapping -> mapping.getColumn()));

        assertThat(resultMap.getType()).isEqualTo(TravelInfoTranslation.class);
        assertThat(columns)
                .containsEntry("id", "id")
                .containsEntry("travelInfoId", "travel_info_id")
                .containsEntry("languageCode", "language_code")
                .containsEntry("title", "title")
                .containsEntry("content", "content");
    }

    @Test
    void singleTravelInfoTranslationsAreReadInDeterministicOrder() throws IOException {
        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".findTranslationsByInfoId")
                .getBoundSql(7L);

        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "SELECT id, travel_info_id, language_code, title, content "
                        + "FROM travel_info_translations WHERE travel_info_id = ? "
                        + "ORDER BY language_code ASC, id ASC");
        assertThat(boundSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("infoId");
    }

    @Test
    void manyTravelInfoTranslationsAreReadInOneQueryWithoutNPlusOne() throws IOException {
        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".findTranslationsByInfoIds")
                .getBoundSql(Map.of("infoIds", List.of(7L, 8L, 9L)));

        // 여행정보 수만큼 조회가 늘지 않도록 IN 한 번으로 묶는다.
        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "SELECT id, travel_info_id, language_code, title, content "
                        + "FROM travel_info_translations WHERE travel_info_id IN (? , ? , ?) "
                        + "ORDER BY travel_info_id ASC, language_code ASC, id ASC");
        assertThat(boundSql.getParameterMappings()).hasSize(3);
    }

    @Test
    void emptyTravelInfoIdListNeverWidensIntoAFullTableRead() throws IOException {
        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".findTranslationsByInfoIds")
                .getBoundSql(Map.of("infoIds", List.of()));

        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "SELECT id, travel_info_id, language_code, title, content "
                        + "FROM travel_info_translations WHERE 1 = 0 "
                        + "ORDER BY travel_info_id ASC, language_code ASC, id ASC");
        assertThat(boundSql.getParameterMappings()).isEmpty();
    }

    @Test
    void insertWritesOneLanguageRowForTheGivenTravelInfo() throws IOException {
        TravelInfoTranslation translation = new TravelInfoTranslation();
        translation.setTravelInfoId(7L);
        translation.setLanguageCode("en");
        translation.setTitle("Seasonal travel");
        translation.setContent("<p>Seasonal travel</p>");

        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".insertTranslation")
                .getBoundSql(translation);

        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "INSERT INTO travel_info_translations "
                        + "(travel_info_id, language_code, title, content) "
                        + "VALUES (?, ?, ?, ?)");
        assertThat(boundSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("travelInfoId", "languageCode", "title", "content");
    }

    @Test
    void updateTouchesOnlyTheRequestedLanguageRow() throws IOException {
        TravelInfoTranslation translation = new TravelInfoTranslation();
        translation.setTravelInfoId(7L);
        translation.setLanguageCode("ja");
        translation.setTitle("季節の旅");
        translation.setContent("<p>季節の旅</p>");

        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".updateTranslation")
                .getBoundSql(translation);

        // 언어 코드는 조건일 뿐 갱신 대상이 아니다. 바꾸면 다른 언어 줄을 덮어쓰게 된다.
        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "UPDATE travel_info_translations SET title = ?, content = ? "
                        + "WHERE travel_info_id = ? AND language_code = ?");
        assertThat(boundSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("title", "content", "travelInfoId", "languageCode");
    }

    @Test
    void deleteRemovesOnlyTheRequestedLanguageRow() throws IOException {
        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".deleteTranslation")
                .getBoundSql(Map.of("travelInfoId", 7L, "languageCode", "zh-CN"));

        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "DELETE FROM travel_info_translations "
                        + "WHERE travel_info_id = ? AND language_code = ?");
        assertThat(boundSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("travelInfoId", "languageCode");
    }

    @Test
    void addingTranslationStatementsDoesNotChangeBaseTravelInfoStatements() throws IOException {
        Configuration configuration = mapperConfiguration();
        BoundSql detail = configuration
                .getMappedStatement(NAMESPACE + ".findPublicDetailById").getBoundSql(7L);
        BoundSql findById = configuration
                .getMappedStatement(NAMESPACE + ".findById").getBoundSql(7L);

        assertThat(normalize(detail.getSql()))
                .contains("FROM travel_info ti JOIN info_categories ic ON ic.id = ti.category_id")
                .doesNotContain("travel_info_translations");
        assertThat(normalize(findById.getSql())).isEqualTo(
                "SELECT id, title, content, scope, content_type, created_at, updated_at, "
                        + "category_id, views, user_id FROM travel_info WHERE id = ?");
    }

    @Test
    void translationTableMatchesTheLiveDatabaseContract() throws IOException {
        String translations = between(schema(),
                "CREATE TABLE `travel_info_translations`",
                "/*!40101 SET character_set_client = @saved_cs_client */;");

        assertThat(translations)
                .contains("`id` bigint NOT NULL AUTO_INCREMENT")
                .contains("`travel_info_id` bigint NOT NULL")
                .contains("`language_code` varchar(10) CHARACTER SET ascii "
                        + "COLLATE ascii_bin NOT NULL")
                .contains("`title` varchar(255)")
                .contains("`content` mediumtext")
                // 한 여행정보에 같은 언어 줄이 둘 생기지 않게 막는 키.
                .contains("UNIQUE KEY `uk_travel_info_translation` "
                        + "(`travel_info_id`,`language_code`)")
                .contains("KEY `idx_travel_info_translation_locale` "
                        + "(`language_code`,`travel_info_id`)")
                .contains("CONSTRAINT `fk_travel_info_translation` "
                        + "FOREIGN KEY (`travel_info_id`) REFERENCES `travel_info` (`id`) "
                        + "ON DELETE CASCADE");
    }

    private Configuration mapperConfiguration() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream input = getClass().getResourceAsStream("/mapper/TravelInfoMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, "mapper/TravelInfoMapper.xml",
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
