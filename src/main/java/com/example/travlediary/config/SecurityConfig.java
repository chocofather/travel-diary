package com.example.travlediary.config;

import com.example.travlediary.security.RestrictedAccountFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomLoginSuccessHandler customLoginSuccessHandler; // ✅ 여기에 추가
    private final CustomLogoutSuccessHandler customLogoutSuccessHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 로그인 후 복귀 대상은 실제 페이지 이동 요청만 저장한다.
     * 기본 설정은 favicon·정적 리소스·/.well-known/** 같은 브라우저 보조 요청까지 저장해
     * 원래 보던 페이지의 SavedRequest 를 덮어쓴다.
     */
    @Bean
    public RequestCache navigationRequestCache() {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(new NavigationRequestMatcher());
        return requestCache;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RequestCache navigationRequestCache,
            ObjectProvider<RestrictedAccountFilter> restrictedAccountFilter) throws Exception {

        // 이용제한 회원 접근 통제. 웹 계층 테스트 슬라이스에는 빈이 없으므로 선택 주입한다.
        restrictedAccountFilter.ifAvailable(
                filter -> http.addFilterAfter(filter, AuthorizationFilter.class));

        http.requestCache(cache -> cache.requestCache(navigationRequestCache));

        http.csrf(csrf -> csrf.requireCsrfProtectionMatcher(new OrRequestMatcher(
                        new RegexRequestMatcher(
                                "^/bookmarks$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/bookmarks/destinations/[0-9]+$", HttpMethod.DELETE.name()),
                        new RegexRequestMatcher(
                                "^/bookmarks/posts/[0-9]+$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/bookmarks/posts/[0-9]+$", HttpMethod.DELETE.name()),
                        new RegexRequestMatcher(
                                "^/bookmarks/courses/[0-9]+$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/bookmarks/courses/[0-9]+$", HttpMethod.DELETE.name()),
                        new RegexRequestMatcher(
                                "^/bookmarks/travel-info/[0-9]+$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/bookmarks/travel-info/[0-9]+$", HttpMethod.DELETE.name()),
                        new RegexRequestMatcher(
                                "^/admin/notices$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/admin/notices/[0-9]+/edit$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/admin/notices/[0-9]+/delete$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/admin/faqs$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/admin/faqs/[0-9]+/edit$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/admin/faqs/[0-9]+/delete$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/support/inquiries$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/support/inquiries/[0-9]+/delete$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/support/inquiries/[0-9]+/edit$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/admin/inquiries/[0-9]+/answer$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/admin/users/[0-9]+/restrict$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/admin/users/[0-9]+/release$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/admin/appeals/[0-9]+/approve$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/admin/appeals/[0-9]+/reject$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/admin/contents/[A-Z_]+/[0-9]+/hide$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/admin/contents/[A-Z_]+/[0-9]+/restore$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/travel-plans$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/travel-plans/[0-9]+/invitations$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/travel-plans/[0-9]+/invitations/regenerate$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/travel-plans/[0-9]+/invitations/disable$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/travel-plans/[0-9]+/days/[0-9]+/items$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/travel-plans/[0-9]+/days/[0-9]+/items/[0-9]+/update$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/travel-plans/[0-9]+/days/[0-9]+/items/[0-9]+/delete$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/travel-plans/[0-9]+/days/[0-9]+/items/[0-9]+/delete-group$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/travel-plans/[0-9]+/days/[0-9]+/items/[0-9]+/alternatives$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/travel-plans/[0-9]+/days/[0-9]+/items/[0-9]+"
                                        + "/alternatives/[0-9]+/update$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/travel-plans/[0-9]+/days/[0-9]+/items/[0-9]+"
                                        + "/alternatives/[0-9]+/delete$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/travel-plans/[0-9]+/days/[0-9]+/items/[0-9]+/move-up$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/travel-plans/[0-9]+/days/[0-9]+/items/[0-9]+/move-down$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/travel-plans/[0-9]+/days/[0-9]+/items/[0-9]+/move$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/diaries$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/diaries/[0-9]+/update$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/diaries/[0-9]+/delete$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/diaries/[0-9]+/pages$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/diaries/[0-9]+/pages/[0-9]+/update$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/diaries/[0-9]+/pages/[0-9]+/delete$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/diaries/[0-9]+/pages/[0-9]+/content$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/diaries/[0-9]+/pages/[0-9]+/header$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/diaries/[0-9]+/pages/[0-9]+/elements/[0-9]+/position$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/diaries/[0-9]+/pages/[0-9]+/elements/[0-9]+/size$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/diaries/[0-9]+/pages/[0-9]+/elements/[0-9]+/rotation$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/diaries/[0-9]+/pages/[0-9]+/elements/[0-9]+/layer$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/diaries/[0-9]+/pages/[0-9]+/elements/photo$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/diaries/[0-9]+/pages/[0-9]+/elements/[0-9]+/photo/delete$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/diaries/[0-9]+/pages/[0-9]+/elements/sticker$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/diaries/[0-9]+/pages/[0-9]+/elements/[0-9]+/sticker/delete$",
                                HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/mypage/profile$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/mypage/account/verify-password$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/mypage/account/edit$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/mypage/account/password$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/mypage/account/withdraw$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/users/verification/resend$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/account/restricted/appeals$", HttpMethod.POST.name()),
                        new RegexRequestMatcher(
                                "^/logout$", HttpMethod.POST.name())
                )))
                .authorizeHttpRequests(auth -> auth

                        /* === 비회원도 접근 가능한 공개 영역 === */
                        .requestMatchers(
                                "/", "/home", "/random-travel",
                                "/login", "/logout",
                                "/register", "/users/register",
                                "/users/verify", "/users/register/verify-waiting",
                                "/users/verification/resend",
                                "/users/find-username", "/users/find-password", "/users/reset-password/**",
                                "/css/**", "/js/**", "/images/**", "/fonts/**", "/uploads/**",
                                "/api/**",     "/api/destinations/**",
                                "/search", "/search.html",
                                "/destinations/**",
                                "/category/**",
                                "/bookmarks/check",    // NEW
                                "/bookmarks/count",     // NEW
                                // ✅ 댓글 목록 및 이미지 조회는 비로그인도 접근 가능하게 설정
                                "/comments/list", "/comments/images",  "/comments/list/page",
                                "/events", "/events/**",
                                "/board/list", "/board/fragment"
                                ).permitAll()

                        // 댓글 deep-link 위치 조회는 상세페이지와 동일하게 공개 읽기만 허용
                        .requestMatchers(new RegexRequestMatcher(
                                "^/comments/[0-9]+/location$", HttpMethod.GET.name())).permitAll()

                        // 사용자 여행정보 목록 GET만 공개
                        .requestMatchers(HttpMethod.GET, "/travel-info").permitAll()

                        // 숫자 ID 사용자 여행정보 상세 GET만 공개
                        .requestMatchers(new RegexRequestMatcher(
                                "^/travel-info/[0-9]+(?:\\?.*)?$", "GET")).permitAll()

                        // 고객센터 공지사항 목록과 숫자 ID 상세 GET만 공개
                        .requestMatchers(HttpMethod.GET, "/support/notices").permitAll()
                        .requestMatchers(new RegexRequestMatcher(
                                "^/support/notices/[0-9]+$", HttpMethod.GET.name())).permitAll()

                        // 자주 묻는 질문 목록 GET만 공개
                        .requestMatchers(HttpMethod.GET, "/support/faq").permitAll()

                        // 1:1 문의는 목록·작성·상세·삭제 모두 로그인 사용자 전용
                        .requestMatchers("/support/inquiries", "/support/inquiries/**").authenticated()

                        // 게시글 일반 댓글 목록은 비회원도 조회 가능
                        .requestMatchers(HttpMethod.GET, "/post-comments", "/post-comments/page").permitAll()
                        .requestMatchers(new RegexRequestMatcher(
                                "^/post-comments/[0-9]+/location$", HttpMethod.GET.name())).permitAll()

                        // 게시글 댓글 작성·수정·삭제는 로그인 사용자만 가능
                        .requestMatchers("/post-comments", "/post-comments/**").authenticated()

                        // 여행 코스 일반 댓글 목록은 비회원도 조회 가능
                        .requestMatchers(HttpMethod.GET, "/course-comments", "/course-comments/page").permitAll()
                        .requestMatchers(new RegexRequestMatcher(
                                "^/course-comments/[0-9]+/location$", HttpMethod.GET.name())).permitAll()

                        // 여행 코스 댓글 작성·수정·삭제는 로그인 사용자만 가능
                        .requestMatchers("/course-comments", "/course-comments/**").authenticated()

                        // 숫자 ID 게시글 상세 GET만 공개 (/post/write는 일치하지 않음)
                        .requestMatchers(new RegexRequestMatcher("^/post/[0-9]+$", "GET")).permitAll()

                        // 숫자 ID 여행 코스 상세 GET만 공개 (/course/write는 일치하지 않음)
                        .requestMatchers(new RegexRequestMatcher("^/course/[0-9]+$", "GET")).permitAll()

                        // 숫자 ID 공개 회원 프로필 GET만 공개 (계정 관련 /users/** 전체는 공개하지 않음)
                        .requestMatchers(new RegexRequestMatcher("^/users/[0-9]+$", "GET")).permitAll()

                        // 초대 링크 미리보기 GET만 공개 (URL-safe Base64 토큰 한 조각)
                        // 방 관리 경로 /travel-plans/{id}/** 는 그대로 인증이 필요하다
                        .requestMatchers(new RegexRequestMatcher(
                                "^/travel-plans/invitations/[A-Za-z0-9_-]+$",
                                HttpMethod.GET.name())).permitAll()

                        /* === 관리자만 접근 가능한 영역 === */
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        /* === 로그인한 사용자만 접근 가능한 영역 === */
                        .requestMatchers(
                                "/bookmark/**",
                                "/comments/**", // 여전히 필요하지만 list/images는 위에서 permitAll 되었음
                                "/diaries/**",  // 개인 여행일기는 본인만 접근
                                "/mypage/**",
                                "/users/profile/**",
                                "/bookmarks/**"
                        ).authenticated()

                        /* === 그 외는 인증 필요 === */
                        .anyRequest().authenticated()
                )
                .formLogin(login -> login
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(customLoginSuccessHandler)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(customLogoutSuccessHandler)
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            String accept = request.getHeader("Accept");
                            if (accept != null && accept.contains("application/json")) {
                                // JS fetch 요청 등: 401 JSON 응답
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json;charset=UTF-8");
                                response.getWriter().write("{\"error\": \"Unauthorized\"}");
                            } else {
                                // 일반 브라우저 접근: 로그인 페이지로 이동
                                response.sendRedirect("/login?redirect=" + request.getRequestURI());
                            }
                        })
                );



        return http.build();
    }
}
