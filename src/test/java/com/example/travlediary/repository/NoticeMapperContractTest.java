package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NoticeMapperContractTest {

    @Test
    void publicListUsesPinnedLatestStableOrderAndPagination() throws IOException {
        String query = between(mapper(), "<select id=\"findPublicList\"", "</select>");

        assertThat(query)
                .contains("FROM notices")
                .contains("ORDER BY is_pinned DESC, created_at DESC, id DESC")
                .contains("LIMIT #{limit}")
                .contains("OFFSET #{offset}")
                .doesNotContain("content", "user_id");
    }

    @Test
    void publicDetailIncrementIsAtomicAndPreservesUpdatedAt() throws IOException {
        String xml = mapper();
        String increment = between(xml, "<update id=\"incrementPublicViews\"", "</update>");
        String detail = between(xml, "<select id=\"findPublicDetailById\"", "</select>");

        assertThat(increment)
                .contains("views = views + 1")
                .contains("updated_at = updated_at")
                .contains("WHERE id = #{id}")
                .doesNotContain("FOR UPDATE");
        assertThat(detail)
                .contains("SELECT id, title, content, views, created_at")
                .doesNotContain("user_id", "updated_at");
    }

    @Test
    void adminWritesUseServerUserAndPreserveOriginalAuthorOnUpdate() throws IOException {
        String xml = mapper();
        String insert = between(xml, "<insert id=\"insertNotice\"", "</insert>");
        String update = between(xml, "<update id=\"updateNotice\"", "</update>");
        String delete = between(xml, "<delete id=\"deleteNotice\"", "</delete>");

        assertThat(insert)
                .contains("useGeneratedKeys=\"true\"")
                .contains("title, content, is_pinned, views, user_id")
                .contains("#{title}, #{content}, #{pinned}, 0, #{userId}");
        assertThat(update)
                .contains("title = #{title}", "content = #{content}", "is_pinned = #{pinned}")
                .doesNotContain("user_id =", "views =");
        assertThat(delete).contains("DELETE FROM notices", "WHERE id = #{id}");
    }

    @Test
    void schemaReferenceMatchesCompletedNoticeTable() throws IOException {
        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
        String notices = between(schema, "CREATE TABLE `notices`", ") ENGINE=InnoDB");

        assertThat(notices)
                .contains("`id` bigint NOT NULL AUTO_INCREMENT")
                .contains("`content` mediumtext NOT NULL")
                .contains("`is_pinned` tinyint NOT NULL DEFAULT '0'")
                .contains("`views` int NOT NULL DEFAULT '0'")
                .contains("KEY `idx_notices_public_order` (`is_pinned`,`created_at`,`id`)")
                .contains("FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT");
    }

    private String mapper() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/mapper/NoticeMapper.xml")) {
            assertThat(input).isNotNull();
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
