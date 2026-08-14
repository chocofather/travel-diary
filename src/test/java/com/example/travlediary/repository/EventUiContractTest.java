package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EventUiContractTest {

    @Test
    void publicListUsesCompactTabsSingleColumnCardsAndPosterFallback()
            throws IOException {
        String template = resource("/templates/event/event-list.html");
        String css = resource("/static/css/event.css");

        assertThat(template)
                .contains("@{/events(status='ongoing')}")
                .contains("@{/events(status='upcoming')}")
                .contains("@{/events(status='ended')}")
                .doesNotContain(">전체</a>", "selectedStatus==null")
                .contains("class=\"event-card\"")
                .contains("class=\"event-card-image\"")
                .contains("class=\"event-status-badge\"")
                // 썸네일은 event_type 기준으로 고르고, 이미지가 없으면 이미지 영역 자체를 그리지 않는다
                .contains("event.eventType.name() == 'STANDARD'")
                .contains("event.posterImg")
                .contains("th:if=\"${thumbnail != null and !#strings.isEmpty(thumbnail)}\"")
                .contains("is-text-only")
                .contains("/images/default.png")
                .contains("@{/events/{id}(id=${event.id})}")
                .contains("현재 진행 중인 이벤트가 없습니다.")
                .contains("예정된 이벤트가 없습니다.")
                .contains("종료된 이벤트가 없습니다.");

        assertThat(css)
                .contains(".event-tab a")
                .contains("min-height: 32px")
                .contains("padding: 5px 12px")
                .contains("grid-template-columns: 1fr")
                .contains(".event-card-link")
                .contains("display: flex")
                .contains("height: 124px")
                .contains("width: 176px")
                .contains("object-fit: cover")
                .contains("-webkit-line-clamp: 1")
                .contains(".event-status-badge.is-ongoing")
                .contains(".event-status-badge.is-upcoming")
                .contains(".event-status-badge.is-ended")
                .contains("@media (max-width: 700px)");
    }

    @Test
    void adminFormUsesEventTypeSectionsAndSingleLineSplitDateInputs() throws IOException {
        String form = resource("/templates/admin/event/event-form.html");
        String script = resource("/static/js/admin-event-form.js");
        String css = resource("/static/css/admin-event.css");

        assertThat(form)
                .containsOnlyOnce("<form")
                .contains("th:action=\"${formAction}\"")
                .contains("th:object=\"${eventForm}\"")
                .contains("<h2>기본 정보</h2>")
                .contains("이벤트 유형")
                .contains("value=\"INFOGRAPHIC\"")
                .contains("value=\"STANDARD\"")
                .contains("data-event-type-option")
                .contains("<h2>이벤트 기간</h2>")
                // 유형별 작성 영역과 초기 표시 상태
                .contains("data-event-panel=\"poster\"")
                .contains("data-event-panel=\"mainImage\"")
                .contains("data-event-panel=\"description\"")
                .contains("<h2>인포그래픽 이미지 <span class=\"admin-required\">필수</span></h2>")
                .contains("<h2>메인 이미지 <span class=\"admin-optional\">선택</span></h2>")
                .contains("<h2>상세 내용 <span class=\"admin-required\">필수</span></h2>")
                .contains("th:hidden=\"${isStandard}\"")
                .contains("th:hidden=\"${!isStandard}\"")
                .contains("th:hidden=\"${!isStandard and !eventForm.slide}\"")
                .contains("th:src=\"${currentEventImage}\"")
                .contains("th:src=\"${currentPosterImage}\"")
                // 이미지 업로드는 HTML 단계에서 정적 required 로 막지 않는다
                .doesNotContain("name=\"posterFile\" accept=\"image/*\" required")
                .doesNotContain("name=\"imageFile\" accept=\"image/*\" required")
                .doesNotContain("th:required=\"${!editMode}\"")
                .contains("th:field=\"*{startYear}\"")
                .contains("th:field=\"*{startMonth}\"")
                .contains("th:field=\"*{startDay}\"")
                .contains("th:field=\"*{endYear}\"")
                .contains("maxlength=\"4\"")
                .contains("maxlength=\"2\"")
                .contains("inputmode=\"numeric\"")
                .contains("class=\"admin-event-date-parts\"")
                .contains("class=\"admin-event-date-separator\"")
                .contains("th:text=\"${submitLabel}\"")
                .contains("/js/admin-event-form.js")
                .doesNotContain("type=\"date\"")
                .doesNotContain("th:field=\"*{description}\" required")
                .doesNotContain("<script>document.addEventListener");

        assertThat(script)
                .contains("URL.createObjectURL")
                .contains("URL.revokeObjectURL")
                .contains("event-image")
                .contains("event-poster")
                .contains("data-event-panel")
                .contains("event-slide")
                .contains("descriptionInput.required")
                .contains("posterInput.required")
                .contains("imageInput.required")
                .contains("data-date-part")
                .contains("replace(/\\D/g, '')")
                .contains("nextInput.focus()")
                .contains("padStart(2, '0')")
                .contains("event.key === 'Backspace'");

        assertThat(css)
                .contains(".admin-event-section[hidden]")
                .contains(".admin-event-date-parts")
                .contains("flex-wrap: nowrap")
                .contains("input[data-date-part=\"year\"]")
                .contains("width: 96px")
                .contains("input[data-date-part=\"month\"]")
                .contains("width: 56px")
                .contains(".admin-event-period-grid")
                .contains("grid-template-columns: repeat(2, minmax(0, 1fr))")
                .contains("@media (max-width: 700px)");
    }

    @Test
    void adminListShowsStatusSlideBadgesAndKeepsCrudActions() throws IOException {
        String list = resource("/templates/admin/event/event-list.html");

        assertThat(list)
                .contains("class=\"admin-event-item\"")
                .contains("event.eventType.name() == 'STANDARD'")
                .contains("event.eventImg", "event.posterImg", "/images/default.png")
                .contains("admin-event-status-badge")
                .contains("진행중", "진행예정", "종료")
                .contains("admin-event-slide-badge")
                .contains("노출", "미노출")
                .contains("@{/admin/event/new}")
                .contains("@{/admin/event/{id}/edit(id=${event.id})}")
                .contains(">수정</a>")
                .contains("@{/admin/event/{id}/delete(id=${event.id})}")
                .contains(">삭제</button>");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
