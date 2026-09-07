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

    /** 공지 번역은 언어 한 줄이 단위다. 언어 코드는 조건일 뿐 갱신 대상이 아니다. */
    @Test
    void translationStatementsTouchOnlyTheRequestedLanguageRow() throws IOException {
        String xml = mapper();
        String select = between(xml, "<select id=\"findTranslationsByNoticeId\"", "</select>");
        String insert = between(xml, "<insert id=\"insertTranslation\"", "</insert>");
        String update = between(xml, "<update id=\"updateTranslation\"", "</update>");
        String delete = between(xml, "<delete id=\"deleteTranslation\"", "</delete>");

        assertThat(select)
                .contains("SELECT id, notice_id, language_code, title, content")
                .contains("FROM notice_translations")
                .contains("WHERE notice_id = #{noticeId}")
                .contains("ORDER BY language_code ASC, id ASC");
        assertThat(insert)
                .contains("useGeneratedKeys=\"true\"")
                .contains("INSERT INTO notice_translations (notice_id, language_code, title, content)")
                .contains("VALUES (#{noticeId}, #{languageCode}, #{title}, #{content})");
        assertThat(update)
                .contains("UPDATE notice_translations")
                .contains("title = #{title}", "content = #{content}")
                .contains("WHERE notice_id = #{noticeId}", "AND language_code = #{languageCode}")
                .doesNotContain("language_code =\n", "notice_id =\n");
        assertThat(delete)
                .contains("DELETE FROM notice_translations")
                .contains("WHERE notice_id = #{noticeId}", "AND language_code = #{languageCode}");
    }

    /** 공개 목록은 공지마다 번역을 읽지 않고 한 번의 IN 조회로 모아 읽는다. */
    @Test
    void publicListTranslationsAreReadInOneQueryWithoutNPlusOne() throws IOException {
        String query = between(mapper(), "<select id=\"findTranslationsByNoticeIds\"", "</select>");

        assertThat(query)
                .contains("SELECT id, notice_id, language_code, title, content")
                .contains("FROM notice_translations")
                .contains("WHERE notice_id IN")
                .contains("<foreach collection=\"noticeIds\"")
                // 빈 목록이 전체 조회로 넓어지지 않게 막는다
                .contains("WHERE 1 = 0")
                .contains("ORDER BY notice_id ASC, language_code ASC, id ASC")
                .doesNotContain("${");
    }

    @Test
    void schemaReferenceMatchesTheNoticeTranslationTable() throws IOException {
        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
        String translations = between(schema, "CREATE TABLE `notice_translations`", ") ENGINE=InnoDB");

        assertThat(translations)
                .contains("`notice_id` bigint NOT NULL")
                .contains("`language_code` varchar(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL")
                .contains("`title` varchar(255)")
                .contains("`content` mediumtext")
                // 한 공지에 같은 언어 줄이 둘 생기지 않게 막는 키
                .contains("UNIQUE KEY `uk_notice_translation` (`notice_id`,`language_code`)")
                .contains("KEY `idx_notice_translation_locale` (`language_code`,`notice_id`)")
                .contains("CONSTRAINT `fk_notice_translation` FOREIGN KEY (`notice_id`) "
                        + "REFERENCES `notices` (`id`) ON DELETE CASCADE");
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
