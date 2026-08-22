package com.example.travlediary.repository;

import com.example.travlediary.model.DestinationType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카테고리 등록 폼 계약: 이름 하나만 받던 화면이 적용 대상까지 함께 받는다.
 */
class AdminCategoryCreateFormUiContractTest {

    @Test
    void createFormBindsTheNameAndTheDestinationTypes() throws IOException {
        String create = create();

        assertThat(create)
                .contains("th:object=\"${categoryForm}\"")
                .contains("th:action=\"@{/admin/categories}\"")
                .contains("th:field=\"*{name}\"")
                // 값은 enum 에서 오고, 리스트에 직접 바인딩한다
                .contains("th:each=\"type : ${destinationTypes}\"")
                .contains("th:field=\"*{destinationTypes}\" th:value=\"${type}\"")
                .contains("destinationTypeLabels.get(type)")
                .contains("하나 이상의 적용 대상을 선택해 주세요.");

        // enum 이름을 템플릿에 하드코딩하지 않는다
        for (DestinationType type : DestinationType.values()) {
            assertThat(create).as("hardcoded %s", type).doesNotContain("\"" + type.name() + "\"");
        }
    }

    @Test
    void clientSideConstraintsStayAdvisoryAndBothFieldsCanShowErrors() throws IOException {
        String create = create();

        assertThat(create)
                .contains("required maxlength=\"100\"")
                .contains("#fields.hasErrors('name')")
                .contains("#fields.hasErrors('destinationTypes')")
                .contains("#fields.hasGlobalErrors()");
    }

    @Test
    void createFormKeepsItsCancelAndSubmitActions() throws IOException {
        String create = create();

        assertThat(create)
                .contains("class=\"admin-form-actions is-centered\"")
                .contains(">취소</a>")
                .contains("<button type=\"submit\" class=\"admin-btn is-primary\">등록</button>")
                .contains("/css/admin-category-form.css");
    }

    @Test
    void destinationTypeCheckboxesUseACompactResponsiveGrid() throws IOException {
        String css = resource("/static/css/admin-category-form.css");

        assertThat(css)
                .contains(".admin-category-type-options")
                .contains("display: grid")
                .contains("grid-template-columns: repeat(3, minmax(0, 1fr))")
                .contains("@media (max-width: 720px)")
                .contains("grid-template-columns: repeat(2, minmax(0, 1fr))")
                .contains("grid-template-columns: minmax(0, 1fr)");
    }

    @Test
    void editFormReusesTheCreateLayoutAndBindsTheSameFields() throws IOException {
        String edit = resource("/templates/admin/categories/edit.html");

        assertThat(edit)
                .contains("th:object=\"${categoryForm}\"")
                .contains("th:action=\"@{|/admin/categories/${categoryId}/edit|}\"")
                .contains("th:field=\"*{name}\"")
                // 기존 선택값은 폼의 destinationTypes 바인딩으로 체크된다
                .contains("th:each=\"type : ${destinationTypes}\"")
                .contains("th:field=\"*{destinationTypes}\" th:value=\"${type}\"")
                .contains("destinationTypeLabels.get(type)")
                // 등록 화면과 같은 compact grid / 스타일시트를 그대로 쓴다
                .contains("class=\"admin-category-type-options\"")
                .contains("/css/admin-category-form.css")
                .contains("class=\"admin-form-actions is-centered\"")
                .contains(">취소</a>")
                .contains("<button type=\"submit\" class=\"admin-btn is-primary\">수정</button>")
                .contains("required maxlength=\"100\"")
                .contains("#fields.hasErrors('name')")
                .contains("#fields.hasErrors('destinationTypes')");

        for (DestinationType type : DestinationType.values()) {
            assertThat(edit).as("hardcoded %s", type).doesNotContain("\"" + type.name() + "\"");
        }
    }

    @Test
    void listShowsTheNameTypeBadgesAndBothActions() throws IOException {
        String list = resource("/templates/admin/categories/list.html");

        // 이름이 카드의 핵심 정보이고, DB id 는 화면에서 뺐다
        assertThat(list)
                .contains("th:text=\"${cat.name}\"")
                .doesNotContain("${cat.id}\">");

        // badge 는 기존 태그 맵을 쪼개 그리고, 매핑이 없으면 미분류
        assertThat(list)
                .contains("${categoryTypeTags.get(cat.id)}")
                .contains("#strings.arraySplit(tag, ' ')")
                .contains("${destinationTypeLabelsByName.get(type)}")
                .contains(">미분류<")
                .contains("class=\"admin-category-badges\"");
        for (DestinationType type : DestinationType.values()) {
            assertThat(list).as("hardcoded %s", type).doesNotContain("\"" + type.name() + "\"");
        }

        // 기존 라우트/method 와 flash error 영역은 그대로
        assertThat(list)
                .contains("${error}")
                .contains("/admin/categories/' + ${cat.id} + '/delete")
                .contains("method=\"post\"")
                .contains("confirm('정말 삭제할까요?')")
                .contains("/admin/categories/${cat.id}/edit")
                .contains(">수정</a>")
                .contains(">삭제</button>")
                .contains("@{/admin/categories/create}");
        // 정렬/페이징은 아직 없다
        assertThat(list).doesNotContain("pagination").doesNotContain("data-category-sort");
    }

    @Test
    void listOffersATypeFilterAndANameSearchOverTheRenderedCards() throws IOException {
        String list = resource("/templates/admin/categories/list.html");

        assertThat(list)
                .contains("/js/admin-category-list-filter.js")
                .contains("data-category-filter=\"ALL\"")
                .contains(">전체</button>")
                // 유형 버튼은 enum 에서 그린다 (하드코딩 금지)
                .contains("th:attr=\"data-category-filter=${type}\"")
                .contains("th:each=\"type : ${destinationTypes}\"")
                // 카드가 자기 유형을 들고 있어야 클라이언트에서 걸러낼 수 있다
                .contains("th:attr=\"data-category-types=${tag}\"")
                .contains("data-category-card")
                .contains("data-category-name")
                // 검색 + 지우기
                .contains("data-category-search")
                .contains("data-category-search-clear")
                .contains("aria-label=\"검색어 지우기\"")
                .contains("for=\"category-search\"")
                // 결과 없음
                .contains("data-category-no-result")
                .contains("조건에 맞는 카테고리가 없습니다.");
        for (DestinationType type : DestinationType.values()) {
            assertThat(list).as("hardcoded %s", type).doesNotContain("\"" + type.name() + "\"");
        }
    }

    @Test
    void filterScriptCombinesTypeAndKeywordWithoutRebuildingTheDom() throws IOException {
        String script = resource("/static/js/admin-category-list-filter.js");

        assertThat(script)
                // 부분 문자열이 아니라 token 단위로 유형을 비교한다
                .contains("split(/\\s+/)")
                .contains("owned.includes(typeFilter)")
                // 유형 + 검색어 AND, 빈 검색어는 항상 통과
                .contains("const matches = matchesType && matchesKeyword")
                .contains("keyword === \"\"")
                .contains("toLocaleLowerCase")
                // 미분류(매핑 없음)는 ALL 에서만 통과한다
                .contains("const showAllTypes = typeFilter === \"ALL\"")
                .contains("const matchesType = showAllTypes || owned.includes(typeFilter)")
                // 결과 없음 / 개수
                .contains("noResult.hidden")
                .contains("개 카테고리")
                // 카드는 지웠다 다시 만들지 않고 hidden 으로만 제어한다
                .contains("card.hidden = !matches")
                .doesNotContain("innerHTML")
                .doesNotContain("remove()")
                .doesNotContain("appendChild");
    }

    @Test
    void hiddenCardsActuallyLeaveTheLayout() throws IOException {
        String script = resource("/static/js/admin-category-list-filter.js");
        String css = resource("/static/css/admin-compact-lists.css");

        // 숨김은 hidden 속성 한 가지 방식으로만 한다 (class 방식과 섞지 않는다)
        assertThat(script).contains("card.hidden = !matches");
        assertThat(script).doesNotContain("is-hidden").doesNotContain("style.display");

        // 카드 class 의 display:flex 가 기본 [hidden] 을 이기므로 명시적으로 되돌려야 한다
        assertThat(between(css, ".admin-category-compact-card[hidden] {", "}"))
                .contains("display: none");
        // 보이는 카드와 개수는 같은 matches 값에서 나온다
        assertThat(script)
                .contains("const matches = matchesType && matchesKeyword")
                .contains("if (matches) visible++");
    }

    @Test
    void clearingTheSearchKeepsTheSelectedTypeFilter() throws IOException {
        String script = resource("/static/js/admin-category-list-filter.js");
        int clear = script.indexOf("clearButton?.addEventListener");
        assertThat(clear).isGreaterThan(0);
        String handler = script.substring(clear);

        // 검색값만 비우고 typeFilter 는 건드리지 않는다
        assertThat(handler).contains("searchInput.value = \"\"");
        assertThat(handler).doesNotContain("typeFilter =");
    }

    @Test
    void categoryCardsStackTheirContentAndFlowResponsively() throws IOException {
        String css = resource("/static/css/admin-compact-lists.css");
        String card = between(css, ".admin-category-compact-card {", "}");
        String grid = between(css, ".admin-category-compact-grid {", "}");
        String actions = between(css, ".admin-category-compact-card .admin-action-group {", "}");

        // 이름 / badge / 액션을 세로로 쌓고 버튼은 하단 정렬
        assertThat(card).contains("display: flex").contains("flex-direction: column");
        assertThat(actions).contains("margin-top: auto");
        // 고정 열 수가 아니라 카드 최소 너비 기준으로 반응한다
        assertThat(grid).contains("repeat(auto-fill, minmax(220px, 1fr))");
        assertThat(css).contains("@media (max-width: 480px)");
        assertThat(css).contains(".admin-category-badge");
        // 도구 영역도 좁은 화면에서 wrap 된다
        assertThat(between(css, ".admin-category-toolbar {", "}"))
                .contains("flex-wrap: wrap");
        assertThat(between(css, ".admin-category-toolbar .admin-category-filters {", "}"))
                .contains("flex-wrap: wrap");
        assertThat(css).contains(".admin-category-search").contains("max-width: 280px");
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }

    private String create() throws IOException {
        return resource("/templates/admin/categories/create.html");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
