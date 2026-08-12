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
}
