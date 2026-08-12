package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MyPageAccountUiContractTest {

    @Test
    void formsUseDedicatedObjectsPasswordAutocompleteAndSafeText() throws IOException {
        String verify = resource("templates/mypage/account-verify.html");
        String edit = resource("templates/mypage/account-edit.html");

        assertThat(verify)
                .contains("/mypage/account/verify-password", "autocomplete=\"current-password\"")
                .contains("navigation('account')")
                .doesNotContain("userId", "th:utext");
        assertThat(edit)
                .contains("/mypage/account/edit", "/mypage/account/password",
                        "/mypage/account/withdraw")
                .contains("autocomplete=\"new-password\"", "autocomplete=\"current-password\"")
                .contains("account.username", "account.userEmail")
                .contains("작성한 게시글, 댓글, 여행 코스와 문의 기록은 유지됩니다.")
                .contains("기존 로그인 ID는 다시 사용할 수 없습니다.")
                .doesNotContain("th:utext", "name=\"userId\"", "th:field=\"*{username}\"",
                        "th:field=\"*{userEmail}\"");
    }

    @Test
    void navigationAndMainLinkToTheAccountVerificationEntry() throws IOException {
        assertThat(resource("templates/fragments/mypage/navigation.html"))
                .contains("activeMenu == 'account'", "@{/mypage/account}")
                .doesNotContain("is-disabled\" aria-disabled=\"true\">회원정보 수정");
        assertThat(resource("templates/mypage/index.html"))
                .contains("th:href=\"@{/mypage/account}\"", "회원정보 수정")
                .doesNotContain("계정 정보 관리 기능은 준비 중입니다.");
    }

    @Test
    void securityProtectsOnlyTheFourAccountMutationsWithCsrf() throws IOException {
        String security = Files.readString(Path.of(
                "src/main/java/com/example/travlediary/config/SecurityConfig.java"),
                StandardCharsets.UTF_8);

        assertThat(security)
                .contains("^/mypage/account/verify-password$",
                        "^/mypage/account/edit$",
                        "^/mypage/account/password$",
                        "^/mypage/account/withdraw$")
                .contains("/mypage/**")
                .doesNotContain("csrf(AbstractHttpConfigurer::disable)");
    }

    private String resource(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/resources").resolve(relativePath),
                StandardCharsets.UTF_8);
    }
}
