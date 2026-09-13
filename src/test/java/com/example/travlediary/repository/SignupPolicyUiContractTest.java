package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가입 화면의 약관 영역이 DB 정책을 그대로 따르는지 고정한다.
 * 화면에 정책 종류를 하드코딩하면 DB 에서 필수/선택을 바꿔도 화면이 따라오지 않는다.
 */
class SignupPolicyUiContractTest {

    /** 체크박스 값은 실제 policy_versions.id 이고, 항목은 DB 세트를 순회해서 만든다. */
    @Test
    void bothSignupScreensRenderConsentsFromThePolicySet() throws IOException {
        for (String template : new String[]{register(), socialSignup()}) {
            assertThat(template)
                    .contains("${signupPolicies.consentPolicies}")
                    .contains("name=\"agreedPolicyVersionIds\"")
                    .contains("th:value=\"${policy.policyVersionId}\"")
                    .contains("data-policy-consent=\"true\"")
                    .contains("th:attr=\"data-policy-required=${policy.required}\"");
        }
    }

    /** 필수/선택 표기는 화면 문자열이 아니라 policy_versions.is_required 가 정한다. */
    @Test
    void theRequiredBadgeFollowsTheDatabaseFlag() throws IOException {
        assertThat(register())
                .contains("th:classappend=\"${policy.required} ? 'required' : 'optional'\"");
        for (String template : new String[]{register(), socialSignup()}) {
            // 필수/선택 문구를 고정 문자열로 찍지 않고 정책 플래그로 고른다.
            assertThat(template)
                    .contains("${policy.required}")
                    .contains("#{signup.terms.required}")
                    .contains("#{signup.terms.optional}");
        }
    }

    /** 개인정보처리방침은 동의 체크박스를 만들지 않고 열람만 제공한다. */
    @Test
    void theViewOnlyPolicyHasNoCheckbox() throws IOException {
        for (String template : new String[]{register(), socialSignup()}) {
            assertThat(template).contains("${signupPolicies.viewOnlyPolicies}");

            String viewOnly = template.substring(
                    template.indexOf("${signupPolicies.viewOnlyPolicies}"));
            // 열람 전용 블록 안에는 체크박스가 없다.
            assertThat(viewOnly).doesNotContain("type=\"checkbox\"");
        }
        // 과거의 고정 체크박스 이름이 남아 있지 않다.
        for (String template : new String[]{register(), socialSignup()}) {
            assertThat(template)
                    .doesNotContain("serviceTermsAccepted")
                    .doesNotContain("privacyTermsAccepted")
                    .doesNotContain("termsAccepted")
                    .doesNotContain("privacyAccepted");
        }
    }

    /** 본문은 화면 안에서 열고 닫으며, 카드 안에서만 스크롤된다. 끝까지 읽어야 체크되지 않는다. */
    @Test
    void thePolicyBodyScrollsInsideItsOwnBoxAndNeverGatesTheCheckbox() throws IOException {
        // 두 화면이 같은 약관 카드 / 같은 본문 상자를 쓴다.
        assertThat(resource("static/css/registration.css"))
                .contains(".term-content")
                .contains("max-height: 260px")
                .contains("overflow-y: auto");
        for (String template : new String[]{register(), socialSignup()}) {
            assertThat(template).contains("/css/registration.css")
                    .contains("class=\"term-content\"");
        }

        // 스크롤 위치나 읽음 여부로 체크박스를 잠그는 코드가 없다.
        for (String script : new String[]{
                resource("static/js/register.js"), resource("static/js/social-signup.js")}) {
            assertThat(script)
                    .doesNotContain("scrollTop")
                    .doesNotContain("scrollHeight");
        }
    }

    /** 다음 단계/제출 버튼은 DB 가 필수라고 말한 항목만 요구한다. */
    @Test
    void theClientGateReadsTheRequiredFlagInsteadOfFixedCheckboxIds() throws IOException {
        assertThat(resource("static/js/register.js"))
                .contains("[data-policy-consent][data-policy-required='true']")
                .doesNotContain("#termsAgree1")
                .doesNotContain("#termsAgree2")
                .doesNotContain("#termsAgree3");
        assertThat(resource("static/js/social-signup.js"))
                .contains("[data-policy-consent][data-policy-required='true']");
    }

    /** 기존 계정 연결 경로는 새 회원을 만들지 않으므로 동의 항목이 붙지 않는다. */
    @Test
    void theExistingAccountLinkFormCarriesNoConsent() throws IOException {
        String socialSignup = socialSignup();
        String linkForm = socialSignup.substring(
                socialSignup.indexOf("id=\"socialSignupExistingAccount\""));

        assertThat(linkForm)
                .contains("/social-signup/link-existing")
                .doesNotContain("agreedPolicyVersionIds")
                .doesNotContain("type=\"checkbox\"");
    }

    /**
     * 12) 기존 계정 로그인/연결은 신규 users 를 만들지 않으므로 신규가입 동의를 받지 않는다.
     * 동의 기록은 새 회원을 만드는 두 경로에만 있다.
     */
    @Test
    void onlyTheTwoSignupPathsRecordConsents() throws IOException {
        for (String path : new String[]{
                "service/user/SocialLoginLinkService.java",
                "service/user/SocialSignupAuthenticationService.java",
                "service/user/SocialEmailAccountResolver.java"}) {
            assertThat(source(path))
                    .as(path)
                    .doesNotContain("PolicyConsentRecorder")
                    .doesNotContain("SignupPolicyService");
        }

        // 새 회원을 만드는 경로에만 동의 기록이 붙어 있다.
        assertThat(source("service/user/SocialSignupService.java"))
                .contains("policyConsentRecorder.record");
        assertThat(source("service/user/RegistrationTransactionService.java"))
                .contains("policyConsentRecorder.record");
    }

    private String source(String relativePath) throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/example/travlediary").resolve(relativePath),
                StandardCharsets.UTF_8);
    }

    private String register() throws IOException {
        return resource("templates/register.html");
    }

    private String socialSignup() throws IOException {
        return resource("templates/social-signup.html");
    }

    private String resource(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/resources").resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
