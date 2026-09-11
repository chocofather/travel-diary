package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 최종 파기 SQL 의 계약.
 * 대상 선정과 익명화는 되돌릴 수 없으므로 조건과 대상 컬럼을 쿼리 수준에서 고정한다.
 */
class AccountPurgeMapperContractTest {

    /** 유예가 끝났고 아직 파기되지 않은 일반 회원만 대상이다. */
    @Test
    void dueAccountsAreSelectedByStatusAndScheduleOnly() throws IOException {
        String select = statement(userXml(), "select", "findDueWithdrawalUserIds");

        assertThat(select)
                .contains("user_role = 'USER'")
                .contains("status = 'WITHDRAWAL_PENDING'")
                .contains("deleted_at IS NULL")
                .contains("purge_scheduled_at IS NOT NULL")
                .contains("purge_scheduled_at &lt;= #{currentTime}")
                // 한 번에 전체를 읽지 않는다
                .contains("LIMIT #{limit}")
                // 개인정보를 목록으로 끌어오지 않는다
                .contains("SELECT id")
                .doesNotContain("user_email", "nickname", "user_password", "full_name");
    }

    /** 파기 직전 재검증은 행을 잠그고 같은 조건을 다시 본다. */
    @Test
    void thePurgeTargetIsRevalidatedUnderARowLock() throws IOException {
        String select = statement(userXml(), "select", "findPurgeTargetByIdForUpdate");

        assertThat(select)
                .contains("id = #{id}")
                .contains("user_role = 'USER'")
                .contains("status = 'WITHDRAWAL_PENDING'")
                .contains("deleted_at IS NULL")
                .contains("purge_scheduled_at IS NOT NULL")
                .contains("purge_scheduled_at &lt;= #{currentTime}")
                .contains("FOR UPDATE")
                // 파일 삭제 task 를 만들려면 프로필 경로가 필요하다
                .contains("profile_image")
                .doesNotContain("user_password");
    }

    @Test
    void finalizeClearsEveryIdentifyingColumnAndKeepsTheWithdrawalTrail() throws IOException {
        String update = statement(userXml(), "update", "finalizeWithdrawal");

        assertThat(update)
                .contains("username = NULL")
                .contains("user_email = #{userEmail}")
                .contains("nickname = #{nickname}")
                .contains("full_name = NULL")
                .contains("user_phone = NULL")
                .contains("user_birth = NULL")
                .contains("user_password = NULL")
                .contains("profile_image = NULL")
                .contains("last_login = NULL")
                .contains("verification_token = NULL")
                .contains("verification_token_exp = NULL")
                .contains("verification_requested_at = NULL")
                .contains("reset_token = NULL")
                .contains("reset_token_exp = NULL")
                .contains("status = #{status}")
                .contains("deleted_at = #{deletedAt}")
                .contains("updated_at = NOW()");

        // 탈퇴 경위와 가입 시점, 역할은 남긴다 (WHERE 조건과 섞이지 않게 SET 절만 본다)
        String setClause = update.substring(0, update.indexOf("WHERE"));
        assertThat(setClause)
                .doesNotContain("withdrawal_requested_at")
                .doesNotContain("purge_scheduled_at")
                .doesNotContain("created_at")
                .doesNotContain("user_role");

        // 조건 없는 대량 UPDATE 가 되지 않도록 대상 조건을 전부 다시 건다
        assertThat(update)
                .contains("WHERE id = #{id}")
                .contains("user_role = 'USER'")
                .contains("status = 'WITHDRAWAL_PENDING'")
                .contains("deleted_at IS NULL")
                .contains("purge_scheduled_at IS NOT NULL")
                .contains("purge_scheduled_at &lt;= #{currentTime}");
    }

    /** 스티커 등 업로드가 아닌 요소가 파일 삭제 후보로 섞이지 않게 한다. */
    @Test
    void onlyPhotoElementsAreCollectedAsFileDeleteCandidates() throws IOException {
        String purgeXml = resource("mapper/AccountPurgeMapper.xml");

        assertThat(statement(purgeXml, "select", "findDiaryImageUrlsByUserId"))
                .contains("e.element_type = 'PHOTO'")
                .contains("d.cover_image_url")
                .contains("user_id = #{userId}");
        assertThat(statement(purgeXml, "select", "findCoverDesignImageUrlsByUserId"))
                .contains("e.element_type = 'PHOTO'")
                .contains("user_id = #{userId}");
    }

    /** 회원당 job 은 하나뿐이라 잠그고 확인한 뒤 이어 쓴다. */
    @Test
    void thePurgeJobIsLookedUpUnderALockBeforeInserting() throws IOException {
        String purgeXml = resource("mapper/AccountPurgeMapper.xml");

        assertThat(statement(purgeXml, "select", "findJobByUserIdForUpdate"))
                .contains("user_id = #{userId}")
                .contains("FOR UPDATE");
        assertThat(statement(purgeXml, "update", "markJobDbDone"))
                .contains("status = 'DB_DONE'")
                .contains("db_completed_at = #{dbCompletedAt}");
        assertThat(statement(purgeXml, "update", "markJobCompleted"))
                .contains("status = 'COMPLETED'")
                .contains("completed_at = #{completedAt}");
    }

    /** 회원 행을 tombstone 으로 남기므로 FK CASCADE 가 돌지 않아 직접 지워야 한다. */
    @Test
    void perUserDeletesAreScopedByUserId() throws IOException {
        assertThat(statement(resource("mapper/DiaryMapper.xml"), "delete", "deleteAllByUserId"))
                .contains("DELETE FROM diaries")
                .contains("WHERE user_id = #{userId}");
        assertThat(statement(resource("mapper/DiaryCoverDesignMapper.xml"), "delete",
                "deleteAllByUserId"))
                .contains("DELETE FROM diary_cover_designs")
                .contains("WHERE user_id = #{userId}");
        assertThat(statement(resource("mapper/AccountRecoveryTokenMapper.xml"), "delete",
                "deleteAllByUserId"))
                .contains("DELETE FROM account_recovery_tokens")
                .contains("WHERE user_id = #{userId}");
        assertThat(statement(resource("mapper/GoogleTranslationDailyUsageMapper.xml"), "delete",
                "deleteAllByUserId"))
                .contains("DELETE FROM google_translation_daily_usage")
                .contains("subject_type = 'USER'")
                .contains("user_id = #{userId}");
    }

    /**
     * 실행 대상은 아직 끝나지 않은 task 이고, 재시도 시각이 남았으면 빠진다.
     * 종류와 provider 는 부르는 worker 가 정하므로 다른 worker 의 task 가 섞이지 않는다.
     */
    @Test
    void onlyReadyTasksOfTheCallersOwnKindArePickedUp() throws IOException {
        String select = statement(resource("mapper/AccountPurgeMapper.xml"), "select",
                "findReadyTasks");

        assertThat(select)
                .contains("status = 'PENDING'")
                .contains("task_type = #{taskType}")
                .contains("AND provider = #{provider}")
                .contains("next_retry_at IS NULL OR next_retry_at &lt;= #{currentTime}")
                .contains("LIMIT #{limit}")
                // COMPLETED / FAILED 는 status 조건에서 빠진다. 종류를 코드에 고정하지 않는다.
                .doesNotContain("'FILE_DELETE'")
                .doesNotContain("'SOCIAL_UNLINK'");
    }

    /** 같은 task 를 두 worker 가 동시에 집지 못하게 조건부 UPDATE 로 집어 든다. */
    @Test
    void claimingATaskIsAConditionalUpdateThatLeasesIt() throws IOException {
        String update = statement(resource("mapper/AccountPurgeMapper.xml"), "update",
                "claimTask");

        assertThat(update)
                .contains("attempts = attempts + 1")
                .contains("last_attempt_at = #{currentTime}")
                .contains("next_retry_at = #{leaseUntil}")
                .contains("id = #{id}")
                .contains("status = 'PENDING'")
                // 종류와 provider 조건이 함께 걸려 다른 worker 의 task 를 집을 수 없다
                .contains("task_type = #{taskType}")
                .contains("AND provider = #{provider}")
                .contains("next_retry_at IS NULL OR next_retry_at &lt;= #{currentTime}");
    }

    @Test
    void taskOutcomesOnlyApplyToATaskThatIsStillOpen() throws IOException {
        String purgeXml = resource("mapper/AccountPurgeMapper.xml");

        assertThat(statement(purgeXml, "update", "markTaskCompleted"))
                .contains("status = 'COMPLETED'")
                .contains("completed_at = #{completedAt}")
                .contains("next_retry_at = NULL")
                .contains("last_error = NULL")
                .contains("status = 'PENDING'");
        assertThat(statement(purgeXml, "update", "markTaskRetry"))
                .contains("next_retry_at = #{nextRetryAt}")
                .contains("last_error = #{lastError}")
                .contains("status = 'PENDING'");
        assertThat(statement(purgeXml, "update", "markTaskFailed"))
                .contains("status = 'FAILED'")
                .contains("next_retry_at = NULL")
                .contains("status = 'PENDING'");
    }

    /**
     * job 완료는 남은 task 가 하나도 없을 때만이다.
     * SOCIAL_UNLINK 가 PENDING 이거나 FILE_DELETE 가 FAILED 면 조건이 깨져 DB_DONE 이 유지된다.
     */
    @Test
    void theJobIsClosedOnlyWhenEveryTaskIsCompleted() throws IOException {
        String update = statement(resource("mapper/AccountPurgeMapper.xml"), "update",
                "completeJobIfAllTasksDone");

        assertThat(update)
                .contains("status = 'COMPLETED'")
                .contains("completed_at = #{completedAt}")
                .contains("id = #{purgeJobId}")
                // 이미 끝난 job 을 다시 완료 처리하지 않는다
                .contains("AND status = 'DB_DONE'")
                .contains("NOT EXISTS")
                .contains("FROM account_purge_tasks t")
                .contains("t.status &lt;&gt; 'COMPLETED'");
    }

    /** 공개 콘텐츠와 운영 기록을 지우는 문장이 파기 경로에 생기지 않았는지 확인한다. */
    @Test
    void noPublicContentOrOperationalRecordIsDeletedByThePurgeMapper() throws IOException {
        String purgeXml = resource("mapper/AccountPurgeMapper.xml");

        assertThat(purgeXml)
                .doesNotContain("DELETE FROM user_posts")
                .doesNotContain("DELETE FROM post_comments")
                .doesNotContain("DELETE FROM destination_comments")
                .doesNotContain("DELETE FROM courses")
                .doesNotContain("DELETE FROM course_comments")
                .doesNotContain("DELETE FROM travel_plans")
                .doesNotContain("DELETE FROM inquiries")
                .doesNotContain("DELETE FROM user_policy_consents")
                .doesNotContain("DELETE FROM content_moderations")
                .doesNotContain("DELETE FROM user_sanctions")
                .doesNotContain("DELETE FROM user_appeals")
                .doesNotContain("DELETE FROM user_account_actions")
                .doesNotContain("DELETE FROM blocked_emails")
                .doesNotContain("DELETE FROM users");
    }

    private String userXml() throws IOException {
        return resource("mapper/UserMapper.xml");
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
