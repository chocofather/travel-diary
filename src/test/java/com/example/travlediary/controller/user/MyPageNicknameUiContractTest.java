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
        String script = read("static/js/register.js");

        assertThat(template).contains("maxlength=\"12\"", FORMAT_GUIDANCE, POLICY_GUIDANCE);
        assertThat(script).contains(
                "const nicknamePattern = /^[가-힣A-Za-z0-9]{2,12}$/;",
                "공백·특수문자 및 부적절한 표현은 사용할 수 없습니다.",
                "res.status === \"FORBIDDEN\"",
                "사용할 수 없는 닉네임입니다.");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(RESOURCES.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
