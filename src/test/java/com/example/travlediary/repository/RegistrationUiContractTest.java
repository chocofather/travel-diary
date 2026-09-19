package com.example.travlediary.repository;

import com.example.travlediary.service.user.AgeVerificationPolicy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
                        "enctype=\"multipart/form-data\"", "step3-submit",
                        "*{username}", "id=\"username\"", "/api/users/check-username");

        assertThat(template.indexOf("id=\"step-2\""))
                .isLessThan(template.indexOf("id=\"nickname\""));

        assertThat(Arrays.stream(
                        com.example.travlediary.dto.RegistrationForm.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain("username", "fullName", "userPhone", "userBirth",
                        "profileImageFile");

        String mapper = resource("mapper/UserMapper.xml");
        String insert = mapper.substring(
                mapper.indexOf("<insert id=\"insertUser\""), mapper.indexOf("</insert>"));
        assertThat(insert).doesNotContain(
                "username", "#{username}",
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
                .contains("email: false")
                .contains("nickname: false")
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
                .contains("!availability.email || !availability.nickname")
                .doesNotContain("username", "check-username", "msgUsername");
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

    /**
     * 만 14세 미만은 step-2 로 넘어가지도, 소셜 가입을 제출하지도 못한다.
     * 클라이언트 검증은 안내일 뿐이라 서버 검증이 남아 있는지도 함께 본다.
     */
    @Test
    void underageBirthDatesAreBlockedBeforeTheNextStepAndBeforeSocialSubmit() throws IOException {
        String registerJs = resource("static/js/register.js");
        String socialSignupJs = resource("static/js/social-signup.js");
        String registerHtml = resource("templates/register.html");
        String socialSignupHtml = resource("templates/social-signup.html");

        // 이전 버그: checkValidity() 는 required/max 만 보고 나이는 모른다.
        assertThat(registerJs)
                .contains("function birthDateStatus()")
                .contains("window.TravelDiaryAgeEligibility.status(")
                .contains("birthDateStatus() === \"ok\"")
                .contains("$(\".next-step\").on(\"click\"")
                .doesNotContain("$(\"#birthDate\")[0].checkValidity()");
        assertThat(socialSignupJs)
                .contains("function initializeAgeCheck()")
                .contains("window.TravelDiaryAgeEligibility.status(")
                .contains("event.preventDefault()");
        // 두 화면이 각자 계산하면 경계가 갈린다. 판정은 한 파일에만 둔다.
        assertThat(registerJs + socialSignupJs).doesNotContain("year + 14", "+ 14,");
        assertThat(registerHtml).contains("/js/age-eligibility.js");
        assertThat(socialSignupHtml).contains("/js/age-eligibility.js");

        // 문구는 서버 messages 번들에서 data-* 로 내려온다. JS 에 한국어를 넣지 않는다.
        assertThat(registerJs)
                .contains("messages.msgBirthdateUnderage", "messages.msgBirthdateInvalid");
        assertThat(registerHtml)
                .contains("th:data-msg-birthdate-underage=\"#{signup.error.birthDate.underage}\"")
                .contains("th:data-msg-birthdate-invalid=\"#{signup.error.birthDate.invalid}\"");
        assertThat(socialSignupJs)
                .contains("$field.data(\"msg-underage\")", "$field.data(\"msg-invalid\")");
        assertThat(socialSignupHtml)
                .contains("th:data-msg-underage=\"#{signup.error.birthDate.underage}\"")
                .contains("th:data-msg-invalid=\"#{signup.error.birthDate.invalid}\"");

        // 최종 판정은 여전히 서버가 한다.
        assertThat(source("service/user/UserService.java"))
                .contains("AgeVerificationPolicy.verify(");
        assertThat(source("service/user/SocialSignupService.java"))
                .contains("AgeVerificationPolicy.verify(");
    }

    /**
     * 클라이언트 만 14세 경계가 서버 {@link com.example.travlediary.service.user.AgeVerificationPolicy}
     * 와 같은지 본다. 빌드에 JS 런타임이 없어 실행 대신 판정식을 고정하고,
     * 같은 경계 날짜를 서버로 함께 검증한다.
     */
    @Test
    void theClientAgeBoundaryMatchesTheServerIncludingLeapDayBirthdays() throws IOException {
        String module = resource("static/js/age-eligibility.js");
        String registerJs = resource("static/js/register.js");
        String socialSignupJs = resource("static/js/social-signup.js");
        String registerHtml = resource("templates/register.html");
        String socialSignupHtml = resource("templates/social-signup.html");

        // 14년 뒤에 같은 날짜가 없으면 그 달 말일로 당긴다. LocalDate.plusYears 와 같은 규칙이다.
        // new Date(y, month, 0) 이 그 달의 말일이다. rollover 한 3월 1일을 쓰면 안 된다.
        assertThat(module)
                .contains("const MINIMUM_AGE = 14;")
                .contains("if (shifted.getMonth() !== month - 1) {")
                .contains("return new Date(targetYear, month, 0);")
                .contains("plusYears(year, month, day, MINIMUM_AGE) > today ? \"underage\" : \"ok\"")
                .contains("if (birthDate > today || year < EARLIEST_YEAR) return \"invalid\";")
                .contains("const birthDate = parseDate(raw);");

        // 기준일은 서버가 내려준 날짜다. 브라우저 timezone 이 앞서면 가입 가능한 사용자를
        // 화면에서 하루 먼저 막게 되므로, new Date() 는 값을 못 받았을 때의 fallback 에만 둔다.
        assertThat(module)
                .contains("function resolveToday(serverToday)")
                .contains("const today = resolveToday(serverToday);");
        assertThat(registerJs)
                .contains("window.TravelDiaryAgeEligibility.status(field.val(), field.data(\"today\"))");
        assertThat(socialSignupJs)
                .contains("window.TravelDiaryAgeEligibility.status($input.val(), $input.data(\"today\"))");
        String serverToday = "th:data-today=\"${#dates.format(#dates.createNow(), 'yyyy-MM-dd')}\"";
        assertThat(registerHtml).as("일반 가입 화면이 서버 기준일을 내려보낸다").contains(serverToday);
        assertThat(socialSignupHtml).as("소셜 가입 화면도 같은 기준일을 쓴다").contains(serverToday);

        // 위 판정식이 재현해야 하는 경계. 서버 쪽 기대값을 같은 곳에 적어 둔다.
        LocalDate leapDayBirth = LocalDate.of(2012, 2, 29);
        assertThat(AgeVerificationPolicy.isOldEnough(leapDayBirth, LocalDate.of(2026, 2, 28)))
                .as("윤년 2월 29일생은 비윤년 2월 28일에 만 14세가 된다").isTrue();
        assertThat(AgeVerificationPolicy.isOldEnough(leapDayBirth, LocalDate.of(2026, 2, 27)))
                .as("그 하루 전은 아직 아니다").isFalse();

        LocalDate ordinaryBirth = LocalDate.of(2012, 5, 10);
        assertThat(AgeVerificationPolicy.isOldEnough(ordinaryBirth, LocalDate.of(2026, 5, 10)))
                .as("일반 생년월일은 14번째 생일 당일 통과").isTrue();
        assertThat(AgeVerificationPolicy.isOldEnough(ordinaryBirth, LocalDate.of(2026, 5, 9)))
                .as("일반 생년월일은 생일 하루 전 차단").isFalse();
    }

    @Test
    void twoStepProgressUsesEqualColumnsAndConnectsTheStepCircles() throws IOException {
        String stylesheet = resource("static/css/registration.css");

        assertThat(stylesheet)
                .contains("grid-template-columns: repeat(2, minmax(0, 1fr));")
                .contains("left: calc(50% + 16px);", "right: calc(-50% + 16px);")
                .doesNotContain("grid-template-columns: repeat(3, 1fr);");
    }

    private String source(String path) throws IOException {
        return java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/example/travlediary", path), StandardCharsets.UTF_8);
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
