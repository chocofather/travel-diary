package com.example.travlediary.repository;

import com.example.travlediary.model.Inquiry;
import com.example.travlediary.model.InquiryAnswer;
import com.example.travlediary.model.InquiryStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InquiryMapperContractTest {

    @Test
    void myListAndCountEnforceOwnerAndStableLatestPagination() throws IOException {
        String xml = mapper();
        String count = between(xml, "<select id=\"countMyInquiries\"", "</select>");
        String list = between(xml, "<select id=\"findMyInquiries\"", "</select>");

        assertThat(count).contains("WHERE user_id = #{userId}");
        assertThat(list)
                .contains("WHERE user_id = #{userId}")
                .contains("ORDER BY created_at DESC, id DESC")
                .contains("LIMIT #{limit}", "OFFSET #{offset}");
    }

    @Test
    void userDetailAndPendingDeleteEnforceOwnerInSql() throws IOException {
        String xml = mapper();
        String detail = between(xml, "<select id=\"findMyInquiryById\"", "</select>");
        String editable = between(xml, "<select id=\"findEditableMyInquiry\"", "</select>");
        String update = between(xml, "<update id=\"updatePendingMyInquiry\"", "</update>");
        String delete = between(xml, "<delete id=\"deletePendingMyInquiry\"", "</delete>");

        assertThat(detail)
                .contains("WHERE i.id = #{id}")
                .contains("AND i.user_id = #{userId}")
                .contains("LEFT JOIN inquiry_answers ia ON ia.inquiry_id = i.id");
        assertThat(editable)
                .contains("WHERE id = #{id}")
                .contains("AND user_id = #{userId}")
                .contains("AND status = 'PENDING'");
        assertThat(update)
                .contains("SET inquiry_type = #{inquiryType}")
                .contains("subject = #{subject}")
                .contains("content = #{content}")
                .contains("WHERE id = #{id}")
                .contains("AND user_id = #{userId}")
                .contains("AND status = 'PENDING'")
                .doesNotContain("SET user_id", "status = #{status}", "created_at =", "updated_at =");
        assertThat(delete)
                .contains("WHERE id = #{id}")
                .contains("AND user_id = #{userId}")
                .contains("AND status = 'PENDING'");
    }

    @Test
    void adminListJoinsMinimalUserIdentityAndBindsWhitelistedStatus() throws IOException {
        String xml = mapper();
        String count = between(xml, "<select id=\"countAdminInquiries\"", "</select>");
        String list = between(xml, "<select id=\"findAdminInquiries\"", "</select>");

        assertThat(count).contains("i.status = #{status}");
        assertThat(list)
                .contains("JOIN users u ON u.id = i.user_id")
                .contains("COALESCE(NULLIF(u.nickname, ''), u.username) AS user_display_name")
                .contains("i.status = #{status}")
                .contains("ORDER BY i.created_at DESC, i.id DESC")
                .contains("LIMIT #{limit}", "OFFSET #{offset}");
        assertThat(xml).doesNotContain("${");
    }

    @Test
    void answerWriteUsesInquiryLockUniqueTargetAndPreservesOriginalAuthor() throws IOException {
        String xml = mapper();
        String lock = between(xml, "<select id=\"findByIdForUpdate\"", "</select>");
        String insert = between(xml, "<insert id=\"insertAnswer\"", "</insert>");
        String update = between(xml, "<update id=\"updateAnswer\"", "</update>");
        String status = between(xml, "<update id=\"updateInquiryStatus\"", "</update>");

        assertThat(lock).contains("WHERE id = #{id}", "FOR UPDATE");
        assertThat(insert)
                .contains("content, inquiry_id, user_id")
                .contains("#{content}", "#{inquiryId}", "#{userId}");
        assertThat(update)
                .contains("SET content = #{content}")
                .contains("AND inquiry_id = #{inquiryId}")
                .doesNotContain("user_id =", "created_at =");
        assertThat(status).contains("SET status = #{status}", "WHERE id = #{id}");
    }

    @Test
    void modelsMatchPreparedDatabaseAndSchemaKeepsOneAnswerPerInquiry() throws Exception {
        assertThat(Inquiry.class.getDeclaredField("updatedAt").getType())
                .isEqualTo(java.sql.Timestamp.class);
        assertThat(Inquiry.class.getDeclaredField("inquiryType").getType().getSimpleName())
                .isEqualTo("InquiryType");
        assertThat(InquiryAnswer.class.getDeclaredField("updatedAt").getType())
                .isEqualTo(java.sql.Timestamp.class);
        assertThat(InquiryStatus.values())
                .contains(InquiryStatus.PENDING, InquiryStatus.IN_PROGRESS,
                        InquiryStatus.ANSWERED, InquiryStatus.CANCELLED);

        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
        String answers = between(schema, "CREATE TABLE `inquiry_answers`", ") ENGINE=InnoDB");
        assertThat(answers)
                .contains("UNIQUE KEY `uq_inquiry_answers_inquiry_id` (`inquiry_id`)")
                .contains("FOREIGN KEY (`inquiry_id`) REFERENCES `inquiries` (`id`) ON DELETE CASCADE");
    }

    private String mapper() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/mapper/InquiryMapper.xml")) {
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
