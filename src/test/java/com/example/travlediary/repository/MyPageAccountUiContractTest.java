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
        String edit = resource("templates/mypage/account-edit.html");
        String social = resource("templates/mypage/account-social.html");
        String socialConnections = resource(
                "templates/fragments/mypage/social-connections.html");

        assertThat(verify)
                .contains("/mypage/account/verify-password", "autocomplete=\"current-password\"")
                .contains("navigation('account')")
                .doesNotContain("userId", "th:utext");
        assertThat(edit)
                .contains("/mypage/account/password", "/mypage/account/withdraw")
                .contains("autocomplete=\"new-password\"")
                .contains("account.username", "account.userEmail",
                        "id=\"account-info-title\"", "id=\"login-security-title\"",
                        "id=\"withdrawal-title\"")
                .contains("작성한 게시글, 댓글, 여행 코스와 문의 기록은 그대로 유지됩니다.")
                .contains("기존 로그인 ID는 다시 사용할 수 없습니다.")
                // 진입 단계에서 재인증을 마쳤으므로 이 화면은 기존 비밀번호를 다시 받지 않는다.
                .contains("th:field=\"*{confirmationPhrase}\"",
                        "mypage.account.withdrawal.confirm.phrase",
                        "mypage.account.withdrawal.submit")
                .doesNotContain("autocomplete=\"current-password\"",
                        "th:field=\"*{currentPassword}\"",
                        "/mypage/account/edit", "accountForm", "th:utext",
                        "name=\"userId\"", "th:field=\"*{username}\"",
                        "th:field=\"*{userEmail}\"", "th:field=\"*{fullName}\"",
                        "th:field=\"*{userPhone}\"", "th:field=\"*{userBirth}\"",
                        "account.fullName", "account.userPhone", "account.userBirth",
                        "personal-info-title");
        assertThat(edit).contains("/js/confirm-submit.js");
        assertThat(social).contains("/js/confirm-submit.js");
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

        assertThat(security)
                .contains("^/mypage/account/verify-password$",
                        "^/mypage/account/edit$",
                        "^/mypage/account/password$",
                        "^/mypage/account/withdraw$",
                        "^/mypage/account/social-connections/[^/]+$",
                        "^/mypage/account/social-connections/[^/]+/disconnect$")
                .contains("/mypage/**")
                .doesNotContain("csrf(AbstractHttpConfigurer::disable)");
    }

    private String resource(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/resources").resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
