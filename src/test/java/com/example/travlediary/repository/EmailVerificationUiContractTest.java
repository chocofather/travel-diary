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
                .contains("이메일 인증이 완료되지 않았나요?")
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

    /** 다른 탭에서 인증을 끝냈을 때 대기 화면이 스스로 알아채도록 polling 이 연결돼 있다. */
    @Test
    void waitingPageWiresTheStatusPollingScriptWithServerProvidedTextAndUrls() throws IOException {
        String waiting = resource("templates/verify-waiting.html");

        assertThat(waiting)
                .contains("/js/email-verification.js")
                .contains("id=\"verificationStatusPoller\"")
                .contains("id=\"verificationCompleteNotice\"")
                .contains("th:data-status-url=\"@{/users/verification/status}\"")
                .contains("th:data-login-url=\"@{/login(verified=true)}\"")
                .contains("th:data-msg-verified=\"#{verification.waiting.verified}\"")
                // 세션이 기다리는 이메일이 있으면 폴링한다. 재발송 UI 조건과는 따로 본다.
                .contains("th:if=\"${verificationPollingAvailable}\" hidden");
    }

    /** 문구는 번들에서 오고, 확인 요청에는 어떤 식별값도 싣지 않는다. */
    @Test
    void pollingScriptCarriesNoHardcodedTextNoIdentifiersAndCleansUpItsTimer() throws IOException {
        String script = resource("static/js/email-verification.js");

        assertThat(script)
                .contains("dataset.msgVerified")
                .contains("document.hidden")
                .contains("visibilitychange")
                .contains("pagehide")
                .contains("window.clearInterval")
                // 겹치는 요청을 막는 guard 와 자동 로그인 없는 이동.
                .contains("inFlight")
                .contains("window.location.href = loginUrl");
        // 주석은 한국어라도 화면에 나가는 문자열 리터럴에는 한글이 없어야 한다.
        assertThat(java.util.regex.Pattern.compile("[\"'][^\"'\\n]*[가-힣][^\"'\\n]*[\"']")
                .matcher(script).find())
                .as("hardcoded Korean string literal in email-verification.js")
                .isFalse();
        // 이메일이나 회원 id 를 요청에 싣지 않는다.
        assertThat(script).doesNotContain("statusUrl + \"?", "email=", "userId=");
    }


    /** 이메일 확인 결과에 따라 두 상태를 오가고, 이메일을 고치면 신규가입으로 되돌아간다. */
    @Test
    void socialSignupScriptSwitchesBetweenSignupAndLinkModesWithoutHardcodedText()
            throws IOException {
        String script = resource("static/js/social-signup.js");

        assertThat(script)
                .contains("function showExistingAccount(show)")
                // 신규가입 영역과 두 header 를 통째로 여닫는다.
                .contains("$newFields.prop(\"hidden\", show)")
                .contains("$newHeader.prop(\"hidden\", show)")
                .contains("$linkHeader.prop(\"hidden\", !show)")
                // 감춘 필수 입력이 브라우저 검증을 막지 않게 required 도 함께 내린다.
                .contains("$(\"#nickname\").prop(\"required\", !show)")
                .contains("$signupSubmit.prop(\"disabled\", show)")
                // 이메일을 고치면 이전 판정을 버리고 신규가입으로 복귀한다.
                .contains("showExistingAccount(status === \"EXISTING_ACTIVE\")")
                .contains("showExistingAccount(false)");
        // 문구는 전부 data-* 로 서버에서 온다.
        assertThat(java.util.regex.Pattern.compile("[\"'][^\"'\\n]*[가-힣][^\"'\\n]*[\"']")
                .matcher(script).find())
                .as("hardcoded Korean string literal in social-signup.js")
                .isFalse();
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
