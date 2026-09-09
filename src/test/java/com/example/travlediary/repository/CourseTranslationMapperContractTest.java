package com.example.travlediary.repository;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CourseTranslationMapperContractTest {

    @Test
    void translationReaderRequiresAnActiveUnmoderatedCourseAndReadsNoDestinationData() {
        Configuration configuration = configuration();
        String visible = sql(configuration, "findVisibleTranslationSource", Map.of("courseId", 9L));
        String locked = sql(configuration, "findVisibleTranslationSourceForUpdate", Map.of("courseId", 9L));

        assertThat(visible)
                .contains("FROM courses c")
                .contains("c.deleted = 0")
                .contains("cm.target_type = 'COURSE'")
                .contains("cm.status = 'ACTIVE'")
                .contains("NOT EXISTS")
                .doesNotContain("course_destinations", "destinations", "destination_translations");
        assertThat(locked).endsWith("FOR UPDATE");
    }

    @Test
    void writesAndBackfillKeepTitleAndContentLanguagesIndependent() {
        Configuration configuration = configuration();
        Timestamp version = Timestamp.from(Instant.parse("2026-09-09T01:00:00Z"));

        assertThat(sql(configuration, "insertCourse", Map.of()))
                .contains("title, title_source_language")
                .contains("content, content_source_language");
        assertThat(sql(configuration, "updateCourse", Map.of(
                "courseId", 9L, "userId", 7L, "countryId", 3L,
                "title", "Title", "content", "<p>Body</p>",
                "titleSourceLanguage", "en", "contentSourceLanguage", "en")))
                .contains("title_source_language = ?")
                .contains("content_source_language = ?");
        assertThat(sql(configuration, "findUndeterminedCourseLanguagesAfter", Map.of(
                "afterId", 0L, "limit", 500)))
                .contains("title_source_language = 'und'")
                .contains("content_source_language = 'und'")
                .contains("id > ?");
        assertThat(sql(configuration, "updateCourseTitleLanguageIfUndetermined", Map.of(
                "courseId", 9L, "sourceLanguage", "en", "title", "Title", "updatedAt", version)))
                .contains("updated_at <=> ?")
                .contains("CAST(title AS BINARY) = CAST(? AS BINARY)");
        assertThat(sql(configuration, "updateCourseContentLanguageIfUndetermined", Map.of(
                "courseId", 9L, "sourceLanguage", "en", "content", "<p>Body</p>", "updatedAt", version)))
                .contains("updated_at <=> ?")
                .contains("CAST(content AS BINARY) = CAST(? AS BINARY)");
    }

    private Configuration configuration() {
        Configuration configuration = new Configuration();
        try (InputStream input = getClass().getResourceAsStream("/mapper/CourseMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, "mapper/CourseMapper.xml",
                    configuration.getSqlFragments()).parse();
            return configuration;
        } catch (Exception e) {
            throw new AssertionError("코스 mapper XML을 파싱할 수 없습니다.", e);
        }
    }

    private String sql(Configuration configuration, String statement, Object parameters) {
        BoundSql boundSql = configuration.getMappedStatement(
                        "com.example.travlediary.repository.course.CourseMapper." + statement)
                .getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }
}
