package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 제재 이력과 재가입 방지 기록의 보관조건은 전부 SQL 이 갖고 있다.
 * 조건이 하나라도 빠지면 적용중인 제재나 아직 보관기간이 남은 기록이 사라지므로 문장을 고정한다.
 */
class SanctionRetentionMapperContractTest {

    /**
     * 1) 유효한 임시제재 → 삭제 대상이 아니다. 적용중 제재를 지우는 갈래는 영구제재 하나뿐이고,
     * 그마저 회원이 최종 탈퇴한 경우로 한정된다.
     */
    @Test
    void anActiveTemporarySanctionIsNeverSelectedForDeletion() throws IOException {
        String condition = statement(sanctionXml(), "sql", "SanctionRetentionExpired");

        // 'ACTIVE' 는 오직 영구제재 갈래에서만, 그것도 type 조건과 함께 등장한다.
        assertThat(condition.split("'ACTIVE'", -1).length - 1)
                .as("적용중 제재를 대상으로 삼는 조건은 하나뿐이다")
                .isEqualTo(1);
        assertThat(activeSanctionBranch())
                .contains("s.type = 'PERMANENT'")
                .contains("s.status = 'ACTIVE'");
    }

    /**
     * 3) 종료 후 3년 미만은 유지, 4) 정확히 3년이면 삭제 가능.
     * 기준은 실제 종료 시각(released_at)이지 예정 종료일(expires_at)이 아니다.
     */
    @Test
    void theEndedSanctionRetentionCountsFromTheActualReleaseTime() throws IOException {
        String endedBranch = endedSanctionBranch();

        assertThat(endedBranch)
                .contains("s.status IN ('EXPIRED', 'LIFTED')")
                .contains("s.released_at IS NOT NULL")
                .contains("s.released_at &lt;= #{retentionCutoff}");
        // expires_at 은 TEMPORARY 의 예정일일 뿐이고 PERMANENT 는 NULL 이다.
        assertThat(statement(sanctionXml(), "sql", "SanctionRetentionExpired"))
                .doesNotContain("expires_at");
        // 이미 종료된 제재의 수명은 회원 탈퇴일과 무관하다. 탈퇴일부터 다시 세지 않는다.
        assertThat(endedBranch).doesNotContain("deleted_at");
    }

    /**
     * 2) 계정이 살아 있는 회원의 적용중 영구제재 → 기간 제한 없이 유지.
     * 6) WITHDRAWAL_PENDING 은 최종 탈퇴가 아니라 대상이 아니다.
     * 최종 탈퇴한 회원(DEACTIVATED + deleted_at)만 이 갈래에 들어온다.
     */
    @Test
    void anActivePermanentSanctionOnlyExpiresAfterTheMemberIsFullyWithdrawn() throws IOException {
        assertThat(activeSanctionBranch())
                .contains("u.id = s.user_id")
                .contains("u.status = 'DEACTIVATED'")
                .contains("u.deleted_at IS NOT NULL");
        // DEACTIVATED 외의 상태를 여는 우회가 없는지 본다.
        assertThat(activeSanctionBranch())
                .doesNotContain("WITHDRAWAL_PENDING")
                .doesNotContain("u.status IN")
                .doesNotContain("u.status !=")
                .doesNotContain("purge_scheduled_at")
                .doesNotContain("withdrawal_requested_at");
    }

    /**
     * 7) 최종 탈퇴 3년 미만은 유지, 8) 정확히 3년이면 삭제 가능.
     * 이 갈래의 기준은 제재 시각이 아니라 users.deleted_at 이다.
     */
    @Test
    void theActivePermanentSanctionRetentionCountsFromTheFinalWithdrawal() throws IOException {
        assertThat(activeSanctionBranch())
                .contains("u.deleted_at &lt;= #{retentionCutoff}")
                .doesNotContain("released_at")
                .doesNotContain("starts_at");
    }

    /** 삭제 직전에 보관조건을 다시 확인한다. SELECT 와 DELETE 가 같은 조건을 공유한다. */
    @Test
    void theSanctionDeleteRepeatsTheRetentionConditionAndTargetsOneRow() throws IOException {
        String delete = statement(sanctionXml(), "delete", "deleteRetentionExpiredSanction");

        assertThat(delete)
                .contains("DELETE s")
                .contains("FROM user_sanctions s")
                // 9) 다른 사용자/다른 제재에는 영향이 없다.
                .contains("s.id = #{id}")
                .contains("<include refid=\"SanctionRetentionExpired\"/>");
    }

    /**
     * 7) 최종 탈퇴 후 3년 미만은 유지, 8) 정확히 3년이면 삭제.
     * 6) WITHDRAWAL_PENDING 은 최종 탈퇴가 아니라 대상이 아니다.
     */
    @Test
    void theReSignupBlockRetentionCountsFromTheFinalPurge() throws IOException {
        String condition = statement(blockedXml(), "sql", "ReSignupBlockRetentionExpiredUser");

        assertThat(condition)
                .contains("u.status = 'DEACTIVATED'")
                .contains("u.deleted_at IS NOT NULL")
                .contains("u.deleted_at &lt;= #{retentionCutoff}");
        // 재가입 차단의 기준일은 탈퇴일이다. 제재 종료일이나 유예 시각이 아니다.
        assertThat(condition)
                .doesNotContain("released_at")
                .doesNotContain("withdrawal_requested_at")
                .doesNotContain("purge_scheduled_at");
    }

    /** 9) 대상 회원의 차단 기록만 지운다. 삭제 직전에 조건을 다시 확인한다. */
    @Test
    void theBlockedEmailDeleteIsScopedToOneUserAndRechecksRetention() throws IOException {
        String delete = statement(blockedXml(),
                "delete", "deleteReSignupBlocksOfRetentionExpiredUser");

        assertThat(delete)
                .contains("DELETE FROM blocked_emails")
                .contains("WHERE user_id = #{userId}")
                .contains("EXISTS")
                .contains("FROM users u")
                .contains("<include refid=\"ReSignupBlockRetentionExpiredUser\"/>");
    }

    /** 대량 데이터에 대비해 두 정리 모두 한 번에 batch 크기만큼만 읽는다. */
    @Test
    void bothRetentionSelectsArePaged() throws IOException {
        assertThat(statement(sanctionXml(), "select", "findRetentionExpiredSanctionIds"))
                .contains("LIMIT #{limit}");
        assertThat(statement(blockedXml(), "select", "findReSignupBlockRetentionExpiredUserIds"))
                .contains("LIMIT #{limit}");
    }

    /**
     * 10) users tombstone 과 공개 콘텐츠는 건드리지 않는다.
     * 5) 제재가 없는 회원은 두 SELECT 어디에도 걸리지 않는다(EXISTS/상태 조건).
     */
    @Test
    void neitherTheUserTombstoneNorPublicContentIsTouched() throws IOException {
        for (String xml : new String[]{sanctionXml(), blockedXml()}) {
            assertThat(xml)
                    .doesNotContain("DELETE FROM users")
                    .doesNotContain("DELETE u\n")
                    .doesNotContain("DELETE FROM user_posts")
                    .doesNotContain("DELETE FROM post_comments")
                    .doesNotContain("DELETE FROM user_account_actions")
                    .doesNotContain("DELETE FROM content_moderations");
        }
        // 제재 없는 회원은 차단 기록도 없어 EXISTS 에서 제외된다.
        assertThat(statement(blockedXml(), "select", "findReSignupBlockRetentionExpiredUserIds"))
                .contains("FROM blocked_emails b");
        // 제재 정리는 언제나 user_sanctions 행에서 출발한다. 제재가 없는 탈퇴 회원은
        // 고를 행 자체가 없고, users 는 읽기만 한다.
        assertThat(statement(sanctionXml(), "select", "findRetentionExpiredSanctionIds"))
                .contains("SELECT s.id")
                .contains("FROM user_sanctions s");
    }

    /** 원본 이메일은 어디에도 새로 보관하지 않는다. 로그로 나갈 값도 SQL 이 만들지 않는다. */
    @Test
    void theRetentionSqlNeverReadsOrWritesARawEmail() throws IOException {
        assertThat(blockedXml())
                .doesNotContain("user_email")
                .doesNotContain("INSERT INTO blocked_emails (email_hash, user_id, sanction_id, reason, created_by, released_at)");
        assertThat(statement(blockedXml(), "select", "findReSignupBlockRetentionExpiredUserIds"))
                .as("정리 대상은 회원 id 로만 고른다")
                .doesNotContain("email_hash");
    }

    /**
     * 재가입 차단이 아직 살아 있는 제재는 지우지 않고 남겨, 차단 기록이 sanction_id 를 잃지 않게 한다.
     * 최종 탈퇴 회원의 적용중 영구제재에서는 이 조건이 곧 순서 보장이다.
     * 차단 기록이 같은 기준으로 먼저 사라진 뒤에야 제재가 대상이 된다.
     */
    @Test
    void aSanctionWithAStillActiveBlockIsLeftAlone() throws IOException {
        assertThat(statement(sanctionXml(), "sql", "SanctionRetentionExpired"))
                .contains("NOT EXISTS")
                .contains("FROM blocked_emails b")
                .contains("b.released_at IS NULL");
    }

    /** 만료 배치는 제재를 종료시키는 역할만 유지한다. 삭제는 retention 쪽 책임이다. */
    @Test
    void theExpirySchedulerStillOnlyReleasesSanctions() throws IOException {
        String expiry = source("service/user/SanctionExpiryScheduler.java");

        assertThat(expiry)
                .contains("expireDueSanctions")
                .doesNotContain("purge")
                .doesNotContain("delete");
    }

    /** 보관조건 A: 이미 종료된 제재. OR 앞쪽 갈래다. */
    private String endedSanctionBranch() throws IOException {
        return retentionBranches()[0];
    }

    /** 보관조건 B: 최종 탈퇴 회원의 적용중 영구제재. OR 뒤쪽 갈래다. */
    private String activeSanctionBranch() throws IOException {
        return retentionBranches()[1];
    }

    /**
     * 보관조건을 OR 기준으로 두 갈래로 나눈다. 공통 안전조건(NOT EXISTS)은 뒤쪽 갈래에서 떼어낸다.
     * 갈래별로 따로 봐야 한쪽 조건이 다른 쪽으로 새는 것을 잡을 수 있다.
     */
    private String[] retentionBranches() throws IOException {
        String condition = statement(sanctionXml(), "sql", "SanctionRetentionExpired");
        String[] branches = condition.split("(?m)^\\s*OR\\s*$");
        assertThat(branches).as("보관조건은 정확히 두 갈래다").hasSize(2);

        int safeguard = branches[1].indexOf("AND NOT EXISTS");
        assertThat(safeguard).as("공통 안전조건은 두 갈래 뒤에 붙는다").isNotNegative();
        branches[1] = branches[1].substring(0, safeguard);
        return branches;
    }

    private String sanctionXml() throws IOException {
        return resource("mapper/UserSanctionMapper.xml");
    }

    private String blockedXml() throws IOException {
        return resource("mapper/BlockedEmailMapper.xml");
    }

    private String resource(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/resources").resolve(relativePath),
                StandardCharsets.UTF_8);
    }

    private String source(String relativePath) throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/example/travlediary").resolve(relativePath),
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
