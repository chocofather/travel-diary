package com.example.travlediary.controller.user;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MyPageNicknameUiContractTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final String FORMAT_GUIDANCE =
            "2~12자의 한글, 영문, 숫자만 사용할 수 있습니다.";
    private static final String POLICY_GUIDANCE =
            "공백·특수문자 및 부적절한 표현은 사용할 수 없습니다.";

    @Test
    void profileUiUsesDebouncedAbortableAuthenticatedNicknameCheck() throws IOException {
        String template = read("templates/mypage/profile.html");
        String script = read("static/js/mypage-profile.js");

        assertThat(template)
                .contains("maxlength=\"12\"", FORMAT_GUIDANCE, POLICY_GUIDANCE,
                        "/js/mypage-profile.js", "id=\"nickname-availability\"",
                        "id=\"profileSaveButton\"", "data-current-nickname");
        assertThat(script)
                .contains("/mypage/profile/check-nickname?nickname=",
                        "new AbortController()", "requestSequence", "}, 250)",
                        "nicknameInput.dataset.currentNickname",
                        "saveButton.disabled",
                        "case \"FORBIDDEN\"",
                        "setState(\"forbidden\"",
                        "사용할 수 없는 닉네임입니다.",
                        "현재 사용 중인 닉네임입니다.",
                        "case \"AVAILABLE\"",
                        "case \"DUPLICATE\"",
                        "닉네임은 2자 이상 입력해주세요.",
                        "공백과 특수문자는 사용할 수 없습니다.")
                .doesNotContain("userId=");
    }

    @Test
    void registrationShowsTheSamePolicyAndKeepsItsClientValidationAligned() throws IOException {
        String template = read("templates/register.html");
        String sharedScript = read("static/js/nickname-availability.js");

        assertThat(template).contains(
                "maxlength=\"12\"", FORMAT_GUIDANCE, POLICY_GUIDANCE,
                "/js/nickname-availability.js");
        assertThat(sharedScript).contains(
                "const nicknamePattern = /^[가-힣A-Za-z0-9]{2,12}$/;",
                "공백·특수문자 및 부적절한 표현은 사용할 수 없습니다.",
                "response.status === \"FORBIDDEN\"",
                "사용할 수 없는 닉네임입니다.");
    }

    @Test
    void socialSignupUsesTheRegistrationNicknameAvailabilityAndRecommendationContract()
            throws IOException {
        String template = read("templates/social-signup.html");
        String sharedScript = read("static/js/nickname-availability.js");
        String stylesheet = read("static/css/login.css");

        assertThat(template).contains(
                "id=\"nickname\"",
                "id=\"generateNickname\"",
                "id=\"nicknameMessage\"",
                "aria-live=\"polite\"",
                "/js/nickname-availability.js");
        assertThat(sharedScript).contains(
                "/api/users/check-nickname",
                "/api/users/generate-nickname",
                "사용 가능한 닉네임입니다.",
                "이미 사용 중인 닉네임입니다.",
                "사용할 수 없는 닉네임입니다.");
        assertThat(stylesheet).contains(
                ".social-signup__nickname-row",
                "align-items: center;",
                "gap: 8px;",
                ".social-signup__recommend-button",
                "box-sizing: border-box;",
                ".social-signup__field-feedback",
                "text-align: left;",
                ".social-signup .login-field",
                "margin-bottom: 12px;",
                ".social-signup__consents",
                "margin: 0 0 18px;");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(RESOURCES.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
