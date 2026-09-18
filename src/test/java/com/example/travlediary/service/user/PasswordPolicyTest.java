package com.example.travlediary.service.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void acceptsTheExistingSignupPasswordPolicy() {
        assertThat(PasswordPolicy.isValid("Password!")).isTrue();
        assertThat(PasswordPolicy.isValid("abc12345@")).isTrue();
    }

    @Test
    void rejectsShortMissingSpecialAndUnsupportedCharacters() {
        for (String password : new String[]{null, "Pass!1", "Password1", "Password!한"}) {
            assertThatThrownBy(() -> PasswordPolicy.validate(password))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(PasswordPolicy.INVALID_MESSAGE);
        }
    }

    /**
     * BCrypt 가 실제로 쓰는 72바이트까지만 받는다.
     *
     * <p>더 긴 값은 예전에는 조용히 잘려 서로 다른 비밀번호가 같은 해시가 됐고, 지금 판의
     * Spring Security 에서는 해싱이 예외로 끝난다. 어느 쪽도 사용자에게 내보낼 상태가 아니라
     * 평범한 입력 오류로 먼저 막는다.
     */
    @Test
    void acceptsUpToTheBcryptLimitAndRejectsLongerInput() {
        String atLimit = "!" + "a".repeat(PasswordPolicy.MAX_LENGTH - 1);
        String overLimit = "!" + "a".repeat(PasswordPolicy.MAX_LENGTH);

        assertThat(atLimit).hasSize(PasswordPolicy.MAX_LENGTH);
        assertThat(PasswordPolicy.isValid(atLimit)).isTrue();

        assertThat(PasswordPolicy.isValid(overLimit)).isFalse();
        assertThatThrownBy(() -> PasswordPolicy.validate(overLimit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(PasswordPolicy.INVALID_MESSAGE);
    }

    /** 허용 문자가 ASCII 뿐이라 글자 수가 곧 BCrypt 가 세는 바이트 수다. */
    @Test
    void theLimitIsCountedInBytesTheSameWayBcryptDoes() {
        String atLimit = "!" + "a".repeat(PasswordPolicy.MAX_LENGTH - 1);

        assertThat(atLimit.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .hasSize(PasswordPolicy.MAX_LENGTH);
    }
}
