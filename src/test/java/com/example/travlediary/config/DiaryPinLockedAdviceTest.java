package com.example.travlediary.config;

import com.example.travlediary.service.diary.DiaryPinLockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 잠긴 다이어리를 주소창으로 열었을 때의 안내.
 *
 * <p>바꾸는 것은 사람이 보는 화면 이동뿐이다. POST 나 fetch 는 예전처럼 403 이 나가야 한다.
 * 돌아갈 자리는 내부 다이어리 경로만 세션에 담으므로 바깥 주소로 튈 길이 없다.
 */
class DiaryPinLockedAdviceTest {

    private DiaryPinLockedAdvice advice;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        advice = new DiaryPinLockedAdvice();
        request = new MockHttpServletRequest();
    }

    /** 주소창으로 연 화면은 책장으로 보내고, 돌아갈 자리를 세션에 담는다. */
    @Test
    void anHtmlNavigationIsSentToTheShelfWithTheDiaryToUnlock() throws Exception {
        htmlGet("/diaries/15/edit", null);

        String view = advice.locked(new DiaryPinLockedException(15L), request);

        assertThat(view).isEqualTo("redirect:/diaries?locked=15");
        assertThat(request.getSession(false)
                .getAttribute(DiaryPinLockedAdvice.PENDING_TARGET)).isEqualTo("/diaries/15/edit");
    }

    /** 물음표가 붙은 주소도 그대로 이어서 간다. */
    @Test
    void theQueryStringIsKeptInTheTarget() throws Exception {
        htmlGet("/diaries/15", "edit=true&page=2");

        advice.locked(new DiaryPinLockedException(15L), request);

        assertThat(request.getSession(false).getAttribute(DiaryPinLockedAdvice.PENDING_TARGET))
                .isEqualTo("/diaries/15?edit=true&page=2");
    }

    /** POST 는 화면 이동이 아니다. 기존 403 정책 그대로 다시 던진다. */
    @Test
    void aPostKeepsTheForbiddenPolicy() {
        request.setMethod("POST");
        request.setRequestURI("/diaries/15/delete");
        request.addHeader(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE);

        assertThatThrownBy(() -> advice.locked(new DiaryPinLockedException(15L), request))
                .isInstanceOf(DiaryPinLockedException.class);
    }

    /** fetch 로 부르는 요청도 화면 이동이 아니다. */
    @Test
    void anAjaxRequestKeepsTheForbiddenPolicy() {
        htmlGet("/diaries/15", null);
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        assertThatThrownBy(() -> advice.locked(new DiaryPinLockedException(15L), request))
                .isInstanceOf(DiaryPinLockedException.class);
    }

    /** 돌아갈 자리는 내부 다이어리 경로만 담는다. */
    @Test
    void onlyAnInternalDiaryPathIsKept() {
        assertThat(DiaryPinLockedAdvice.takePendingTarget(session("/diaries/15/edit")))
                .isEqualTo("/diaries/15/edit");
        // 바깥 주소나 다른 화면은 담기지 않는다
        assertThat(DiaryPinLockedAdvice.takePendingTarget(session("https://evil.example/steal")))
                .isNull();
        assertThat(DiaryPinLockedAdvice.takePendingTarget(session("//evil.example"))).isNull();
        assertThat(DiaryPinLockedAdvice.takePendingTarget(session("/mypage/profile"))).isNull();
    }

    /** 한 번 꺼내면 세션에서 사라진다. (다음 요청에 다시 튀어나오지 않는다) */
    @Test
    void thePendingTargetIsReadOnlyOnce() {
        var session = session("/diaries/15/edit");

        assertThat(DiaryPinLockedAdvice.takePendingTarget(session)).isNotNull();
        assertThat(DiaryPinLockedAdvice.takePendingTarget(session)).isNull();
    }

    private void htmlGet(String uri, String query) {
        request.setMethod("GET");
        request.setRequestURI(uri);
        request.setQueryString(query);
        request.addHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml");
    }

    private org.springframework.mock.web.MockHttpSession session(String target) {
        var session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(DiaryPinLockedAdvice.PENDING_TARGET, target);
        return session;
    }
}
