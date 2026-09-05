package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 여행정보·축제 폼의 번역 입력 UI 규약.
 *
 * <p>두 화면이 같은 조각·같은 스크립트를 쓰고, 언어별 본문 편집기는 자리 번호가 아니라
 * languageCode 로 hidden input 과 묶인다.
 */
class AdminTravelInfoTranslationUiContractTest {

    private static final String FRAGMENT =
            "/templates/fragments/admin/travel-info-translation-tabs.html";
    private static final String TRAVEL_INFO_FORM = "/templates/admin/travel-info/form.html";
    private static final String FESTIVAL_FORM = "/templates/admin/festivals/form.html";

    @Test
    void bothAdminFormsReuseTheSharedFragmentScriptsAndStyles() throws IOException {
        for (String path : new String[]{TRAVEL_INFO_FORM, FESTIVAL_FORM}) {
            String form = resource(path);

            assertThat(form).as(path)
                    .contains("<script src=\"/js/admin-translation-tabs.js\" defer></script>")
                    .contains("<script src=\"/js/admin-translation-editors.js\" defer></script>")
                    .contains("<link rel=\"stylesheet\" href=\"/css/admin-translation-tabs.css\">")
                    .contains("fragments/admin/travel-info-translation-tabs")
                    .contains(":: travelInfoTranslations")
                    // 화면마다 탭 시스템을 새로 만들지 않는다
                    .doesNotContain("data-festival-translation")
                    .doesNotContain("data-travel-info-translation");
        }
    }

    @Test
    void onlyTheFestivalFormAsksForTheEventDetailFields() throws IOException {
        // 언어 탭 세트는 화면마다 하나뿐이고, 축제 화면만 그 안에 행사 상세정보 칸을 더 받는다.
        String festivalForm = resource(FESTIVAL_FORM);
        String travelInfoForm = resource(TRAVEL_INFO_FORM);

        assertThat(festivalForm).contains(":: travelInfoTranslations(true)");
        assertThat(count(festivalForm, ":: travelInfoTranslations")).isEqualTo(1);

        assertThat(travelInfoForm)
                .contains(":: travelInfoTranslations(false)")
                // 일반 여행정보 폼에는 행사 필드가 절대 나오면 안 된다
                .doesNotContain("festivalInfoTranslations")
                .doesNotContain("행사 상세정보");
        assertThat(count(travelInfoForm, ":: travelInfoTranslations")).isEqualTo(1);
        // 탭 골격은 공용 조각 한 곳에만 있다
        assertThat(count(resource(FRAGMENT), "data-translation-tabs")).isEqualTo(1);
    }

    @Test
    void festivalDetailFieldsSitInsideTheSameLanguagePanelAsTitleAndContent()
            throws IOException {
        String fragment = resource(FRAGMENT);
        String panel = between(fragment,
                "<div class=\"admin-translation-panel\"", "</th:block>\n  </div>");

        // 제목·본문·행사 상세정보가 같은 패널 안에 있다
        assertThat(panel)
                .contains("th:field=\"*{translations[__${slot.index}__].title}\"")
                .contains("data-translation-editor=${translation.languageCode}")
                .contains("<h4>행사 상세정보</h4>")
                .contains("th:field=\"*{festivalInfoTranslations[__${festivalSlot.index}__]"
                        + ".eventPlace}\"");
        // 축제 칸은 조건부로만 그린다
        assertThat(fragment)
                .contains("th:fragment=\"travelInfoTranslations(festivalFields)\"")
                .contains("<th:block th:if=\"${festivalFields}\">")
                // 언어 짝은 자리 번호가 아니라 languageCode 로 맞춘다
                .contains("th:if=\"${festivalTranslation.languageCode == translation.languageCode}\"");
    }

    @Test
    void everyTranslatedEventDetailFieldIsBoundAndNoUntranslatedOneIs() throws IOException {
        String fragment = resource(FRAGMENT);

        for (String field : new String[]{
                "eventPlace", "address", "playTime", "useTime", "sponsor1", "sponsor2"}) {
            assertThat(fragment).as(field)
                    .contains("th:field=\"*{festivalInfoTranslations[__${festivalSlot.index}__]."
                            + field + "}\"");
        }
        assertThat(fragment)
                .contains("th:field=\"*{festivalInfoTranslations[__${festivalSlot.index}__]"
                        + ".languageCode}\"")
                // 연락처·홈페이지·TourAPI 값은 언어와 무관하므로 번역 칸을 두지 않는다
                .doesNotContain("sponsor1Tel", "sponsor2Tel", "contactTel",
                        "homepageUrl", "ktoFestivalContentId")
                // 감출 때 입력을 비활성화하지 않는다
                .doesNotContain("disabled");
    }

    @Test
    void theTranslationBlockSitsInsideEachFormSoBindingSurvives() throws IOException {
        assertFragmentIsInsideForm(TRAVEL_INFO_FORM, "<form id=\"travel-info-form\"");
        assertFragmentIsInsideForm(FESTIVAL_FORM, "<form th:id=");
    }

    @Test
    void koreanStaysInTheBaseInputsAndGetsNoTranslationTab() throws IOException {
        String fragment = resource(FRAGMENT);

        // 0번 슬롯(한국어)은 탭에도 패널에도 그리지 않는다.
        assertThat(count(fragment, "th:unless=\"${slot.first}\"")).isEqualTo(2);
        assertThat(fragment).doesNotContain("translations[0]");

        // 두 화면의 한국어 원본 입력은 그대로 남아 있다.
        assertThat(resource(TRAVEL_INFO_FORM))
                .contains("th:field=\"*{title}\"")
                .contains("id=\"travel-info-content\" th:field=\"*{content}\"")
                .contains("id=\"travel-info-editor\"");
        assertThat(resource(FESTIVAL_FORM))
                .contains("th:field=\"*{title}\"")
                .contains("id=\"festival-content\" th:field=\"*{content}\"")
                .contains("id=\"festival-editor\"");
    }

    @Test
    void festivalFormKeepsItsExistingSectionsAlongsideTheNewTranslationBlock()
            throws IOException {
        String form = resource(FESTIVAL_FORM);

        assertThat(form)
                .contains("<h2>TourAPI에서 축제·행사 불러오기</h2>")
                .contains("<h2>목록 썸네일 선택</h2>")
                .contains("<h2>행사 소개</h2>")
                .contains("<h2>행사 상세정보</h2>")
                .contains("th:field=\"*{startDate}\"")
                .contains("th:field=\"*{endDate}\"")
                .contains("th:field=\"*{thumbnailImageId}\"")
                .contains("th:field=\"*{ktoFestivalContentId}\"")
                // 행사 상세정보 번역은 후속 단계다
                .doesNotContain("eventPlaceTranslations")
                .doesNotContain("festivalInfoTranslations");
    }

    @Test
    void everyLanguagePanelCarriesATitleInputAndItsOwnEditorBoundByLanguageCode()
            throws IOException {
        String fragment = resource(FRAGMENT);

        assertThat(fragment)
                .contains("data-translation-tabs")
                .contains("role=\"tablist\"")
                .contains("data-translation-tab=${translation.languageCode}")
                .contains("data-translation-panel=${translation.languageCode}")
                .contains("role=\"tabpanel\"")
                .contains("th:field=\"*{translations[__${slot.index}__].languageCode}\"")
                .contains("th:field=\"*{translations[__${slot.index}__].title}\"")
                .contains("th:field=\"*{translations[__${slot.index}__].content}\"")
                // 편집기·hidden input·초기값이 모두 언어 코드로 묶인다
                .contains("data-translation-editor=${translation.languageCode}")
                .contains("data-translation-content=${translation.languageCode}")
                .contains("data-translation-initial-content=${translation.languageCode}");
        // 탭과 패널은 같은 슬롯 목록을 돈다 (언어 수를 화면에 박지 않는다)
        assertThat(count(fragment, "th:each=\"translation, slot : *{translations}\"")).isEqualTo(2);
        // 감출 때 입력을 비활성화하지 않는다 (disabled 면 저장에서 빠진다)
        assertThat(fragment).doesNotContain("disabled");
        // 언어 코드를 화면에 하드코딩하지 않는다
        assertThat(fragment).doesNotContain("\"en\"", "\"ja\"", "\"zh-CN\"", "\"zh-TW\"");
    }

    @Test
    void translationEditorsAreWiredByLanguageCodeForWhicheverFormTheySitIn()
            throws IOException {
        String script = resource("/static/js/admin-translation-editors.js");

        assertThat(script)
                .contains("[data-translation-editor]")
                .contains("editorElement.dataset.translationEditor")
                .contains("editorElement.closest('form')")
                .contains("[data-translation-content=\"${languageCode}\"]")
                .contains("[data-translation-initial-content=\"${languageCode}\"]")
                .contains("{required: false}")
                // 자리 번호나 화면 이름으로 짝을 찾지 않는다
                .doesNotContain("translations[0]")
                .doesNotContain("translations[1]")
                .doesNotContain("travel-info-form")
                .doesNotContain("festival-create-form");
    }

    @Test
    void koreanBaseEditorsStayOnTheirOwnScreenScripts() throws IOException {
        assertThat(resource("/static/js/admin-travel-info-form.js"))
                .contains("'#travel-info-editor'")
                .contains("'travel-info-content'")
                .contains("'travel-info-initial-content'")
                // 언어별 편집기는 공통 스크립트가 맡는다
                .doesNotContain("data-translation-editor");
        assertThat(resource("/static/js/admin-festival-form.js"))
                .contains("getElementById('festival-editor')")
                .contains("'festival-content'")
                .contains("'festival-initial-content'")
                .doesNotContain("data-translation-editor");
        assertThat(resource("/static/js/admin-festival-autofill.js"))
                .doesNotContain("data-translation-editor");
    }

    @Test
    void theSharedEditorBindsSubmitPerEditorSoEveryLanguageIsSaved() throws IOException {
        String initializer = resource("/static/js/quill-editor-init.js");

        assertThat(initializer)
                // 폼 단위가 아니라 편집기 단위로 걸어야 편집기 여러 개가 모두 실린다
                .contains("editorElement.dataset.quillEditorSubmitBound")
                .doesNotContain("form.dataset.quillEditorSubmitBound")
                // 선택 입력 편집기는 빈 본문이어도 저장을 막지 않는다
                .contains("const required = options.required !== false;")
                .contains("if (required) {")
                // element 를 그대로 넘겨도 동작한다
                .contains("typeof editorSelector === 'string'")
                .contains("typeof contentInputId === 'string'")
                .contains("typeof initialContentId === 'string'");
    }

    @Test
    void theSharedTabScriptStillKeepsHiddenLanguagesIntact() throws IOException {
        String script = resource("/static/js/admin-translation-tabs.js");

        assertThat(script)
                .contains("panel.hidden = panel.dataset.translationPanel !== languageCode")
                .doesNotContain(".value = \"\"")
                .doesNotContain("disabled = true")
                .doesNotContain("remove()");
    }

    private void assertFragmentIsInsideForm(String path, String formOpenTag) throws IOException {
        String form = resource(path);
        int formStart = form.indexOf(formOpenTag);
        int fragment = form.indexOf("travelInfoTranslations");
        int formEnd = form.indexOf("</form>");

        assertThat(formStart).as(path).isGreaterThanOrEqualTo(0);
        assertThat(fragment).as(path).isGreaterThan(formStart);
        assertThat(fragment).as(path).isLessThan(formEnd);
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }

    private int count(String source, String token) {
        int total = 0;
        int index = source.indexOf(token);
        while (index >= 0) {
            total++;
            index = source.indexOf(token, index + token.length());
        }
        return total;
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
