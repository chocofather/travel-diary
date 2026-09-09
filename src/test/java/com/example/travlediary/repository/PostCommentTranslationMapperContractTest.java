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

class PostCommentTranslationMapperContractTest {

    @Test
    void translationSourceQueriesEnforceThePostCommentPublicPolicy() {
        Configuration configuration = mapperConfiguration();
        String select = sql(configuration, "findVisibleTranslationSource", Map.of("id", 8L));

        assertThat(select)
                .contains("FROM post_comments pc")
                .contains("JOIN user_posts p")
                .contains("p.deleted = 0")
                .contains("pc.deleted = 0")
                .contains("cm.target_type = 'POST_COMMENT'")
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
                .contains("content, source_language, post_id")
                .contains("VALUES ( ?, ?, ?, ?, ?, ?, NOW(), NOW(), 0 )");

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
        try (InputStream input = getClass().getResourceAsStream("/mapper/PostCommentMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, "mapper/PostCommentMapper.xml",
                    configuration.getSqlFragments()).parse();
            return configuration;
        } catch (Exception e) {
            throw new AssertionError("게시글 댓글 mapper XML을 파싱할 수 없습니다.", e);
        }
    }

    private String sql(Configuration configuration, String statement, Object parameters) {
        return configuration.getMappedStatement(
                        "com.example.travlediary.repository.post.PostCommentMapper." + statement)
                .getBoundSql(parameters)
                .getSql().replaceAll("\\s+", " ").trim();
    }
}
