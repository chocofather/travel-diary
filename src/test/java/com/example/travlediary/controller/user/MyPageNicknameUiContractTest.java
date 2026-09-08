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

        // 안내 문구는 화면이 data-* 로 내려 준다. (스크립트에 언어별 문자열을 두지 않는다)
        assertThat(template)
                .contains("maxlength=\"12\"",
                        "#{mypage.profile.nickname.help.format}",
                        "#{mypage.profile.nickname.help.forbidden}",
                        "/js/mypage-profile.js", "id=\"nickname-availability\"",
                        "id=\"profileSaveButton\"", "data-current-nickname",
                        "data-message-too-short=#{mypage.profile.nickname.client.tooShort}",
                        "data-message-invalid-chars=#{mypage.profile.nickname.client.invalidChars}",
                        "data-message-current=#{mypage.profile.nickname.status.CURRENT}",
                        "data-message-forbidden=#{mypage.profile.nickname.status.FORBIDDEN}");
        assertThat(script)
                .contains("/mypage/profile/check-nickname?nickname=",
                        "new AbortController()", "requestSequence", "}, 250)",
                        "nicknameInput.dataset.currentNickname",
                        "saveButton.disabled",
                        "case \"FORBIDDEN\"",
                        "setState(\"forbidden\"",
                        "messages.messageForbidden",
                        "messages.messageCurrent",
                        "case \"AVAILABLE\"",
                        "case \"DUPLICATE\"",
                        "messages.messageTooShort",
                        "messages.messageInvalidChars")
                .doesNotContain("userId=")
                // 한국어 문구를 스크립트에 다시 넣지 않는다
                .doesNotContain(FORMAT_GUIDANCE, POLICY_GUIDANCE,
                        "사용할 수 없는 닉네임입니다.", "현재 사용 중인 닉네임입니다.");
    }

    @Test
    void profileHidesTheNativeFilePickerButKeepsItsUploadContract() throws IOException {
        String template = read("templates/mypage/profile.html");
        String script = read("static/js/mypage-profile.js");
        String stylesheet = read("static/css/mypage-profile.css");

        assertThat(template)
                // 실제 파일 칸은 그대로 두고 화면에서만 감춘다
                .contains("class=\"mypage-file-input\"",
                        "type=\"file\"",
                        "th:field=\"*{profileImageFile}\"",
                        "accept=\"image/jpeg,image/png,image/webp\"",
                        "enctype=\"multipart/form-data\"",
                        // 선택창은 label 이 연다
                        "<label class=\"mypage-file-button\" for=\"profileImageFile\"",
                        "#{mypage.profile.image.choose}",
                        "id=\"profileImageFileName\"",
                        "aria-live=\"polite\"",
                        "th:text=\"#{mypage.profile.image.noneSelected}\"",
                        "data-message-none-selected=#{mypage.profile.image.noneSelected}",
                        // 업로드 안내는 그대로 유지한다
                        "#{mypage.profile.image.help}");
        assertThat(script)
                .contains("#profileImageFile", "#profileImageFileName",
                        "fileInput.files[0]",
                        "fileName.dataset.messageNoneSelected",
                        "addEventListener(\"change\"")
                // 파일 안내 문구도 스크립트에 두지 않는다
                .doesNotContain("파일 선택", "선택된 파일 없음",
                        "No file selected", "ファイルを選択");
        assertThat(stylesheet)
                .contains(".mypage-file-input", "clip: rect(0, 0, 0, 0)",
                        ".mypage-file-input:focus-visible + .mypage-file-button",
                        "text-overflow: ellipsis")
                // 회색 native 파일 버튼 스타일은 남기지 않는다
                .doesNotContain("input[type=\"file\"]");
    }

    @Test
    void accountBirthIsShownAsReadOnlyInformationInsteadOfAnInput() throws IOException {
        String template = read("templates/mypage/account-edit.html");

        assertThat(template)
                // 회원이 고칠 수 있는 칸이 아니다
                .doesNotContain("id=\"userBirth\"")
                .doesNotContain("*{userBirth}")
                .doesNotContain("type=\"date\"")
                // 이메일·로그인 ID 와 같은 조회 전용 스타일을 쓴다
                .contains("<dt th:text=\"#{mypage.account.edit.birth}\">",
                        "th:text=\"${account.userBirth != null} ? ${account.userBirth} : '-'\"");
        assertThat(template.split("mypage-account-readonly-list", -1).length - 1)
                .as("계정 정보와 생년월일 두 곳에서 조회 전용 목록을 쓴다").isEqualTo(2);
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
