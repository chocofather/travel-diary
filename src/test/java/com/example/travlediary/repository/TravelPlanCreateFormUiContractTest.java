package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공동 여행계획 생성 폼 계약.
 * 검증은 서버가 최종 기준이고 HTML 제약은 보조다.
 */
class TravelPlanCreateFormUiContractTest {

    @Test
    void formPostsEveryFieldTheServiceNeeds() throws IOException {
        String create = createHtml();

        assertThat(create)
                .contains("th:object=\"${travelPlanCreateForm}\"")
                .contains("th:action=\"@{/travel-plans}\"")
                .contains("method=\"post\"")
                .contains("th:field=\"*{title}\"")
                .contains("th:field=\"*{startDate}\"")
                .contains("th:field=\"*{endDate}\"")
                .contains("th:field=\"*{displayName}\"")
                .contains("<button type=\"submit\"")
                .contains("공동 여행계획 만들기");
        // 이번 단계에 없는 입력은 만들지 않는다
        assertThat(create)
                .doesNotContain("representativeImage")
                .doesNotContain("enctype");
    }

    @Test
    void clientSideConstraintsMirrorTheServerRules() throws IOException {
        String create = createHtml();

        assertThat(create)
                .contains("maxlength=\"150\"")
                .contains("maxlength=\"50\"")
                .contains("type=\"date\"");
        // 네 입력 모두 required
        assertThat(countOf(create, "required")).isGreaterThanOrEqualTo(4);
    }

    @Test
    void everyValidatedFieldCanShowItsOwnErrorNextToTheInput() throws IOException {
        String create = createHtml();

        for (String field : new String[]{"title", "startDate", "endDate", "displayName"}) {
            assertThat(create).as("error slot for %s", field)
                    .contains("#fields.hasErrors('" + field + "')")
                    .contains("th:errors=\"*{" + field + "}\"");
        }
        // 전역 오류 박스가 필드 오류를 대체하지 않는다
        assertThat(create).contains("#fields.hasGlobalErrors()");
    }

    @Test
    void formReusesTheSiteLayoutAndShowsTheSuccessMessage() throws IOException {
        String create = createHtml();

        assertThat(create)
                .contains("~{layout/main :: layout(~{::body}, ~{::headFragment})}")
                .contains("/css/travel-plan.css")
                .contains("${travelPlanMessage}")
                .contains("이 여행계획 방에서 다른 참여자에게 표시되는 이름입니다.");
    }

    @Test
    void planCreationPostIsCsrfProtectedLikeTheOtherMemberFeatures() throws IOException {
        String securityConfig = Files.readString(
                Path.of("src/main/java/com/example/travlediary/config/SecurityConfig.java"),
                StandardCharsets.UTF_8);

        // /diaries POST 와 같은 방식으로 CSRF 매처에 등록한다
        assertThat(securityConfig)
                .contains("\"^/travel-plans$\", HttpMethod.POST.name()")
                .contains("\"^/diaries$\", HttpMethod.POST.name()");
        // 공동여행용 별도 인가 규칙은 추가하지 않는다 (anyRequest().authenticated() 사용)
        assertThat(securityConfig).doesNotContain("/travel-plans/**");
    }

    private String createHtml() throws IOException {
        return resource("/templates/travelplan/create.html");
    }

    private int countOf(String source, String token) {
        int count = 0;
        int index = source.indexOf(token);
        while (index >= 0) {
            count++;
            index = source.indexOf(token, index + token.length());
        }
        return count;
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
