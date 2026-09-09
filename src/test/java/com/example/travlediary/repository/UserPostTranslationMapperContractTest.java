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

class UserPostTranslationMapperContractTest {

    @Test
    void translationQueriesRequireAnActivePublicUserPostWithoutModeration() {
        Configuration configuration = configuration();
        String visible = sql(configuration, "findVisibleTranslationSource", Map.of("postId", 9L));
        String locked = sql(configuration, "findVisibleTranslationSourceForUpdate", Map.of("postId", 9L));

        assertThat(visible)
                .contains("FROM user_posts p")
                .contains("p.deleted = 0")
                .contains("p.post_type IN ('QUESTION', 'TIP')")
                .contains("cm.target_type = 'POST'")
                .contains("cm.status = 'ACTIVE'")
                .contains("NOT EXISTS");
        assertThat(locked).endsWith("FOR UPDATE");
    }

    @Test
    void writeUpdateAndBackfillKeepBothLanguageColumnsIndependent() {
        Configuration configuration = configuration();
        Timestamp version = Timestamp.from(Instant.parse("2026-09-09T01:00:00Z"));

        assertThat(sql(configuration, "insertPost", Map.of()))
                .contains("title, title_source_language, content, content_source_language")
                .contains("?, ?, ?, ?");
        assertThat(sql(configuration, "updatePost", Map.of(
                "postId", 9L, "userId", 7L, "title", "제목", "postType", "TIP",
                "content", "<p>본문</p>", "titleSourceLanguage", "ko",
                "contentSourceLanguage", "ko")))
                .contains("title_source_language = ?")
                .contains("content_source_language = ?");
        assertThat(sql(configuration, "findUndeterminedPostLanguagesAfter", Map.of(
                "afterId", 0L, "limit", 500)))
                .contains("title_source_language = 'und'")
                .contains("content_source_language = 'und'")
                .contains("id > ?")
                .doesNotContain("deleted = 0");
        assertThat(sql(configuration, "updateTitleLanguageIfUndetermined", Map.of(
                "id", 9L, "sourceLanguage", "en", "title", "Title", "updatedAt", version)))
                .contains("title_source_language = 'und'")
                .contains("updated_at <=> ?")
                .contains("CAST(title AS BINARY) = CAST(? AS BINARY)");
        assertThat(sql(configuration, "updateContentLanguageIfUndetermined", Map.of(
                "id", 9L, "sourceLanguage", "en", "content", "<p>Body</p>", "updatedAt", version)))
                .contains("content_source_language = 'und'")
                .contains("updated_at <=> ?")
                .contains("CAST(content AS BINARY) = CAST(? AS BINARY)");
    }

    private Configuration configuration() {
        Configuration configuration = new Configuration();
        try (InputStream input = getClass().getResourceAsStream("/mapper/PostMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, "mapper/PostMapper.xml",
                    configuration.getSqlFragments()).parse();
            return configuration;
        } catch (Exception e) {
            throw new AssertionError("게시글 mapper XML을 파싱할 수 없습니다.", e);
        }
    }

    private String sql(Configuration configuration, String statement, Object parameters) {
        BoundSql boundSql = configuration.getMappedStatement(
                        "com.example.travlediary.repository.post.PostMapper." + statement)
                .getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
    }
}
