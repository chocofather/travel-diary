package com.example.travlediary.repository;

import com.example.travlediary.model.DestinationType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 편의시설 등록 폼 계약.
 * 코드 하나만 받던 화면이 아이콘/번역/적용 대상까지 한 번에 받는다.
 */
class AdminAmenityCreateFormUiContractTest {

    @Test
    void createFormPostsEveryRegistrationFieldAsMultipart() throws IOException {
        String create = resource("/templates/admin/amenities/create.html");

        assertThat(create)
                .contains("th:object=\"${amenityForm}\"")
                .contains("enctype=\"multipart/form-data\"")
                .contains("th:action=\"@{/admin/amenities/create}\"")
                .contains("th:field=\"*{code}\"")
                .contains("th:field=\"*{nameKo}\"")
                .contains("th:field=\"*{nameEn}\"")
                .contains("th:field=\"*{nameJa}\"")
                .contains("th:field=\"*{nameZh}\"")
                .contains("th:field=\"*{destinationTypes}\"");
    }

    @Test
    void iconInputAcceptsRequiredPngJpegOrSvgUploads() throws IOException {
        String create = resource("/templates/admin/amenities/create.html");

        assertThat(create)
                // 파일 input 은 th:field 대신 name 으로 바인딩한다
                .contains("type=\"file\" name=\"icon\"")
                .contains("accept=\"image/png,image/jpeg,image/svg+xml,.svg\"")
                .contains("PNG, JPG 또는 SVG · 최대 512KB");
        assertThat(create).doesNotContain("th:field=\"*{icon}\"");
    }

    @Test
    void detailPageUsesIconUrlAndFallsBackToTheLegacyPngPath() throws IOException {
        String detail = resource("/templates/destination/detail.html");

        // 아이콘을 그리는 5곳 모두 iconUrl 우선 + 기존 code 기반 .png 대체
        assertThat(countOf(detail, "${#strings.isEmpty(a.iconUrl)}")).isEqualTo(5);
        assertThat(countOf(detail, "? @{'/uploads/icons/amenities/' "
                + "+ ${#strings.toLowerCase(a.code)} + '.png'}")).isEqualTo(5);
        assertThat(countOf(detail, ": @{${a.iconUrl}}")).isEqualTo(5);
        // code + .png 를 조건 없이 조립하던 옛 표현식은 남아 있지 않다
        assertThat(detail).doesNotContain(
                "th:src=\"@{'/uploads/icons/amenities/'");
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

    @Test
    void formLayoutUsesTheAmenityFormStylesheet() throws IOException {
        String create = resource("/templates/admin/amenities/create.html");
        String css = resource("/static/css/admin-amenity-form.css");

        assertThat(create)
                .contains("/css/admin-amenity-form.css")
                .contains("class=\"admin-amenity-icon-preview\"")
                .contains("class=\"admin-amenity-type-options\"")
                .contains("class=\"admin-form-actions is-centered\"");

        // 미리보기 확대
        assertThat(between(css, ".admin-amenity-icon-preview", "}"))
                .contains("width: 76px")
                .contains("height: 76px")
                .contains("object-fit: contain");
        // 적용 대상은 데스크톱 3열 그리드, 좁은 화면에서 줄어든다
        assertThat(between(css, ".admin-amenity-type-options {", "}"))
                .contains("display: grid")
                .contains("grid-template-columns: repeat(3, minmax(0, 1fr))");
        assertThat(css)
                .contains("@media (max-width: 720px)")
                .contains("grid-template-columns: repeat(2, minmax(0, 1fr))");
        // 하단 버튼 중앙 정렬
        assertThat(between(css, ".admin-form-actions.is-centered", "}"))
                .contains("justify-content: center");
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }

    @Test
    void destinationTypeCheckboxesComeFromTheEnumInsteadOfHardcodedValues() throws IOException {
        String create = resource("/templates/admin/amenities/create.html");

        assertThat(create)
                .contains("th:each=\"type : ${destinationTypes}\"")
                .contains("th:value=\"${type}\"")
                .contains("destinationTypeLabels.get(type)");
        // enum 이름을 템플릿에 하드코딩하지 않는다
        for (DestinationType type : DestinationType.values()) {
            assertThat(create).as("hardcoded %s", type)
                    .doesNotContain("\"" + type.name() + "\"");
        }
    }

    @Test
    void clientSideConstraintsMirrorTheServerRules() throws IOException {
        String create = resource("/templates/admin/amenities/create.html");

        assertThat(create)
                .contains("maxlength=\"50\" pattern=\"[A-Z0-9_]{2,50}\"")
                .contains("maxlength=\"100\"");
    }

    @Test
    void everyValidatedFieldCanShowItsOwnError() throws IOException {
        String create = resource("/templates/admin/amenities/create.html");

        for (String field : new String[]{
                "code", "nameKo", "nameEn", "nameJa", "nameZh", "destinationTypes", "icon"}) {
            assertThat(create).as("error slot for %s", field)
                    .contains("#fields.hasErrors('" + field + "')");
        }
        assertThat(create).contains("#fields.hasGlobalErrors()");
    }

    @Test
    void existingTranslationScreensAreLeftInPlace() throws IOException {
        assertThat(resource("/templates/admin/amenities/translation-create.html"))
                .contains("name=\"languageCode\"")
                .contains("name=\"name\"");
        assertThat(resource("/templates/admin/amenities/translation-list.html"))
                .contains("${translations}");
        assertThat(resource("/templates/admin/amenities/list.html"))
                .contains("/admin/amenities/${a.id}/translations");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
