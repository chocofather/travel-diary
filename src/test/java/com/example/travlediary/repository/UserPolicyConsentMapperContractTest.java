package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동의 이력 보관정책의 삭제 조건은 전부 SQL 이 갖고 있다.
 * 조건이 하나라도 빠지면 아직 지우면 안 되는 이력이 사라지므로 문장 자체를 고정한다.
 */
class UserPolicyConsentMapperContractTest {

    /**
     * 대상은 최종 탈퇴가 끝나고 3년이 지난 회원뿐이다.
     * ACTIVE/INACTIVE/RESTRICTED/SUSPENDED/WITHDRAWAL_PENDING 은 동의일이 아무리 오래돼도 아니다.
     */
    @Test
    void onlyUsersWhoseFinalPurgeCompletedThreeYearsAgoAreSelected() throws IOException {
        String select = statement(consentXml(), "select",
                "findConsentRetentionExpiredUserIds");
        String condition = statement(consentXml(), "sql", "ConsentRetentionExpiredUser");

        // tombstone 이 된 회원만. 다른 상태는 조건에서 걸러진다.
        assertThat(condition).contains("u.status = 'DEACTIVATED'");
        // 30일 유예 중 복구한 회원은 deleted_at 이 NULL 로 돌아가 제외된다.
        assertThat(condition).contains("u.deleted_at IS NOT NULL");
        // 경계는 이하라서 정확히 3년이 된 회원부터 대상이다.
        assertThat(condition).contains("u.deleted_at &lt;= #{retentionCutoff}");

        // 기준은 반드시 deleted_at 이다. 유예 시각으로 계산하면 30일 일찍 지운다.
        assertThat(condition)
                .doesNotContain("withdrawal_requested_at")
                .doesNotContain("purge_scheduled_at");

        // 대량 데이터에 대비해 한 번에 batch 크기만큼만 읽는다.
        assertThat(select).contains("LIMIT #{limit}");
        // 이미 정리된 회원을 매 주기 다시 집지 않는다.
        assertThat(select).contains("FROM user_policy_consents c");
    }

    /**
     * 삭제는 회원 단위 전체다. 오래된 개별 동의행만 골라 지우지 않고,
     * 다른 회원의 이력에는 손대지 않는다.
     */
    @Test
    void theDeleteRemovesEveryConsentOfOneUserAndNoOneElses() throws IOException {
        String delete = statement(consentXml(), "delete",
                "deleteConsentsOfRetentionExpiredUser");

        assertThat(delete)
                .contains("DELETE FROM user_policy_consents")
                .contains("WHERE user_id = #{userId}");

        // 동의행 자체의 나이로 지우지 않는다. 회원의 보관기한만 본다.
        assertThat(delete).doesNotContain("c.created_at").doesNotContain("created_at &lt;=");

        // 목록을 읽은 뒤 조건이 깨졌으면 한 행도 지우지 않는다.
        assertThat(delete)
                .contains("EXISTS")
                .contains("FROM users u")
                .contains("<include refid=\"ConsentRetentionExpiredUser\"/>");
    }

    /** 정책 문서 자체와 회원 tombstone 은 이 mapper 가 건드리지 않는다. */
    @Test
    void neitherPolicyDocumentsNorTheUserTombstoneAreTouched() throws IOException {
        String consentXml = consentXml();

        assertThat(consentXml)
                .doesNotContain("DELETE FROM policy_versions")
                .doesNotContain("DELETE FROM policy_translations")
                .doesNotContain("DELETE FROM users")
                .doesNotContain("UPDATE users")
                // append-only 기록이다. 가입 시 INSERT 와 보관기간 만료 DELETE 외에는 손대지 않는다.
                .doesNotContain("UPDATE user_policy_consents")
                .doesNotContain("<update")
                .doesNotContain("ON DUPLICATE KEY UPDATE")
                .doesNotContain("REPLACE INTO");
    }

    /**
     * 최종 account purge 는 동의 이력을 지우지 않는다. 보관기간은 그때부터 시작이다.
     * users tombstone 행이 남으므로 FK 의 ON DELETE CASCADE 도 일어나지 않는다.
     */
    @Test
    void theFinalAccountPurgeLeavesTheConsentHistoryInPlace() throws IOException {
        assertThat(resource("mapper/AccountPurgeMapper.xml"))
                .doesNotContain("user_policy_consents");
        assertThat(resource("mapper/UserMapper.xml"))
                .doesNotContain("DELETE FROM user_policy_consents");
    }

    private String consentXml() throws IOException {
        return resource("mapper/UserPolicyConsentMapper.xml");
    }

    private String resource(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/resources").resolve(relativePath),
                StandardCharsets.UTF_8);
    }

    private String statement(String xml, String tag, String id) {
        String open = "<" + tag + " id=\"" + id + "\"";
        int start = xml.indexOf(open);
        int end = xml.indexOf("</" + tag + ">", start);
        assertThat(start).as("%s statement %s", tag, id).isNotNegative();
        assertThat(end).as("%s statement %s closing tag", tag, id).isGreaterThan(start);
        return xml.substring(start, end);
    }
}
