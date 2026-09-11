package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 탈퇴 유예 계정 복구 SQL 계약.
 * 유예기간 판단은 언제나 users.purge_scheduled_at 이고,
 * 복구 토큰은 해시만 저장하며 한 번만 쓸 수 있어야 한다.
 */
class AccountRecoveryMapperContractTest {

    @Test
    void recoveryLookupRequiresTheWithdrawalGracePeriodToStillBeOpen() throws IOException {
        for (String id : new String[]{
                "findRecoverableWithdrawalById", "findRecoverableWithdrawalByIdForUpdate"}) {
            assertThat(statement(userXml(), "select", id))
                    .as(id)
                    .contains("status = 'WITHDRAWAL_PENDING'", "deleted_at IS NULL",
                            "purge_scheduled_at IS NOT NULL",
                            "purge_scheduled_at &gt; #{currentTime}")
                    .doesNotContain("user_password", "reset_token", "verification_token");
        }
    }

    @Test
    void theConfirmationPathLocksTheUserRowBeforeRestoringIt() throws IOException {
        assertThat(statement(userXml(), "select", "findRecoverableWithdrawalByIdForUpdate"))
                .contains("WHERE id = #{id}", "FOR UPDATE");
    }

    /** 링크 확인(GET)용 조회는 아무것도 잠그지 않는다. */
    @Test
    void theLinkCheckReadsWithoutLockingAnything() throws IOException {
        assertThat(statement(userXml(), "select", "findRecoverableWithdrawalById"))
                .contains("WHERE id = #{id}")
                .doesNotContain("FOR UPDATE");
    }

    /**
     * 안내 화면은 유예기간이 끝난 회원도 봐야 하므로 날짜를 조건에 넣지 않는다.
     * 넣으면 격리 필터와 안내 화면 사이에 리다이렉트 루프가 생긴다.
     */
    @Test
    void theWithdrawalNoticeLookupIsNotGatedOnTheGracePeriod() throws IOException {
        assertThat(statement(userXml(), "select", "findWithdrawalPendingById"))
                .contains("withdrawal_requested_at", "purge_scheduled_at",
                        "status = 'WITHDRAWAL_PENDING'", "deleted_at IS NULL")
                .doesNotContain("purge_scheduled_at &gt;", "FOR UPDATE",
                        "user_password", "reset_token");
    }

    /**
     * 복구는 상태와 유예 일정만 되돌린다.
     * 이메일/닉네임/비밀번호/프로필을 만지면 30일간 보존한 데이터가 깨진다.
     */
    @Test
    void restoreOnlyResetsTheStatusAndTheGraceSchedule() throws IOException {
        String update = statement(userXml(), "update", "restoreWithdrawalPendingAccount");

        assertThat(update)
                .contains("status = #{status}",
                        "withdrawal_requested_at = NULL",
                        "purge_scheduled_at = NULL",
                        "deleted_at = NULL",
                        "updated_at = NOW()",
                        "WHERE id = #{id}",
                        "status = 'WITHDRAWAL_PENDING'",
                        "purge_scheduled_at &gt; #{currentTime}")
                .doesNotContain("user_email =", "nickname =", "username =",
                        "full_name =", "user_phone =", "user_birth =",
                        "user_password =", "profile_image =", "user_role =",
                        "verification_token", "reset_token", "DELETE FROM users");
    }

    @Test
    void recoveryTokensAreStoredAsHashesOnly() throws IOException {
        String insert = statement(recoveryXml(), "insert", "insertToken");

        assertThat(insert)
                .contains("INSERT INTO account_recovery_tokens", "token_hash", "#{tokenHash}")
                .doesNotContain("#{rawToken}", "#{token}");
    }

    @Test
    void aRecoveryTokenIsUsableOnlyWhileUnusedAndUnexpired() throws IOException {
        assertThat(statement(recoveryXml(), "select", "findUsableByTokenHash"))
                .contains("SELECT id, user_id, expires_at",
                        "token_hash = #{tokenHash}", "used_at IS NULL",
                        "expires_at &gt; #{currentTime}")
                // 해시는 조회 조건일 뿐 결과로 끌고 나오지 않는다.
                .doesNotContain("token_hash,", ", token_hash");
    }

    /** used_at IS NULL 조건이 일회성을 보장한다. */
    @Test
    void burningATokenIsGuardedSoTheSameLinkCannotBeUsedTwice() throws IOException {
        assertThat(statement(recoveryXml(), "update", "markUsed"))
                .contains("used_at = #{usedAt}", "WHERE id = #{id}", "used_at IS NULL");
    }

    /**
     * 재발급은 이력을 지우지 않고 기존 미사용 토큰만 닫는다.
     *
     * <p>행을 지우는 문장은 최종 탈퇴 파기(deleteAllByUserId) 하나뿐이다. 그쪽은 계정이
     * 사라지는 경로라 이력을 남길 이유가 없다. 재발급 경로가 그 문장을 쓰지 않는지만 본다.
     */
    @Test
    void reissuingClosesOldTokensWithoutDeletingTheHistory() throws IOException {
        assertThat(statement(recoveryXml(), "update", "invalidateUnusedTokens"))
                .contains("used_at = #{invalidatedAt}", "user_id = #{userId}",
                        "used_at IS NULL")
                .doesNotContain("DELETE");
        assertThat(statement(recoveryXml(), "update", "markUsed")).doesNotContain("DELETE");
    }

    private String userXml() throws IOException {
        return resource("mapper/UserMapper.xml");
    }

    private String recoveryXml() throws IOException {
        return resource("mapper/AccountRecoveryTokenMapper.xml");
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
