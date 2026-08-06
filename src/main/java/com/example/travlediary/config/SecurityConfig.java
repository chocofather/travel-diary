package com.example.travlediary.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth

                        /* === 비회원도 접근 가능한 공개 영역 === */
                        .requestMatchers(
                                "/", "/home",
                                "/login", "/logout",
                                "/register", "/users/register",
                                "/users/verify", "/users/register/verify-waiting",
                                "/users/find-username", "/users/find-password", "/users/reset-password/**",
                                "/css/**", "/js/**", "/images/**", "/uploads/**",
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

                        // 게시글 일반 댓글 목록은 비회원도 조회 가능
                        .requestMatchers(HttpMethod.GET, "/post-comments").permitAll()

                        // 게시글 댓글 작성·수정·삭제는 로그인 사용자만 가능
                        .requestMatchers("/post-comments", "/post-comments/**").authenticated()

                        // 숫자 ID 게시글 상세 GET만 공개 (/post/write는 일치하지 않음)
                        .requestMatchers(new RegexRequestMatcher("^/post/[0-9]+$", "GET")).permitAll()

                        /* === 관리자만 접근 가능한 영역 === */
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        /* === 로그인한 사용자만 접근 가능한 영역 === */
                        .requestMatchers(
                                "/bookmark/**",
                                "/comments/**", // 여전히 필요하지만 list/images는 위에서 permitAll 되었음
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
