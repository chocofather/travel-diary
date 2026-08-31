package com.example.travlediary.config;

import com.example.travlediary.service.diary.DiaryPinLockedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.regex.Pattern;

/**
 * 잠긴 다이어리를 주소창으로 열었을 때의 안내.
 *
 * <p>주소를 직접 치면 403 오류 화면만 뜨는 것이 아니라 책장으로 보낸 뒤 PIN 입력을 띄운다.
 * 풀고 나면 원래 열려던 자리로 이어서 간다.
 *
 * <p>바꾸는 것은 화면 이동(HTML GET)뿐이다. POST 나 fetch 요청은 예전 그대로 403 이 나간다 —
 * 잠금을 막는 힘은 그대로 두고 사람이 보는 길만 부드럽게 한다.
 *
 * <p>돌아갈 자리는 주소로 실어 나르지 않고 세션에 한 번만 담는다.
 * 게다가 /diaries/{번호} 로 시작하는 내부 경로만 담으므로 바깥 주소로 튈 길이 없다.
 */
@ControllerAdvice
public class DiaryPinLockedAdvice {

    /** 돌아갈 자리를 담아 두는 자리. 책장이 한 번 읽고 지운다. */
    public static final String PENDING_TARGET = "diaryPinPendingTarget";
    /** 담을 수 있는 주소. 내부의 그 다이어리 경로 하나뿐이다. */
    private static final Pattern INTERNAL_TARGET =
            Pattern.compile("^/diaries/\\d+(/[A-Za-z0-9/_-]*)?(\\?[A-Za-z0-9=&_%.\\-]*)?$");

    /**
     * @return 책장으로 보내는 redirect. 어느 다이어리를 풀어야 하는지만 주소에 남긴다.
     *         (그 번호는 이미 사용자 본인의 것이라 감출 값이 아니다)
     */
    @ExceptionHandler(DiaryPinLockedException.class)
    public String locked(DiaryPinLockedException exception,
                         HttpServletRequest request) throws DiaryPinLockedException {
        if (!isHtmlNavigation(request)) {
            // POST/AJAX 는 기존 정책 그대로 403 이다.
            throw exception;
        }

        String target = target(request);
        if (target != null) {
            request.getSession(true).setAttribute(PENDING_TARGET, target);
        }
        return "redirect:/diaries?locked=" + exception.getDiaryId();
    }

    /** 사람이 화면을 여는 요청인지. (fetch 로 부르는 요청과 가른다) */
    private boolean isHtmlNavigation(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        if (request.getHeader("X-Requested-With") != null) {
            return false;
        }
        String accept = request.getHeader(org.springframework.http.HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    /** 풀고 나서 돌아갈 자리. 내부 다이어리 경로가 아니면 담지 않는다. */
    private String target(HttpServletRequest request) {
        String query = request.getQueryString();
        String uri = query == null || query.isBlank()
                ? request.getRequestURI()
                : request.getRequestURI() + "?" + query;
        return INTERNAL_TARGET.matcher(uri).matches() ? uri : null;
    }

    /** 담아 둔 자리를 한 번만 꺼낸다. (읽고 나면 세션에서 지운다) */
    public static String takePendingTarget(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object target = session.getAttribute(PENDING_TARGET);
        session.removeAttribute(PENDING_TARGET);
        return target instanceof String value && INTERNAL_TARGET.matcher(value).matches()
                ? value : null;
    }
}
