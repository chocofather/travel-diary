package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationMapperContractTest {

    @Test
    void mapperRestrictsVerificationLifecycleToPendingNonDeletedUsers() throws IOException {
        String mapper = resource("mapper/UserMapper.xml");
        String obsoleteColumn = "verification_" + "sent_at";
        String obsoleteProperty = "verification" + "SentAt";

        assertThat(mapper)
                .contains("id=\"findPendingVerificationByToken\"")
                .contains("id=\"activatePendingUser\"")
                .contains("id=\"refreshVerificationToken\"")
                .contains("verification_token_exp = NULL")
                .contains("status = 'INACTIVE'")
                .contains("verification_token = #{token}")
                .contains("deleted_at IS NULL")
                .contains("verification_requested_at &lt;= #{cooldownCutoff}")
                .doesNotContain(obsoleteColumn, obsoleteProperty)
                .doesNotContain("id=\"updateUser\"");
    }

    @Test
    void schemaReferenceDocumentsVerificationExpirationAndCooldownColumns() throws IOException {
        String schema = workspaceFile("docs/db/travel_diary_schema_reference.md");
        String obsoleteColumn = "verification_" + "sent_at";
        String obsoleteProperty = "verification" + "SentAt";

        assertThat(schema)
                .contains("`verification_token_exp` datetime DEFAULT NULL")
                .contains("`verification_requested_at` datetime DEFAULT NULL")
                .doesNotContain(obsoleteColumn, obsoleteProperty);
    }


    /**
     * 예전 소셜 회원 이메일 보완 대상 판정.
     * Google 만 연결된 계정이 섞이지 않도록 provider 를 KAKAO/NAVER 로 못박는다.
     */
    @Test
    void theMissingEmailTargetQueryOnlyMatchesActiveKakaoOrNaverAccounts() throws IOException {
        String mapper = resource("mapper/UserMapper.xml");
        String query = between(mapper, "id=\"isSocialAccountMissingEmail\"", "</select>");

        assertThat(query)
                .contains("JOIN social_accounts sa ON sa.user_id = u.id")
                .contains("u.status = 'ACTIVE'")
                .contains("u.deleted_at IS NULL")
                // 공백 이메일도 이메일 없음으로 본다.
                .contains("u.user_email IS NULL OR TRIM(u.user_email) = ''")
                .contains("sa.provider IN ('KAKAO', 'NAVER')")
                .doesNotContain("GOOGLE");
    }

    /**
     * 이메일 등록은 같은 users 행을 인증 대기로 되돌릴 뿐이다.
     * 대상 조건을 WHERE 로 다시 걸어 상태가 바뀌었으면 아무것도 바꾸지 않는다.
     */
    @Test
    void theMissingEmailUpdateOnlyTouchesTheVerificationColumnsOfAnEligibleRow()
            throws IOException {
        String mapper = resource("mapper/UserMapper.xml");
        String update = between(
                mapper, "id=\"startEmailVerificationForSocialAccount\"", "</update>");

        assertThat(update)
                .contains("UPDATE users")
                .contains("user_email = #{userEmail}")
                .contains("status = 'INACTIVE'")
                .contains("verification_token = #{token}")
                .contains("verification_token_exp = #{expiresAt}")
                .contains("verification_requested_at = #{requestedAt}")
                // 대상 조건 재확인.
                .contains("AND status = 'ACTIVE'")
                .contains("AND deleted_at IS NULL")
                .contains("sa.provider IN ('KAKAO', 'NAVER')")
                // 기존 계정 정보는 손대지 않는다.
                .doesNotContain("nickname =", "profile_image =", "user_password =",
                        "deleted_at =", "DELETE", "INSERT");
    }


    /**
     * 이메일 오타 수정. 가입 경로를 가리지 않고 "이메일 인증 대기" 행이면 바뀐다.
     * 지금 이메일이 기대값과 같아야 하므로, 예전 링크로 이미 ACTIVE 가 됐다면 조건이 깨진다.
     */
    @Test
    void theEmailCorrectionUpdateOnlyTouchesAStillPendingRowWithTheExpectedAddress()
            throws IOException {
        String mapper = resource("mapper/UserMapper.xml");
        String update = between(mapper, "id=\"changePendingVerificationEmail\"", "</update>");

        assertThat(update)
                .contains("UPDATE users")
                .contains("user_email = #{userEmail}")
                // 새 토큰이 예전 토큰을 덮어써 옛 링크가 즉시 무효가 된다.
                .contains("verification_token = #{token}")
                .contains("verification_token_exp = #{expiresAt}")
                .contains("verification_requested_at = #{requestedAt}")
                // 경합 방어: 상태와 현재 이메일을 WHERE 에서 다시 본다.
                .contains("AND status = 'INACTIVE'")
                .contains("AND deleted_at IS NULL")
                .contains("AND user_email = #{expectedCurrentEmail}")
                .contains("AND verification_token IS NOT NULL")
                // 일반 회원가입도 대상이므로 소셜 연결이나 legacy purpose 를 요구하지 않는다.
                .doesNotContain("social_accounts", "LEGACY_SOCIAL_EMAIL",
                        "LegacySocialEmailPurpose")
                // 계정 정보와 상태는 그대로 둔다.
                .doesNotContain("status = 'ACTIVE'", "nickname =", "profile_image =",
                        "user_password =", "deleted_at =", "DELETE", "INSERT");
    }

    /** 변경 대상 판정도 Kakao/Naver 인증 대기로 한정한다. */
    @Test
    void theCorrectionTargetQueryOnlyMatchesPendingKakaoOrNaverAccounts() throws IOException {
        String mapper = resource("mapper/UserMapper.xml");
        String query = between(mapper, "id=\"isSocialAccountVerificationPending\"", "</select>");

        assertThat(query)
                .contains("u.status = 'INACTIVE'")
                .contains("u.deleted_at IS NULL")
                .contains("u.user_email IS NOT NULL")
                .contains("u.verification_token IS NOT NULL")
                .contains("sa.provider IN ('KAKAO', 'NAVER')")
                .doesNotContain("GOOGLE");
    }


    /**
     * email_verification_purpose 는 예전 소셜 회원 보완에만 기록되고,
     * 인증 완료와 최종 파기에서 정리된다. 값은 이 파일 한 곳에서만 정의한다.
     */
    @Test
    void theVerificationPurposeIsWrittenOnlyForTheLegacyFlowAndClearedWhenItEnds()
            throws IOException {
        String mapper = resource("mapper/UserMapper.xml");

        // 값의 출처는 한 곳뿐이다.
        assertThat(between(mapper, "<sql id=\"LegacySocialEmailPurpose\"", "</sql>"))
                .contains("'LEGACY_SOCIAL_EMAIL'");
        assertThat(mapper.split("'LEGACY_SOCIAL_EMAIL'", -1)).hasSize(2);

        // 보완 시작에서만 기록한다.
        assertThat(between(mapper, "id=\"startEmailVerificationForSocialAccount\"", "</update>"))
                .contains("email_verification_purpose = <include refid=\"LegacySocialEmailPurpose\"/>");
        // 신규 가입은 컬럼 목록에 없어 DEFAULT NULL 이다.
        assertThat(between(mapper, "id=\"insertUser\"", "</insert>"))
                .doesNotContain("email_verification_purpose");

        // 인증 완료와 최종 파기에서 정리한다.
        assertThat(between(mapper, "id=\"activatePendingUser\"", "</update>"))
                .contains("status = 'ACTIVE'")
                .contains("email_verification_purpose = NULL");
        assertThat(between(mapper, "id=\"finalizeWithdrawal\"", "</update>"))
                .contains("email_verification_purpose = NULL");
    }

    /** 오타 수정은 purpose 를 건드리지 않는다. legacy 값은 인증 완료까지 그대로 유지된다. */
    @Test
    void theEmailCorrectionNeverTouchesTheVerificationPurpose() throws IOException {
        String update = between(resource("mapper/UserMapper.xml"),
                "id=\"changePendingVerificationEmail\"", "</update>");

        assertThat(update).doesNotContain("email_verification_purpose");
    }

    /** 변경 자격은 가입 경로를 가리지 않는 "이메일 인증 대기" 하나로만 본다. */
    @Test
    void theCommonPendingCheckIgnoresTheSignupPathAndTheTokenExpiry() throws IOException {
        String query = between(resource("mapper/UserMapper.xml"),
                "id=\"isEmailVerificationPending\"", "</select>");

        assertThat(query)
                .contains("u.status = 'INACTIVE'")
                .contains("u.deleted_at IS NULL")
                .contains("u.user_email IS NOT NULL")
                .contains("u.verification_token IS NOT NULL")
                // 가입 경로를 가리지 않는다.
                .doesNotContain("social_accounts", "email_verification_purpose")
                // 링크가 만료됐어도 본인확인 뒤 새 토큰으로 복구할 수 있어야 한다.
                .doesNotContain("verification_token_exp");
    }

    /** 판정 쿼리도 purpose 완전 일치로 본다. LIKE/prefix 로 넓히지 않는다. */
    @Test
    void thePendingCheckComparesThePurposeExactly() throws IOException {
        String query = between(resource("mapper/UserMapper.xml"),
                "id=\"isSocialAccountVerificationPending\"", "</select>");

        assertThat(query)
                .contains("AND u.email_verification_purpose = "
                        + "<include refid=\"LegacySocialEmailPurpose\"/>")
                .doesNotContain("LIKE", "%");
    }

    /** 문서의 users DDL 이 실제 컬럼과 같아야 한다. */
    @Test
    void theSchemaReferenceDocumentsTheVerificationPurposeColumn() throws IOException {
        String users = between(workspaceFile("docs/db/travel_diary_schema_reference.md"),
                "CREATE TABLE `users`", ") ENGINE=InnoDB");

        assertThat(users).contains("`email_verification_purpose` varchar(30) DEFAULT NULL");
        // 실제 DB 와 같은 자리(verification_requested_at 뒤)에 있어야 한다.
        assertThat(users.indexOf("`email_verification_purpose`"))
                .isGreaterThan(users.indexOf("`verification_requested_at`"))
                .isLessThan(users.indexOf("`profile_image`"));
    }

    private String between(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        assertThat(start).as("%s exists", startMarker).isNotNegative();
        int end = text.indexOf(endMarker, start);
        assertThat(end).as("%s is closed", startMarker).isNotNegative();
        return text.substring(start, end);
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String workspaceFile(String path) throws IOException {
        return java.nio.file.Files.readString(java.nio.file.Path.of(path), StandardCharsets.UTF_8);
    }
}
