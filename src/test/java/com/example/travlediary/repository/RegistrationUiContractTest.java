package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationUiContractTest {

    @Test
    void registrationFormUsesDedicatedFieldsAndAccessibleEmailHints() throws IOException {
        String template = resource("templates/register.html");

        assertThat(template)
                .contains("th:object=\"${registrationForm}\"")
                .contains("autocomplete=\"email\"")
                .contains("inputmode=\"email\"")
                .contains("autocomplete=\"name\"")
                .contains("data-field-error=\"userEmail\"")
                .contains("id=\"emailSuggestion\"")
                .contains("id=\"emailDomainSuggestions\"")
                .contains("aria-autocomplete=\"list\"")
                .contains("class=\"registration-progress\"")
                .contains("data-step-indicator=\"1\"")
                .contains("class=\"button-secondary prev-step\"")
                .contains("class=\"button-primary\" id=\"step3-submit\"")
                .doesNotContain("th:object=\"${user}\"");
    }

    @Test
    void availabilityStateIsInvalidatedWhenAnyCheckedIdentityFieldChanges() throws IOException {
        String javascript = resource("static/js/register.js");
        String nicknameAvailability = resource("static/js/nickname-availability.js");
        String emailSuggestion = resource("static/js/email-domain-suggestion.js");
        String template = resource("templates/register.html");

        assertThat(javascript)
                .contains("username: false")
                .contains("email: false")
                .contains("nickname: false")
                .contains("invalidate(\"username\")")
                .contains("invalidate(\"email\")")
                .contains("TravelDiaryNicknameAvailability.initialize")
                .contains("TravelDiaryEmailDomain?.suggest(email)")
                .contains("TravelDiaryEmailDomain?.autocomplete(email)")
                .contains("event.key === \"ArrowDown\"")
                .contains("event.key === \"ArrowUp\"")
                .contains("event.key === \"Enter\"")
                .contains("event.key === \"Escape\"")
                .contains(".term-toggle")
                .contains("aria-expanded")
                .contains("fullNamePattern")
                .contains("let isSubmitting = false")
                .contains("if (isSubmitting)")
                .contains("isSubmitting = true")
                .contains("!$(serverErrorSelectors[field]).length")
                .contains("!availability.username || !availability.email || !availability.nickname");
        assertThat(nicknameAvailability)
                .contains("$input.on(\"input.nicknameAvailability\"")
                .contains("requestVersion += 1")
                .contains("setAvailable(false)");
        assertThat(emailSuggestion)
                .contains("[\"gamil.com\", \"gmail.com\"]")
                .contains("\"gmail.com\"")
                .contains("\"naver.com\"")
                .contains("\"daum.net\"")
                .contains("\"hanmail.net\"")
                .contains("\"kakao.com\"");
        assertThat(template).contains("/js/email-domain-suggestion.js", "/js/nickname-availability.js");
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
