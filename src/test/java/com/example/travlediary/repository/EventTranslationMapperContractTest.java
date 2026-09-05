package com.example.travlediary.repository;

import com.example.travlediary.model.EventTranslation;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class EventTranslationMapperContractTest {

    private static final String NAMESPACE =
            "com.example.travlediary.repository.event.EventMapper";

    @Test
    void translationResultMapBindsEveryLiveColumnToTheModel() throws IOException {
        ResultMap resultMap = mapperConfiguration()
                .getResultMap(NAMESPACE + ".EventTranslationResultMap");
        Map<String, String> columns = resultMap.getResultMappings().stream()
                .collect(Collectors.toMap(mapping -> mapping.getProperty(),
                        mapping -> mapping.getColumn()));

        assertThat(resultMap.getType()).isEqualTo(EventTranslation.class);
        assertThat(columns)
                .containsEntry("id", "id")
                .containsEntry("eventId", "event_id")
                .containsEntry("languageCode", "language_code")
                .containsEntry("title", "title")
                .containsEntry("description", "description")
                .containsEntry("posterImg", "poster_img");
        // 대표 이미지는 언어와 무관한 공용 이미지라 번역 테이블에 없다.
        assertThat(columns).doesNotContainKey("eventImg");
    }

    @Test
    void singleEventTranslationsAreReadInDeterministicOrder() throws IOException {
        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".findTranslationsByEventId")
                .getBoundSql(7L);

        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "SELECT id, event_id, language_code, title, description, poster_img "
                        + "FROM event_translations WHERE event_id = ? "
                        + "ORDER BY language_code ASC, id ASC");
        assertThat(boundSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("eventId");
    }

    @Test
    void manyEventTranslationsAreReadInOneQueryWithoutNPlusOne() throws IOException {
        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".findTranslationsByEventIds")
                .getBoundSql(Map.of("eventIds", List.of(7L, 8L, 9L)));

        // 이벤트 수만큼 조회가 늘지 않도록 IN 한 번으로 묶는다.
        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "SELECT id, event_id, language_code, title, description, poster_img "
                        + "FROM event_translations WHERE event_id IN (? , ? , ?) "
                        + "ORDER BY event_id ASC, language_code ASC, id ASC");
        assertThat(boundSql.getParameterMappings()).hasSize(3);
    }

    @Test
    void emptyEventIdListNeverWidensIntoAFullTableRead() throws IOException {
        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".findTranslationsByEventIds")
                .getBoundSql(Map.of("eventIds", List.of()));

        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "SELECT id, event_id, language_code, title, description, poster_img "
                        + "FROM event_translations WHERE 1 = 0 "
                        + "ORDER BY event_id ASC, language_code ASC, id ASC");
        assertThat(boundSql.getParameterMappings()).isEmpty();
    }

    @Test
    void insertWritesOneLanguageRowForTheGivenEvent() throws IOException {
        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".insertTranslation")
                .getBoundSql(translation("en", "Summer event", "Summer event body",
                        "/uploads/events/posters/en-summer.png"));

        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "INSERT INTO event_translations "
                        + "(event_id, language_code, title, description, poster_img) "
                        + "VALUES (?, ?, ?, ?, ?)");
        assertThat(boundSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("eventId", "languageCode", "title", "description", "posterImg");
    }

    @Test
    void updateTouchesOnlyTheRequestedLanguageRow() throws IOException {
        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".updateTranslation")
                .getBoundSql(translation("ja", "夏のイベント", "夏のイベント本文",
                        "/uploads/events/posters/ja-summer.png"));

        // event_id 와 language_code 는 조건일 뿐 갱신 대상이 아니다.
        // 바꾸면 다른 이벤트나 다른 언어 줄을 덮어쓰게 된다.
        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "UPDATE event_translations "
                        + "SET title = ?, description = ?, poster_img = ? "
                        + "WHERE event_id = ? AND language_code = ?");
        assertThat(boundSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("title", "description", "posterImg", "eventId", "languageCode");
        assertThat(normalize(boundSql.getSql()))
                .doesNotContain("SET event_id")
                .doesNotContain("language_code = ?, ");
    }

    @Test
    void deleteRemovesOnlyTheRequestedLanguageRow() throws IOException {
        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".deleteTranslation")
                .getBoundSql(Map.of("eventId", 7L, "languageCode", "zh-CN"));

        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "DELETE FROM event_translations "
                        + "WHERE event_id = ? AND language_code = ?");
        assertThat(boundSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("eventId", "languageCode");
    }

    @Test
    void addingTranslationStatementsDoesNotChangeBaseEventStatements() throws IOException {
        Configuration configuration = mapperConfiguration();

        String slide = normalize(configuration
                .getMappedStatement(NAMESPACE + ".selectSlideEvents").getBoundSql(null).getSql());
        String all = normalize(configuration
                .getMappedStatement(NAMESPACE + ".selectAllEvents").getBoundSql(null).getSql());
        String detail = normalize(configuration
                .getMappedStatement(NAMESPACE + ".selectEventById").getBoundSql(7L).getSql());

        // 슬라이드는 노출 플래그와 기간 조건을 그대로 지킨다.
        assertThat(slide).isEqualTo(
                "SELECT id, title, description, event_img, event_type, is_slide, "
                        + "user_id, start_date, end_date, created_at FROM events "
                        + "WHERE is_slide = 1 AND start_date <= NOW() AND end_date >= NOW() "
                        + "ORDER BY id");
        assertThat(all).isEqualTo("SELECT * FROM events ORDER BY start_date DESC");
        assertThat(detail).isEqualTo("SELECT * FROM events WHERE id = ?");
        assertThat(slide + all + detail).doesNotContain("event_translations");
    }

    @Test
    void statusAndPagingQueriesKeepTheirExistingConditionsAndSort() throws IOException {
        Configuration configuration = mapperConfiguration();

        assertThat(pagedSql(configuration, "ongoing")).isEqualTo(
                "SELECT * FROM events WHERE start_date <= NOW() AND end_date >= NOW() "
                        + "ORDER BY start_date DESC LIMIT ? OFFSET ?");
        assertThat(pagedSql(configuration, "upcoming")).isEqualTo(
                "SELECT * FROM events WHERE start_date > NOW() "
                        + "ORDER BY start_date ASC LIMIT ? OFFSET ?");
        assertThat(pagedSql(configuration, "ended")).isEqualTo(
                "SELECT * FROM events WHERE end_date < NOW() "
                        + "ORDER BY end_date DESC LIMIT ? OFFSET ?");

        assertThat(countSql(configuration, "ongoing")).isEqualTo(
                "SELECT COUNT(*) FROM events "
                        + "WHERE start_date <= NOW() AND end_date >= NOW()");
        assertThat(countSql(configuration, "upcoming")).isEqualTo(
                "SELECT COUNT(*) FROM events WHERE start_date > NOW()");
        assertThat(countSql(configuration, "ended")).isEqualTo(
                "SELECT COUNT(*) FROM events WHERE end_date < NOW()");
    }

    @Test
    void translationTableMatchesTheLiveDatabaseContract() throws IOException {
        String translations = between(schema(),
                "CREATE TABLE `event_translations`",
                "/*!40101 SET character_set_client = @saved_cs_client */;");

        assertThat(translations)
                .contains("`id` bigint NOT NULL AUTO_INCREMENT")
                .contains("`event_id` bigint NOT NULL")
                .contains("`language_code` varchar(10) CHARACTER SET ascii "
                        + "COLLATE ascii_bin NOT NULL")
                .contains("`title` varchar(255)")
                .contains("`description` text")
                .contains("`poster_img` varchar(255)")
                // 한 이벤트에 같은 언어 줄이 둘 생기지 않게 막는 키.
                .contains("UNIQUE KEY `uk_event_translation` (`event_id`,`language_code`)")
                .contains("KEY `idx_event_translation_locale` (`language_code`,`event_id`)")
                .contains("CONSTRAINT `fk_event_translation` "
                        + "FOREIGN KEY (`event_id`) REFERENCES `events` (`id`) "
                        + "ON DELETE CASCADE");
        // 대표 이미지는 공용이므로 번역 테이블이 들고 있지 않다.
        assertThat(translations).doesNotContain("`event_img`");
    }

    private String pagedSql(Configuration configuration, String status) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("status", status);
        parameters.put("offset", 0L);
        parameters.put("size", 9);
        return normalize(configuration
                .getMappedStatement(NAMESPACE + ".selectEventsByStatusPaged")
                .getBoundSql(parameters).getSql());
    }

    private String countSql(Configuration configuration, String status) {
        return normalize(configuration
                .getMappedStatement(NAMESPACE + ".countEventsByStatus")
                .getBoundSql(Map.of("status", status)).getSql());
    }

    private EventTranslation translation(String languageCode, String title,
                                         String description, String posterImg) {
        EventTranslation translation = new EventTranslation();
        translation.setEventId(7L);
        translation.setLanguageCode(languageCode);
        translation.setTitle(title);
        translation.setDescription(description);
        translation.setPosterImg(posterImg);
        return translation;
    }

    private Configuration mapperConfiguration() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream input = getClass().getResourceAsStream("/mapper/EventMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, "mapper/EventMapper.xml",
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
