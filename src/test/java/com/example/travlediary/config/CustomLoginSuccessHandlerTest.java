package com.example.travlediary.config;

import com.example.travlediary.model.PendingSocialLoginLink;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.security.LoginFormState;
import com.example.travlediary.security.LoginThrottle;
import com.example.travlediary.service.user.MissingEmailRegistrationService;
import com.example.travlediary.service.user.SocialAccountService;
import com.example.travlediary.service.user.SocialEmailAccountResolver;
import com.example.travlediary.service.user.SocialConnectionResult;
import com.example.travlediary.service.user.SocialLoginLinkService;
import com.example.travlediary.service.user.WithdrawalGraceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomLoginSuccessHandlerTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private LoginThrottle loginThrottle;
    @Mock
    private WithdrawalGraceService withdrawalGraceService;
    @Mock
    private SocialAccountService socialAccountService;

    private CustomLoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CustomLoginSuccessHandler(
                userMapper, loginThrottle, withdrawalGraceService,
                        socialLoginLinkService(),
                        missingEmailRegistrationService());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulLoginClearsTheAccountFailureHistory() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(
                LoginFormState.SESSION_ATTRIBUTE,
                new LoginFormState("member", 4, null));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(7L, "member", UserRole.USER));

        verify(loginThrottle).recordSuccess("member");
        assertThat(request.getSession().getAttribute(LoginFormState.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    void restrictedMemberGoesToTheRestrictedPageBeforeAnySavedRedirect() throws Exception {
        when(userMapper.findStatusById(7L)).thenReturn(UserStatus.RESTRICTED);
        MockHttpServletRequest request = requestWithRedirect("/mypage");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(7L, "travler", UserRole.USER));

        assertThat(response.getRedirectedUrl()).isEqualTo("/account/restricted");
        assertThat(request.getSession().getAttribute("userId")).isEqualTo(7L);
        verify(userMapper, never()).findByUsername(anyString());
    }

    /**
     * 탈퇴 유예 회원은 인증만 통과한다. 저장된 요청으로 일반 서비스에 들어가지 못하고
     * 전용 안내 화면으로만 간다.
     */
    @Test
    void withdrawalPendingMemberGoesToTheNoticePageBeforeAnySavedRedirect() throws Exception {
        when(userMapper.findStatusById(7L)).thenReturn(UserStatus.WITHDRAWAL_PENDING);
        when(withdrawalGraceService.resolveAccess(7L, null))
                .thenReturn(WithdrawalGraceService.Outcome.IN_GRACE);
        MockHttpServletRequest request = requestWithRedirect("/mypage");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(7L, "travler", UserRole.USER));

        assertThat(response.getRedirectedUrl()).isEqualTo("/account/withdrawal-pending");
        assertThat(request.getSession().getAttribute("userId")).isEqualTo(7L);
    }

    /**
     * 유예가 끝난 계정으로 올바른 비밀번호를 넣은 경우.
     * 본인 확인이 끝났으므로 기존 계정을 즉시 최종 파기하고, 정상 회원 세션은 만들지 않는다.
     */
    @Test
    void anExpiredWithdrawalLoginIsFinalizedAndSentToTheSignupPage() throws Exception {
        when(userMapper.findStatusById(7L)).thenReturn(UserStatus.WITHDRAWAL_PENDING);
        when(withdrawalGraceService.resolveAccess(7L, null))
                .thenReturn(WithdrawalGraceService.Outcome.GRACE_ENDED);
        MockHttpServletRequest request = requestWithRedirect("/mypage");
        MockHttpSession session = (MockHttpSession) request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(7L, "travler", UserRole.USER));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("/users/register?withdrawalExpired=true");
        // 탈퇴 유예 화면으로도, 저장된 요청으로도 가지 않는다.
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void activeMemberKeepsTheExistingRedirectBehaviour() throws Exception {
        when(userMapper.findStatusById(7L)).thenReturn(UserStatus.ACTIVE);
        MockHttpServletRequest request = requestWithRedirect("/mypage");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(7L, "travler", UserRole.USER));

        assertThat(response.getRedirectedUrl()).isEqualTo("/mypage");
        assertThat(request.getSession().getAttribute("userId")).isEqualTo(7L);
    }

    @Test
    void adminGeneralLoginUsesNormalSiteRedirectInsteadOfForcedAdminHome() throws Exception {
        MockHttpServletRequest request = requestWithRedirect("/travel-info?sort=views");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(99L, "admin", UserRole.ADMIN));

        assertThat(response.getRedirectedUrl()).isEqualTo("/travel-info?sort=views");
        assertThat(request.getSession().getAttribute("userId")).isEqualTo(99L);
    }

    @Test
    void directAdminLoginWithoutOriginalRequestFallsBackToGeneralHome() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(99L, "admin", UserRole.ADMIN));

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void adminReturnsToSameOriginSavedAdminRequestIncludingQuery() throws Exception {
        MockHttpSession session = saveRequest("/admin/inquiries", "status=PENDING&page=2");
        MockHttpServletRequest request = requestWithRedirect("/");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(99L, "admin", UserRole.ADMIN));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("/admin/inquiries?status=PENDING&page=2");
        assertThat(new HttpSessionRequestCache().getRequest(request, response)).isNull();
    }

    @Test
    void regularUserCannotFollowSavedAdminRequestAndFallsBackToHome() throws Exception {
        MockHttpSession session = saveRequest("/admin", null);
        MockHttpServletRequest request = requestWithRedirect("/admin");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(7L, "member", UserRole.USER));

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
        assertThat(new HttpSessionRequestCache().getRequest(request, response)).isNull();
    }

    @Test
    void regularUserKeepsValidatedInternalRedirect() throws Exception {
        MockHttpServletRequest request = requestWithRedirect("/travel-info?sort=views");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(7L, "member", UserRole.USER));

        assertThat(response.getRedirectedUrl()).isEqualTo("/travel-info?sort=views");
        assertThat(request.getSession().getAttribute("userId")).isEqualTo(7L);
    }

    @Test
    void externalProtocolRelativeAndMalformedRedirectsFallBackToHome() throws Exception {
        assertRedirectFallsBack("https://evil.example/path");
        assertRedirectFallsBack("//evil.example/path");
        assertRedirectFallsBack("/\\evil.example/path");
        assertRedirectFallsBack("/%2f%2fevil.example/path");
        assertRedirectFallsBack("/%5cevil.example/path");
        assertRedirectFallsBack("/travel-info%0d%0aLocation:https://evil.example");
        assertRedirectFallsBack("not-an-internal-path");
    }

    /** 비로그인 댓글 클릭이 넘긴 상세페이지 경로로 항상 되돌아온다. */
    @Test
    void detailPagesAreRestoredAfterLogin() throws Exception {
        for (String detailPath : new String[]{"/destinations/15", "/post/13", "/course/9"}) {
            MockHttpServletRequest request = requestWithRedirect(detailPath);
            MockHttpServletResponse response = new MockHttpServletResponse();

            handler.onAuthenticationSuccess(
                    request, response, authentication(7L, "member", UserRole.USER));

            assertThat(response.getRedirectedUrl()).isEqualTo(detailPath);
        }
    }

    @Test
    void missingRedirectFallsBackToHome() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(7L, "member", UserRole.USER));

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    private void assertRedirectFallsBack(String redirect) throws Exception {
        MockHttpServletRequest request = requestWithRedirect(redirect);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
                request, response, authentication(7L, "member", UserRole.USER));

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    private MockHttpServletRequest requestWithRedirect(String redirect) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("redirect", redirect);
        return request;
    }

    private MockHttpSession saveRequest(String requestUri, String query) {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest original = new MockHttpServletRequest("GET", requestUri);
        original.setScheme("http");
        original.setServerName("localhost");
        original.setServerPort(80);
        original.setQueryString(query);
        original.setSession(session);
        new HttpSessionRequestCache().saveRequest(original, new MockHttpServletResponse());
        return session;
    }

    private UsernamePasswordAuthenticationToken authentication(Long id,
                                                               String username,
                                                               UserRole role) {
        CustomUserDetails userDetails = new CustomUserDetails(user(id, username, role));
        return new UsernamePasswordAuthenticationToken(
                userDetails, "password", userDetails.getAuthorities());
    }

    private User user(Long id, String username, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setUserRole(role);
        return user;
    }

    /** 기다리는 연결 문맥이 없으면 항상 NONE 이라 평소 로그인 흐름이 그대로 유지된다. */
    /** userMapper stub 이 false 를 주므로 기본값은 "이메일 등록 대상 아님" 이다. */
    private MissingEmailRegistrationService missingEmailRegistrationService() {
        return new MissingEmailRegistrationService(
                userMapper,
                org.mockito.Mockito.mock(SocialEmailAccountResolver.class),
                org.mockito.Mockito.mock(
                        com.example.travlediary.service.email.EmailVerificationService.class));
    }

    private SocialLoginLinkService socialLoginLinkService() {
        return new SocialLoginLinkService(userMapper, socialAccountService);
    }


    /** 기다리던 소셜 연결이 있으면 로그인 직후 붙이고 결과 화면으로 보낸다. */
    @Test
    void aPendingSocialLinkIsCompletedRightAfterLoginAndOverridesTheDestination()
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("redirect", "/mypage");
        Instant now = Instant.now();
        request.getSession().setAttribute(PendingSocialLoginLink.SESSION_ATTRIBUTE,
                new PendingSocialLoginLink("link-flow", SocialProvider.KAKAO, "kakao-sub",
                        null, null, 7L, "member@example.com",
                        now.minusSeconds(10), now.plusSeconds(590)));
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(userMapper.findStatusById(7L)).thenReturn(UserStatus.ACTIVE);
        when(userMapper.findById(7L)).thenReturn(linkTarget());
        when(socialAccountService.connectToUser(
                7L, SocialProvider.KAKAO, "kakao-sub", null, null))
                .thenReturn(SocialConnectionResult.CONNECTED);

        handler.onAuthenticationSuccess(request, response, authentication(7L));

        assertThat(response.getRedirectedUrl()).isEqualTo("/mypage/account");
        assertThat(request.getSession().getAttribute(
                PendingSocialLoginLink.SESSION_ATTRIBUTE)).isNull();
        assertThat(request.getSession().getAttribute("userId")).isEqualTo(7L);
    }

    /** 연결 대기가 없으면 평소 이동 규칙을 그대로 쓴다. */
    @Test
    void anOrdinaryLoginKeepsItsUsualRedirect() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("redirect", "/mypage");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(userMapper.findStatusById(7L)).thenReturn(UserStatus.ACTIVE);

        handler.onAuthenticationSuccess(request, response, authentication(7L));

        assertThat(response.getRedirectedUrl()).isEqualTo("/mypage");
        verify(socialAccountService, never())
                .connectToUser(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }


    /** 이메일 없이 남아 있던 예전 소셜 회원은 로그인 직후 이메일 등록 화면으로 간다. */
    @Test
    void aLegacySocialAccountWithoutAnEmailIsSentToTheEmailRegistrationScreen()
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("redirect", "/mypage");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(userMapper.findStatusById(7L)).thenReturn(UserStatus.ACTIVE);
        when(userMapper.isSocialAccountMissingEmail(7L)).thenReturn(true);

        handler.onAuthenticationSuccess(request, response, authentication(7L));

        assertThat(response.getRedirectedUrl()).isEqualTo("/account/email-required");
    }

    /** 이용제한·탈퇴 유예는 기존 격리가 먼저다. 이메일 게이트가 그 정책을 앞지르지 않는다. */
    @Test
    void theExistingStatusIsolationStillComesFirst() throws Exception {
        MockHttpServletResponse restricted = new MockHttpServletResponse();
        when(userMapper.findStatusById(7L)).thenReturn(UserStatus.RESTRICTED);
        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(), restricted, authentication(7L));
        assertThat(restricted.getRedirectedUrl()).isEqualTo("/account/restricted");

        MockHttpServletResponse withdrawing = new MockHttpServletResponse();
        when(userMapper.findStatusById(7L)).thenReturn(UserStatus.WITHDRAWAL_PENDING);
        when(withdrawalGraceService.resolveAccess(7L, null))
                .thenReturn(WithdrawalGraceService.Outcome.IN_GRACE);
        handler.onAuthenticationSuccess(
                new MockHttpServletRequest(), withdrawing, authentication(7L));
        assertThat(withdrawing.getRedirectedUrl()).isEqualTo("/account/withdrawal-pending");

        // 두 경우 모두 이메일 게이트 판정까지 가지 않는다.
        verify(userMapper, never()).isSocialAccountMissingEmail(org.mockito.ArgumentMatchers.anyLong());
    }


    /**
     * 자격증명은 맞지만 아직 이메일 인증 전인 계정.
     * 로그인 상태로 두지 않고 인증 대기 화면으로만 보낸다. 오타를 낸 사람의 복구 경로다.
     */
    @Test
    void aCredentialCheckOnAnUnverifiedAccountEndsAtTheWaitingScreenWithoutALogin()
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("redirect", "/mypage");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(userMapper.findStatusById(7L)).thenReturn(UserStatus.INACTIVE);
        User pending = new User();
        pending.setId(7L);
        pending.setUserEmail("typo@example.com");
        when(userMapper.findById(7L)).thenReturn(pending);

        handler.onAuthenticationSuccess(request, response, authentication(7L));

        assertThat(response.getRedirectedUrl()).isEqualTo("/users/register/verify-waiting");
        // 대기 화면과 이메일 변경이 쓸 세션 값만 남는다.
        assertThat(request.getSession().getAttribute("pendingVerificationEmail"))
                .isEqualTo("typo@example.com");
        // 일반 서비스에 들어갈 수 있는 인증은 남지 않는다.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getSession().getAttribute("userId")).isNull();
    }

    private UsernamePasswordAuthenticationToken authentication(long userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("member");
        user.setUserRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        CustomUserDetails principal = new CustomUserDetails(user);
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
    }

    private User linkTarget() {
        User user = new User();
        user.setId(7L);
        user.setUserEmail("member@example.com");
        user.setUserRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }

}
