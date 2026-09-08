package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationUiContractTest {

    @Test
    void registrationFormUsesTwoStepsAndKeepsAllAccountFieldsTogether() throws IOException {
        String template = resource("templates/register.html");

        assertThat(template)
                .contains("th:object=\"${registrationForm}\"")
                .contains("autocomplete=\"email\"")
                .contains("inputmode=\"email\"")
                .contains("data-field-error=\"userEmail\"")
                .contains("id=\"emailSuggestion\"")
                .contains("id=\"emailDomainSuggestions\"")
                .contains("aria-autocomplete=\"list\"")
                .contains("class=\"registration-progress\"")
                .contains("data-step-indicator=\"1\"")
                .contains("data-step-indicator=\"2\"")
                .contains("class=\"button-secondary prev-step\"")
                .contains("id=\"nickname\"", "id=\"step2-submit\"")
                .doesNotContain("th:object=\"${user}\"", "*{fullName}", "*{userPhone}",
                        "*{userBirth}", "id=\"fullName\"", "id=\"userPhone\"",
                        "id=\"userBirth\"", "data-step-indicator=\"3\"",
                        "id=\"step-3\"", "profileImageFile",
                        "enctype=\"multipart/form-data\"", "step3-submit");

        assertThat(template.indexOf("id=\"step-2\""))
                .isLessThan(template.indexOf("id=\"nickname\""));

        assertThat(Arrays.stream(
                        com.example.travlediary.dto.RegistrationForm.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain("fullName", "userPhone", "userBirth", "profileImageFile");

        String mapper = resource("mapper/UserMapper.xml");
        String insert = mapper.substring(
                mapper.indexOf("<insert id=\"insertUser\""), mapper.indexOf("</insert>"));
        assertThat(insert).doesNotContain(
                "full_name", "user_phone", "user_birth",
                "#{fullName}", "#{userPhone}", "#{userBirth}");
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
                .contains("let isSubmitting = false")
                .contains("if (isSubmitting)")
                .contains("isSubmitting = true")
                .contains("!$(serverErrorSelectors[field]).length")
                .contains("!availability.username || !availability.email || !availability.nickname");
        assertThat(javascript).doesNotContain(
                "fullNamePattern", "#fullName", "#userPhone", "#userBirth",
                "step-3", "step3-submit", "3단계 중", "Math.min(3");
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

    @Test
    void twoStepProgressUsesEqualColumnsAndConnectsTheStepCircles() throws IOException {
        String stylesheet = resource("static/css/registration.css");

        assertThat(stylesheet)
                .contains("grid-template-columns: repeat(2, minmax(0, 1fr));")
                .contains("left: calc(50% + 16px);", "right: calc(-50% + 16px);")
                .doesNotContain("grid-template-columns: repeat(3, 1fr);");
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
