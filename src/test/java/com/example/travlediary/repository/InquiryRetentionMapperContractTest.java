package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 문의 보존기간(3년) 만료 정리 쿼리의 계약.
 *
 * <p>진행 중인 문의를 지우지 않는 것과, 기준 시점이 "접수 시각"이 아니라 "처리 종료 시각"이라는 점이
 * 이 정책의 핵심이라 SQL 수준에서 못 박아 둔다.
 */
class InquiryRetentionMapperContractTest {

    /** 종료된 상태만 대상이다. 접수·처리 중인 문의는 아무리 오래돼도 지우지 않는다. */
    @Test
    void onlyFinishedInquiriesAreEverSelectedForDeletion() throws IOException {
        String condition = between(mapper(),
                "<sql id=\"ExpiredInquiryCondition\">", "</sql>");

        assertThat(condition)
                .contains("i.status IN ('ANSWERED', 'CANCELLED')")
                .doesNotContain("PENDING")
                .doesNotContain("IN_PROGRESS");
    }

    /** 보존기간 기준은 답변 등록 시각이고, 답변이 없는 취소 문의만 마지막 변경 시각으로 대신한다. */
    @Test
    void theRetentionBaseIsTheAnswerTimeNotTheCreationTime() throws IOException {
        String base = between(mapper(), "<sql id=\"RetentionBase\">", "</sql>");

        assertThat(base)
                .contains("SELECT ia.created_at FROM inquiry_answers ia WHERE ia.inquiry_id = i.id")
                .contains("i.updated_at");
        // 접수 시각(i.created_at)은 기준이 아니다.
        assertThat(base).doesNotContain("i.created_at");
    }

    /** 만료 시각은 애플리케이션이 계산해 넘긴다. SQL 이 임의로 현재 시각을 쓰지 않는다. */
    @Test
    void theCutoffComesFromTheApplicationSoTheBoundaryStaysTestable() throws IOException {
        String condition = between(mapper(),
                "<sql id=\"ExpiredInquiryCondition\">", "</sql>");

        assertThat(condition)
                .contains("&lt;= #{retentionCutoff}")
                .doesNotContain("NOW()", "CURRENT_TIMESTAMP", "INTERVAL");
    }

    /** 한 번에 전부 지우지 않고 batch 로 끊어 읽는다. */
    @Test
    void theExpiredListIsPagedAndStablyOrdered() throws IOException {
        String select = between(mapper(),
                "<select id=\"findExpiredInquiryIds\"", "</select>");

        assertThat(select)
                .contains("<include refid=\"ExpiredInquiryCondition\"/>")
                .contains("ORDER BY i.id")
                .contains("LIMIT #{limit}");
    }

    /** 삭제 직전에 행을 잠그고 같은 조건을 다시 확인한다(회원 파기 배치와 같은 방식). */
    @Test
    void theDeleteIsGuardedByALockedRecheck() throws IOException {
        String xml = mapper();
        String locked = between(xml,
                "<select id=\"findExpiredInquiryByIdForUpdate\"", "</select>");
        String delete = between(xml, "<delete id=\"deleteInquiryById\"", "</delete>");

        assertThat(locked)
                .contains("WHERE i.id = #{id}")
                .contains("<include refid=\"ExpiredInquiryCondition\"/>")
                .contains("FOR UPDATE");
        assertThat(delete).contains("DELETE FROM inquiries").contains("WHERE id = #{id}");
    }

    /** 답변은 스키마의 CASCADE 로 함께 지워진다. 그 전제를 스키마에서 확인해 둔다. */
    @Test
    void answersAreRemovedByTheExistingCascade() throws Exception {
        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
        String answers = between(schema, "CREATE TABLE `inquiry_answers`", ") ENGINE=InnoDB");

        assertThat(answers).contains(
                "FOREIGN KEY (`inquiry_id`) REFERENCES `inquiries` (`id`) ON DELETE CASCADE");
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
