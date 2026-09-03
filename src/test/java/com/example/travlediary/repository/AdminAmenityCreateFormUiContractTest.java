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
                // 중국어는 간체/번체 두 칸이다
                .contains("th:field=\"*{nameZhCn}\"")
                .contains("th:field=\"*{nameZhTw}\"")
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
                "code", "nameKo", "nameEn", "nameJa", "nameZhCn", "nameZhTw",
                "destinationTypes", "icon"}) {
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

    @Test
    void editFormReusesTheCreateDesignWithAReadonlyCodeAndOptionalIcon() throws IOException {
        String edit = resource("/templates/admin/amenities/edit.html");

        assertThat(edit)
                .contains("th:object=\"${amenityForm}\"")
                .contains("enctype=\"multipart/form-data\"")
                .contains("th:action=\"@{|/admin/amenities/${amenityId}/edit|}\"")
                // 등록 화면과 같은 스타일시트/레이아웃을 그대로 쓴다
                .contains("/css/admin-amenity-form.css")
                .contains("class=\"admin-amenity-type-options\"")
                .contains("class=\"admin-form-actions is-centered\"")
                // code 는 보여주기만 하고 고칠 수 없다
                .contains("th:field=\"*{code}\" readonly")
                .contains("내부 코드는 등록 후 변경할 수 없습니다.")
                // 기존 아이콘 미리보기 + 선택 교체
                .contains("th:src=\"@{${currentIconUrl}}\"")
                .contains("accept=\"image/png,image/jpeg,image/svg+xml,.svg\"")
                .contains("변경하지 않으려면 선택하지 마세요.")
                .contains("URL.revokeObjectURL")
                .contains("<button type=\"submit\" class=\"admin-btn is-primary\">수정</button>");

        // 아이콘은 필수가 아니다 (등록 화면과 다른 지점)
        int iconInput = edit.indexOf("type=\"file\" name=\"icon\"");
        assertThat(iconInput).isGreaterThan(0);
        assertThat(edit.substring(iconInput, edit.indexOf(">", iconInput)))
                .doesNotContain("required");
    }

    @Test
    void editFormRestoresTranslationsAndCheckedTypes() throws IOException {
        String edit = resource("/templates/admin/amenities/edit.html");

        assertThat(edit)
                .contains("th:field=\"*{nameKo}\"")
                .contains("th:field=\"*{nameEn}\"")
                .contains("th:field=\"*{nameJa}\"")
                // 중국어는 간체/번체 두 칸이다
                .contains("th:field=\"*{nameZhCn}\"")
                .contains("th:field=\"*{nameZhTw}\"")
                // 체크 상태는 폼의 destinationTypes 바인딩으로 복원된다
                .contains("th:each=\"type : ${destinationTypes}\"")
                .contains("th:field=\"*{destinationTypes}\" th:value=\"${type}\"");
        for (String field : new String[]{
                "nameKo", "nameEn", "nameJa", "nameZhCn", "nameZhTw", "destinationTypes", "icon"}) {
            assertThat(edit).as("error slot for %s", field)
                    .contains("#fields.hasErrors('" + field + "')");
        }
    }

    @Test
    void listShowsTheIconKoreanNameCodeAndTypeBadges() throws IOException {
        String list = resource("/templates/admin/amenities/list.html");

        // 컬럼 구성
        assertThat(list)
                .contains("<th class=\"admin-amenity-icon-cell\">아이콘</th>")
                .contains("<th>편의시설</th>")
                .contains("<th>내부 코드</th>")
                .contains("<th>적용 대상</th>")
                .contains("<th>관리</th>");

        // 아이콘: icon_url 우선, 없으면 code 기반 .png 로 대체
        assertThat(list)
                .contains("${#strings.isEmpty(a.iconUrl)}")
                .contains("? @{'/uploads/icons/amenities/' "
                        + "+ ${#strings.toLowerCase(a.code)} + '.png'}")
                .contains(": @{${a.iconUrl}}");

        // 한국어 이름 우선, 없으면 관리자가 알아볼 수 있게 표시
        assertThat(list)
                .contains("th:text=\"${a.name}\"")
                .contains("이름 미등록")
                .contains("th:text=\"${a.code}\"");

        // 적용 대상은 기존 태그 맵을 쪼개 badge 로 그리고, 매핑이 없으면 미분류
        assertThat(list)
                .contains("${amenityTypeTags.get(a.id)}")
                .contains("#strings.arraySplit(tag, ' ')")
                .contains("${destinationTypeLabelsByName.get(type)}")
                .contains(">미분류<");
        // enum 이름을 템플릿에 하드코딩하지 않는다
        for (DestinationType type : DestinationType.values()) {
            assertThat(list).as("hardcoded %s", type).doesNotContain("\"" + type.name() + "\"");
        }
    }

    @Test
    void listKeepsItsLinksAndStillHasNoDeleteAction() throws IOException {
        String list = resource("/templates/admin/amenities/list.html");

        assertThat(list)
                .contains("/admin/amenities/${a.id}/edit")
                .contains("/admin/amenities/${a.id}/translations")
                .contains("@{/admin/amenities/create}")
                .contains("/css/admin-amenity-list.css");
        // 삭제는 아직 만들지 않는다
        assertThat(list).doesNotContain("/delete").doesNotContain("삭제");
    }

    @Test
    void listStylesKeepACompactTableWithBadges() throws IOException {
        String css = resource("/static/css/admin-amenity-list.css");

        assertThat(css)
                .contains(".admin-amenity-icon-frame")
                .contains("object-fit: contain")
                .contains(".admin-amenity-code")
                .contains(".admin-amenity-badge")
                .contains("flex-wrap: wrap")
                .contains("@media (max-width: 720px)");
    }

    @Test
    void adminListQueryKeepsEveryAmenityEvenWithoutAKoreanName() throws IOException {
        String mapper = resource("/mapper/AmenityMapper.xml");
        String select = between(mapper, "<select id=\"findAdminAmenityRows\"", "</select>");

        assertThat(select)
                .contains("SELECT a.id, a.code, a.icon_url, t.name")
                .contains("FROM amenities a")
                // ko 번역이 없어도 행이 사라지지 않아야 한다
                .contains("LEFT JOIN amenity_translations t")
                .contains("ON t.amenity_id = a.id AND t.language_code = 'ko'")
                .contains("ORDER BY a.id ASC")
                .doesNotContain("INNER JOIN")
                .doesNotContain("${");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
