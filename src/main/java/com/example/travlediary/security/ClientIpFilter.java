package com.example.travlediary.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청 하나의 클라이언트 주소를 맨 앞에서 한 번만 정해 둔다.
 *
 * <p>정해 둔 값은 요청 속성에 담기고, 이후의 필터·Controller·Service 는
 * {@link ClientIpResolver#of(HttpServletRequest)} 로 그 값을 읽는다. 요청마다 한 번만
 * 판단하므로 같은 요청 안에서 로그인 제한과 요청 제한이 서로 다른 주소를 볼 일이 없다.
 *
 * <p>이 필터를 지나지 않은 요청은 접속 주소를 그대로 쓴다. 즉 필터가 없을 때의 동작이
 * {@link ClientIpResolver.Mode#DIRECT} 와 같아서, 빠뜨려도 머리말을 믿게 되지는 않는다.
 */
public class ClientIpFilter extends OncePerRequestFilter {

    private final ClientIpResolver clientIpResolver;

    public ClientIpFilter(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        request.setAttribute(
                ClientIpResolver.REQUEST_ATTRIBUTE, clientIpResolver.resolve(request));
        filterChain.doFilter(request, response);
    }
}
