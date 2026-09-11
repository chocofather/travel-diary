package com.example.travlediary.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

/**
 * 유예가 끝난 탈퇴 계정으로 들어온 인증 요청의 공통 마무리.
 *
 * <p>기존 계정은 이미 최종 파기됐으므로 그 세션을 그대로 들고 다니게 두면 안 된다.
 * 세션을 끊고 새로 가입할 수 있는 화면으로 보낸다. 별도의 "새 계정으로 시작" 화면은 두지 않는다.
 */
public final class ExpiredWithdrawalResponse {

    /** 안내 문구는 세션이 아니라 쿼리 파라미터로 넘긴다. 세션을 끊은 뒤라 flash 를 쓸 수 없다. */
    public static final String REGISTER_PATH = "/users/register?withdrawalExpired=true";

    private ExpiredWithdrawalResponse() {
    }

    /** 인증 세션과 로그인 쿠키를 모두 정리한다. */
    public static void endSession(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        new CookieClearingLogoutHandler("JSESSIONID").logout(request, response, authentication);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }
}
