package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AccountRecoveryUiContractTest {

    @Test
    void recoveryAndEmailAccountPagesUseTheScopedAuthFlowPresentation() throws IOException {
        for (String path : new String[]{
                "templates/find-username.html",
                "templates/find-password.html",
                "templates/reset-password.html",
                "templates/recover-account-confirm.html",
                "templates/account/email-required.html",
                "templates/account/email-change.html",
                "templates/account/email-change-password.html"
        }) {
            assertThat(resource(path))
                    .as(path)
                    .contains("class=\"login-page auth-flow-page\"")
                    .contains("class=\"auth-flow-icon\"")
                    .contains("<svg")
                    .doesNotContain(">✉<");
        }

        assertThat(resource("templates/login.html"))
                .doesNotContain("auth-flow-page", "auth-flow-icon");
    }

    @Test
    void scopedAuthFlowKeepsSemanticErrorsAndUsesPurpleKeyboardFocus() throws IOException {
        String css = resource("static/css/login.css");

        assertThat(css)
                .contains(".login-page.auth-flow-page .login-field input[aria-invalid=\"true\"]")
                .contains(".login-page.auth-flow-page .login-password-control .toggle-password:focus-visible")
                .contains("border-color: #d92d20;")
                .contains("outline-color: rgba(118, 87, 200, 0.28);");
    }

    @Test
    void usernameRecoveryAcceptsOnlyEmailAndUsesAGenericCompletionState() throws IOException {
        String template = resource("templates/find-username.html");

        assertThat(template)
                .contains("/css/login.css", "name=\"userEmail\"", "type=\"email\"")
                .contains("th:if=\"${recoveryRequested}\"")
                .contains("아이디 안내를 요청했어요")
                .contains("입력하신 이메일과 일치하는 계정이 있다면")
                .doesNotContain("name=\"fullName\"", "정보가 일치하지 않습니다.",
                        "register-container", "<style>", "${username}");
    }

    @Test
    void passwordRecoveryKeepsUsernameAndEmailAndUsesAGenericCompletionState()
            throws IOException {
        String template = resource("templates/find-password.html");

        assertThat(template)
                .contains("/css/login.css", "name=\"username\"", "name=\"userEmail\"")
                .contains("th:if=\"${recoveryRequested}\"")
                .contains("재설정 링크를 요청했어요")
                .contains("입력하신 정보와 일치하는 계정이 있다면")
                .doesNotContain("정보가 일치하지 않습니다.", "<style>");
    }

    @Test
    void loginPageContractsRemainAvailable() throws IOException {
        String template = resource("templates/login.html");

        assertThat(template)
                .contains("id=\"loginForm\"", "id=\"username\"", "name=\"username\"")
                .contains("id=\"rememberId\"", "id=\"loginPassword\"", "name=\"password\"")
                .contains("name=\"redirect\"", "data-toggle=\"#loginPassword\"");
    }

    @Test
    void resetPasswordUsesTheSharedAuthUiAndRequiresPasswordConfirmation()
            throws IOException {
        String template = resource("templates/reset-password.html");

        assertThat(template)
                .contains("/css/login.css", "class=\"login-page auth-flow-page\"")
                .contains("name=\"token\"", "name=\"newPassword\"")
                .contains("name=\"newPasswordConfirm\"")
                .contains("data-toggle=\"#newPassword\"")
                .contains("data-toggle=\"#newPasswordConfirm\"")
                .contains("${passwordPolicyMessage}")
                .doesNotContain("<style>");
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
