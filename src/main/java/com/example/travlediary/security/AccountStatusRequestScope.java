package com.example.travlediary.security;

import com.example.travlediary.model.UserStatus;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 한 HTTP 요청 안에서만 쓰고 버리는 계정 상태 자리.
 *
 * <p>격리 필터 둘이 같은 {@code users.status} 를 본다. 앞선 필터가 읽은 값을 뒤쪽이 그대로
 * 쓰게 해서 요청 한 번에 같은 SELECT 가 두 번 나가지 않도록 한다.
 *
 * <p><b>요청 밖으로 절대 나가지 않는다.</b> 세션이나 애플리케이션 캐시에 담지 않는다.
 * 계정 상태·제재·탈퇴유예는 "다음 요청부터 바로 반영된다"가 계약이라, 요청을 넘겨 들고 있으면
 * 이미 정지된 회원이 로그인 상태로 계속 돌아다닐 수 있다.
 * 값은 서블릿 컨테이너가 요청을 버릴 때 함께 사라진다.
 *
 * <p>요청 도중 상태를 바꾸는 쪽(만료된 제재 해제 등)은 반드시 {@link #clear(HttpServletRequest)}
 * 로 지워서, 뒤따르는 판정이 낡은 값을 보지 않게 한다.
 */
final class AccountStatusRequestScope {

    private static final String USER_ID_ATTRIBUTE =
            AccountStatusRequestScope.class.getName() + ".userId";
    private static final String STATUS_ATTRIBUTE =
            AccountStatusRequestScope.class.getName() + ".status";

    private AccountStatusRequestScope() {
    }

    /**
     * 이 요청에서 이미 읽어 둔 상태. 읽은 적이 없거나 다른 회원의 값이면 {@code null}.
     *
     * <p>회원 번호를 함께 확인하는 것은 한 요청 안에서 인증 주체가 바뀌는 경우
     * (로그인 직후 등) 남의 상태를 잘못 집어 오지 않게 하기 위해서다.
     */
    static UserStatus find(HttpServletRequest request, Long userId) {
        if (request == null || userId == null) {
            return null;
        }
        return userId.equals(request.getAttribute(USER_ID_ATTRIBUTE))
                ? (UserStatus) request.getAttribute(STATUS_ATTRIBUTE)
                : null;
    }

    /** 방금 읽은 상태를 이 요청에 한해 남겨 둔다. */
    static void remember(HttpServletRequest request, Long userId, UserStatus status) {
        if (request == null || userId == null || status == null) {
            return;
        }
        request.setAttribute(USER_ID_ATTRIBUTE, userId);
        request.setAttribute(STATUS_ATTRIBUTE, status);
    }

    /** 상태를 바꿨을 때 지운다. 다음 판정은 DB 에서 다시 읽는다. */
    static void clear(HttpServletRequest request) {
        if (request == null) {
            return;
        }
        request.removeAttribute(USER_ID_ATTRIBUTE);
        request.removeAttribute(STATUS_ATTRIBUTE);
    }
}
