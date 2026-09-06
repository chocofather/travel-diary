package com.example.travlediary.repository;

import com.example.travlediary.controller.admin.AdminTranslationLabels;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 이벤트 폼의 외국어 번역 탭 계약.
 *
 * <p>새 탭 시스템을 만들지 않고 공통 조각(/js/admin-translation-tabs.js, admin-translation-tabs.css)을
 * 그대로 쓴다. 상세 내용은 한국어 원본과 같은 textarea 로 받는다.
 */
class AdminEventTranslationUiContractTest {

    @Test
    void adminEventFormLoadsTheSharedTranslationTabAssetsAndFragment() throws IOException {
        String form = resource("/templates/admin/event/event-form.html");

        assertThat(form)
                .contains("/css/admin-translation-tabs.css")
                .contains("/js/admin-translation-tabs.js")
                .contains("fragments/admin/event-translation-tabs")
                .contains(":: eventTranslations");
        // 기존 유형별 입력 구성은 그대로 남는다.
        assertThat(form)
                .contains("<h2>인포그래픽 이미지 <span class=\"admin-required\">필수</span></h2>")
                .contains("<h2>메인 슬라이더용 대표 이미지 <span class=\"admin-optional\">선택</span></h2>")
                .contains("<h2>상세 내용 <span class=\"admin-required\">필수</span></h2>");
    }

    @Test
    void translationFragmentDrawsFourForeignTabsAndSkipsTheKoreanSlot() throws IOException {
        String fragment = resource("/templates/fragments/admin/event-translation-tabs.html");

        assertThat(fragment)
                .contains("data-translation-tabs")
                .contains("data-translation-tab=${translation.languageCode}")
                .contains("data-translation-panel=${translation.languageCode}")
                // 0번(한국어) 슬롯은 위쪽 원본 입력이 담당하므로 탭으로 그리지 않는다.
                .contains("th:unless=\"${slot.first}\"")
                .contains("translationTabLabels.get(translation.languageCode)");

        // 탭 이름은 공용 라벨을 쓴다. 관리자 화면은 한국어 고정이다.
        assertThat(AdminTranslationLabels.TAB_LABELS)
                .containsEntry("en", "영어")
                .containsEntry("ja", "일본어")
                .containsEntry("zh-CN", "간체")
                .containsEntry("zh-TW", "번체")
                .doesNotContainKey("ko");
    }

    @Test
    void translationPanelTakesTitleAndDescriptionWithTheSameInputKindAsTheBaseForm()
            throws IOException {
        String fragment = resource("/templates/fragments/admin/event-translation-tabs.html");

        assertThat(fragment)
                .contains("*{translations[__${slot.index}__].languageCode}")
                .contains("*{translations[__${slot.index}__].title}")
                .contains("*{translations[__${slot.index}__].description}")
                // 본문은 원본과 같은 textarea 다. Quill 을 새로 들이지 않는다.
                .contains("<textarea")
                .doesNotContain("quill-editor");
    }

    @Test
    void translationPanelOffersAPosterUploadPreviewAndRemoveForEachLanguage() throws IOException {
        String fragment = resource("/templates/fragments/admin/event-translation-tabs.html");

        assertThat(fragment)
                .contains("인포그래픽 포스터")
                // 파일 칸은 언어 슬롯 이름으로 묶고, 삭제는 체크박스로 받는다.
                .contains("${'translations[' + slot.index + '].posterFile'}")
                .contains("type=\"file\" accept=\"image/*\"")
                .contains("*{translations[__${slot.index}__].removePoster}")
                // 미리보기는 한국어 포스터 칸과 같은 구성을 쓴다.
                .contains("class=\"admin-event-image-preview is-poster\"")
                .contains("event-translation-poster-preview-")
                .contains("event-translation-poster-empty-")
                .contains("/images/default.png");
    }

    @Test
    void storedPosterPathsComeFromTheServerNeverFromTheSubmittedForm() throws IOException {
        String fragment = resource("/templates/fragments/admin/event-translation-tabs.html");

        // 경로는 서버가 언어 코드로 내려보낸 값만 읽는다. 슬롯 번호나 폼 값에 기대지 않는다.
        assertThat(fragment)
                .contains("translationPosterImages.get(translation.languageCode)")
                .doesNotContain("*{translations[__${slot.index}__].posterImg}")
                .doesNotContain("type=\"hidden\" th:field=\"*{translations[__${slot.index}__]"
                        + ".posterImg}\"");
    }

    @Test
    void foreignPosterFieldsHideWithTheKoreanPosterWhenTheEventIsStandard() throws IOException {
        String fragment = resource("/templates/fragments/admin/event-translation-tabs.html");
        String script = resource("/static/js/admin-event-form.js");

        assertThat(fragment)
                .contains("data-event-panel=\"translationPoster\"")
                .contains("th:hidden=\"${isStandard}\"");
        // 유형을 바꿔도 값은 지우지 않고 보이기만 감춘다. (기존 패널 정책과 같다)
        assertThat(script)
                .contains("translationPoster: !isStandard")
                .contains("data-translation-poster-input")
                .contains("event-translation-poster-preview-")
                .contains("event-translation-poster-remove-");
    }

    @Test
    void adminEventListStillShowsTheKoreanBaseTitle() throws IOException {
        String list = resource("/templates/admin/event/event-list.html");

        assertThat(list)
                .contains("th:text=\"${event.title}\"")
                .doesNotContain("translation");
    }

    @Test
    void publicEventTemplatesAreLeftOnTheBaseValuesForNow() throws IOException {
        String publicList = resource("/templates/event/event-list.html");
        String publicDetail = resource("/templates/event/event-detail.html");

        assertThat(publicList)
                .contains("th:text=\"${event.title}\"")
                .doesNotContain("translation");
        assertThat(publicDetail)
                .contains("th:text=\"${event.title}\"")
                .doesNotContain("translation");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
