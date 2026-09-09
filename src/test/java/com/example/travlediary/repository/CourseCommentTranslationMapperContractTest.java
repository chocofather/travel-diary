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

class CourseCommentTranslationMapperContractTest {

    @Test
    void translationSourceQueriesEnforceTheCourseCommentPublicPolicy() {
        Configuration configuration = mapperConfiguration();
        String select = sql(configuration, "findVisibleTranslationSource", Map.of("id", 8L));

        assertThat(select)
                .contains("FROM course_comments cc")
                .contains("JOIN courses c")
                .contains("c.deleted = 0")
                .contains("cc.deleted = 0")
                .contains("cm.target_type = 'COURSE_COMMENT'")
                .contains("cm.status = 'ACTIVE'")
                .contains("NOT EXISTS");

        String locked = sql(configuration, "findVisibleTranslationSourceForUpdate", Map.of("id", 8L));
        assertThat(locked).endsWith("FOR UPDATE");
    }

    @Test
    void insertUpdateAndBackfillPersistDetectedLanguageSafely() {
        Configuration configuration = mapperConfiguration();
        Timestamp version = Timestamp.from(Instant.parse("2026-09-09T01:00:00Z"));

        assertThat(sql(configuration, "insert", Map.of()))
                .contains("content, source_language, deleted")
                .contains("VALUES ( ?, ?, ?, ?, 0");

        assertThat(sql(configuration, "updateContent", Map.of(
                "commentId", 8L, "userId", 7L,
                "content", "수정", "sourceLanguage", "ko")))
                .contains("source_language = ?");

        assertThat(sql(configuration, "findUndeterminedLanguagesAfter", Map.of(
                "afterId", 0L, "limit", 500)))
                .contains("source_language = 'und'")
                .contains("id > ?")
                .contains("ORDER BY id")
                .doesNotContain("deleted = 0");

        assertThat(sql(configuration, "updateDetectedLanguageIfUndetermined", Map.of(
                "id", 8L, "sourceLanguage", "ko", "content", "맛집", "updatedAt", version)))
                .contains("source_language = 'und'")
                .contains("updated_at <=> ?")
                .contains("CAST(content AS BINARY) = CAST(? AS BINARY)");
    }

    private Configuration mapperConfiguration() {
        Configuration configuration = new Configuration();
        try (InputStream input = getClass().getResourceAsStream("/mapper/CourseCommentMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, "mapper/CourseCommentMapper.xml",
                    configuration.getSqlFragments()).parse();
            return configuration;
        } catch (Exception e) {
            throw new AssertionError("코스 댓글 mapper XML을 파싱할 수 없습니다.", e);
        }
    }

    private String sql(Configuration configuration, String statement, Object parameters) {
        BoundSql boundSql = configuration.getMappedStatement(
                        "com.example.travlediary.repository.course.CourseCommentMapper." + statement)
                .getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }
}
