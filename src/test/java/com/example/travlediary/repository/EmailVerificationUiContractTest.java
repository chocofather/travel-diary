package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationUiContractTest {

    @Test
    void standaloneResendFormAcceptsOnlyEmailAndIncludesCsrfAndAccessibleHints() throws IOException {
        String template = resource("templates/verification-resend.html");

        assertThat(template)
                .contains("th:action=\"@{/users/verification/resend}\"")
                .contains("method=\"post\"")
                .contains("name=\"email\"")
                .contains("type=\"email\"")
                .contains("autocomplete=\"email\"")
                .contains("inputmode=\"email\"")
                .contains("${_csrf.parameterName}", "${_csrf.token}")
                .contains("인증이 필요한 계정인 경우")
                .doesNotContain("name=\"userId\"", "name=\"username\"", "name=\"token\"");
    }

    @Test
    void loginAndSessionlessWaitingStateExposeTheRecoveryEntry() throws IOException {
        String login = resource("templates/login.html");
        String waiting = resource("templates/verify-waiting.html");

        assertThat(login)
                .contains("인증메일을 받지 못하셨나요?")
                .contains("@{/users/verification/resend}");
        assertThat(waiting)
                .contains("@{/users/verification/resend}")
                .contains("가입 이메일을 입력해주세요");
    }

    @Test
    void registrationAndResendReuseTheSameSmallDomainSuggestionPolicy() throws IOException {
        String registration = resource("templates/register.html");
        String resend = resource("templates/verification-resend.html");
        String suggestion = resource("static/js/email-domain-suggestion.js");

        assertThat(registration).contains("/js/email-domain-suggestion.js");
        assertThat(resend).contains("/js/email-domain-suggestion.js");
        assertThat(suggestion)
                .contains("[\"gamil.com\", \"gmail.com\"]")
                .contains("TravelDiaryEmailDomain");
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
