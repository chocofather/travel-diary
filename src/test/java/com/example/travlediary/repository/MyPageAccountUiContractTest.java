package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MyPageAccountUiContractTest {

    @Test
    void accountAndSecurityPageOmitsPersonalDetailsAndKeepsSafeBindings() throws IOException {
        String verify = resource("templates/mypage/account-verify.html");
        String account = resource("templates/mypage/account.html");
        String edit = resource("templates/mypage/account-edit.html");
        String withdraw = resource("templates/mypage/account-withdraw.html");
        String socialConnections = resource(
                "templates/fragments/mypage/social-connections.html");

        assertThat(verify)
                .contains("/mypage/account/verify-password", "autocomplete=\"current-password\"")
                .contains("name=\"next\"", "navigation('account')")
                .doesNotContain("userId", "th:utext");
        // 공통 계정 관리: 계정 정보 · 로그인 및 보안(비밀번호 변경 메뉴 + 소셜 연결) · 회원 탈퇴. 비밀번호는 묻지 않는다.
        assertThat(account)
                .contains("account.userEmail",
                        "id=\"account-info-title\"", "id=\"login-security-title\"",
                        "id=\"social-connection-title\"", "id=\"withdrawal-title\"")
                .contains("@{/mypage/account/edit}", "@{/mypage/account/withdraw}",
                        "@{/mypage/account/social-withdrawal}", "th:if=\"${localPasswordAccount}\"")
                .contains("~{fragments/mypage/social-connections :: rows}", "/js/confirm-submit.js")
                .doesNotContain("th:field=\"*{currentPassword}\"", "th:field=\"*{newPassword}\"",
                        "th:utext", "name=\"userId\"", "account.fullName", "account.userPhone",
                        "account.userBirth", "personal-info-title");
        // 비밀번호 변경 전용: 소셜 연결·탈퇴를 중복해서 두지 않는다.
        assertThat(edit)
                .contains("/mypage/account/password", "autocomplete=\"new-password\"")
                .doesNotContain("autocomplete=\"current-password\"", "th:field=\"*{currentPassword}\"",
                        "social-connections", "/mypage/account/withdraw", "th:utext",
                        "name=\"userId\"", "account.fullName", "account.userPhone", "account.userBirth");
        // 탈퇴: 비밀번호 확인 뒤 들어오므로 확인 문구만 다시 받는다.
        assertThat(withdraw)
                .contains("/mypage/account/withdraw",
                        "작성한 게시글, 댓글, 여행 코스와 문의 기록은 그대로 유지됩니다.",
                        "th:field=\"*{confirmationPhrase}\"",
                        "mypage.account.withdrawal.confirm.phrase",
                        "mypage.account.withdrawal.submit", "/js/withdrawal-confirm.js")
                .doesNotContain("autocomplete=\"current-password\"", "th:field=\"*{currentPassword}\"",
                        "social-connections", "th:utext", "name=\"userId\"");
        assertThat(socialConnections)
                .contains("/social-connections/{provider}/disconnect",
                        "method=\"post\"", "th:data-confirm",
                        "mypage.account.social.disconnect.action");
    }

    @Test
    void navigationAndMainLinkToTheAccountVerificationEntry() throws IOException {
        assertThat(resource("templates/fragments/mypage/navigation.html"))
                .contains("activeMenu == 'account'", "@{/mypage/account}")
                .doesNotContain("is-disabled\" aria-disabled=\"true\">회원정보 수정");
        assertThat(resource("templates/mypage/index.html"))
                .contains("th:href=\"@{/mypage/account}\"", "계정 및 보안")
                .doesNotContain("계정 정보 관리 기능은 준비 중입니다.");
    }

    @Test
    void securityProtectsAccountMutationsWithCsrf() throws IOException {
        String security = Files.readString(Path.of(
                "src/main/java/com/example/travlediary/config/SecurityConfig.java"),
                StandardCharsets.UTF_8);

        // 계정 변경 POST 는 기본 CSRF 정책이 보호한다. (보호 목록을 따로 두지 않는다)
        assertThat(security)
                .contains("/mypage/**")
                .doesNotContain("requireCsrfProtectionMatcher")
                .doesNotContain("ignoringRequestMatchers")
                .doesNotContain("csrf(AbstractHttpConfigurer::disable)")
                .doesNotContain("csrf.disable()");
    }

    private String resource(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/resources").resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
