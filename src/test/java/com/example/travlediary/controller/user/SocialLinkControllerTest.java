package com.example.travlediary.controller.user;

import com.example.travlediary.model.PendingSocialLink;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.SocialAccountService;
import com.example.travlediary.service.user.SocialConnectionResult;
import com.example.travlediary.service.user.SocialSignupAuthenticationException;
import com.example.travlediary.service.user.SocialSignupAuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ConcurrentModel;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialLinkControllerTest {

    private static final String FLOW_ID = "link-flow";
    private static final String EMAIL = "member@example.com";

    @Mock
    private SocialAccountService socialAccountService;
    @Mock
    private SocialSignupAuthenticationService authenticationService;
    @Mock
    private UserMapper userMapper;

    private SocialLinkController controller;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messages.setFallbackToSystemLocale(false);
        controller = new SocialLinkController(
                socialAccountService, authenticationService, userMapper, messages);
    }

    /** 확인 화면은 계정 열거에 쓰일 정보를 내려주지 않는다. */
    @Test
    void confirmationPageShowsOnlyTheBrandNameAndAMaskedEmail() {
        MockHttpSession session = sessionWith(pending());
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.linkPage(null, session, model);

        assertThat(view).isEqualTo("social-link");
        assertThat(model.getAttribute("provider")).isEqualTo(SocialProvider.GOOGLE);
        assertThat(model.getAttribute("providerDisplayName")).isEqualTo("Google");
        assertThat(model.getAttribute("maskedEmail")).isEqualTo("mem***@example.com");
        assertThat(model.getAttribute("flowId")).isEqualTo(FLOW_ID);
        assertThat(model.asMap())
                .doesNotContainKeys("providerUserId", "targetUserId", "email", "username");
    }

    @Test
    void confirmingTheLinkReusesTheExistingConnectServiceAndSignsIntoTheExistingAccount()
            throws Exception {
        MockHttpServletRequest request = requestWith(pending());
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(userMapper.findById(17L)).thenReturn(existingUser(UserStatus.ACTIVE));
        when(socialAccountService.connectToUser(
                17L, SocialProvider.GOOGLE, "google-sub", EMAIL, Boolean.TRUE))
                .thenReturn(SocialConnectionResult.CONNECTED);

        String view = controller.confirmLink(FLOW_ID, null, request, response);

        assertThat(view).isNull();
        verify(authenticationService).authenticateExistingMember(17L, request, response);
        assertThat(request.getSession().getAttribute(
                PendingSocialLink.SESSION_ATTRIBUTE)).isNull();
        verify(userMapper, never()).insertUser(any());
    }

    /** 이미 붙어 있던 경우도 성공으로 본다. 새 users 를 만들지 않는다. */
    @Test
    void anAlreadyConnectedProviderStillSignsIntoTheExistingAccount() throws Exception {
        MockHttpServletRequest request = requestWith(pending());
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(userMapper.findById(17L)).thenReturn(existingUser(UserStatus.ACTIVE));
        when(socialAccountService.connectToUser(
                anyLong(), any(), any(), any(), any()))
                .thenReturn(SocialConnectionResult.ALREADY_CONNECTED);

        String view = controller.confirmLink(FLOW_ID, null, request, response);

        assertThat(view).isNull();
        verify(authenticationService).authenticateExistingMember(17L, request, response);
    }

    /** 확인 화면을 여는 사이 그 provider 가 다른 계정에 붙었다면 로그인시키지 않는다. */
    @Test
    void aProviderTakenByAnotherAccountEndsTheFlowWithoutSigningAnyoneIn() throws Exception {
        MockHttpServletRequest request = requestWith(pending());
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(userMapper.findById(17L)).thenReturn(existingUser(UserStatus.ACTIVE));
        when(socialAccountService.connectToUser(
                anyLong(), any(), any(), any(), any()))
                .thenReturn(SocialConnectionResult.OWNED_BY_ANOTHER_USER);

        String view = controller.confirmLink(FLOW_ID, null, request, response);

        assertThat(view).isEqualTo("redirect:/login?socialLinkError=true");
        verify(authenticationService, never())
                .authenticateExistingMember(anyLong(), any(), any());
        assertThat(request.getSession().getAttribute(
                PendingSocialLink.SESSION_ATTRIBUTE)).isNull();
    }

    /** UNIQUE 경합 등 저장 실패는 안전한 위치로 보낸다. */
    @Test
    void aConnectFailureEndsTheFlowWithoutSigningAnyoneIn() throws Exception {
        MockHttpServletRequest request = requestWith(pending());
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(userMapper.findById(17L)).thenReturn(existingUser(UserStatus.ACTIVE));
        when(socialAccountService.connectToUser(
                anyLong(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("duplicate"));

        String view = controller.confirmLink(FLOW_ID, null, request, response);

        assertThat(view).isEqualTo("redirect:/login?socialLinkError=true");
        verify(authenticationService, never())
                .authenticateExistingMember(anyLong(), any(), any());
    }

    @Test
    void cancellingLinksNothingAndClearsThePendingContext() {
        MockHttpSession session = sessionWith(pending());

        String view = controller.cancelLink(session);

        assertThat(view).isEqualTo("redirect:/login?socialLinkCancelled=true");
        assertThat(session.getAttribute(PendingSocialLink.SESSION_ATTRIBUTE)).isNull();
        verify(socialAccountService, never())
                .connectToUser(anyLong(), any(), any(), any(), any());
        verify(userMapper, never()).insertUser(any());
    }

    /** flowId 가 다르면 세션 문맥이 있어도 연결하지 않는다. */
    @Test
    void aSubmittedFlowIdThatDoesNotMatchTheSessionContextCannotLink() throws Exception {
        MockHttpServletRequest request = requestWith(pending());
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller.confirmLink("other-flow", null, request, response);

        assertThat(view).isEqualTo("redirect:/login?socialSignupExpired=true");
        verify(socialAccountService, never())
                .connectToUser(anyLong(), any(), any(), any(), any());
        assertThat(request.getSession().getAttribute(
                PendingSocialLink.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    void anExpiredOrMissingPendingContextCannotLink() throws Exception {
        Instant past = Instant.now().minusSeconds(700);
        MockHttpServletRequest expired = requestWith(new PendingSocialLink(
                FLOW_ID, SocialProvider.GOOGLE, "google-sub", EMAIL, 17L,
                past, past.plusSeconds(600)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(controller.confirmLink(FLOW_ID, null, expired, response))
                .isEqualTo("redirect:/login?socialSignupExpired=true");
        assertThat(controller.confirmLink(
                FLOW_ID, null, new MockHttpServletRequest(), response))
                .isEqualTo("redirect:/login?socialSignupExpired=true");
        assertThat(controller.linkPage(null, new MockHttpSession(), new ConcurrentModel()))
                .isEqualTo("redirect:/login?socialSignupExpired=true");
        verify(socialAccountService, never())
                .connectToUser(anyLong(), any(), any(), any(), any());
    }

    /** 대상 계정이 더는 그 이메일을 갖고 있지 않으면 연결하지 않는다. */
    @Test
    void aTargetWhoseEmailChangedSinceTheConfirmationScreenIsNotLinked() throws Exception {
        MockHttpServletRequest request = requestWith(pending());
        MockHttpServletResponse response = new MockHttpServletResponse();
        User moved = existingUser(UserStatus.ACTIVE);
        moved.setUserEmail("withdrawn-17-abc@example.invalid");
        when(userMapper.findById(17L)).thenReturn(moved);

        String view = controller.confirmLink(FLOW_ID, null, request, response);

        assertThat(view).isEqualTo("redirect:/login?socialLinkError=true");
        verify(socialAccountService, never())
                .connectToUser(anyLong(), any(), any(), any(), any());
    }

    /** 확인 화면을 여는 사이 대상 계정이 탈퇴·휴면 상태로 바뀌면 연결하지 않는다. */
    @ParameterizedTest
    @EnumSource(value = UserStatus.class,
            names = {"INACTIVE", "SUSPENDED", "WITHDRAWAL_PENDING", "DEACTIVATED"})
    void aTargetThatBecameUnusableSinceTheConfirmationScreenIsNotLinked(UserStatus status)
            throws Exception {
        MockHttpServletRequest request = requestWith(pending());
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(userMapper.findById(17L)).thenReturn(existingUser(status));

        String view = controller.confirmLink(FLOW_ID, null, request, response);

        assertThat(view).isEqualTo("redirect:/login?socialLinkError=true");
        verify(socialAccountService, never())
                .connectToUser(anyLong(), any(), any(), any(), any());
    }

    @Test
    void aTargetThatDisappearedIsNotLinked() throws Exception {
        MockHttpServletRequest request = requestWith(pending());
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(userMapper.findById(17L)).thenReturn(null);

        String view = controller.confirmLink(FLOW_ID, null, request, response);

        assertThat(view).isEqualTo("redirect:/login?socialLinkError=true");
        verify(socialAccountService, never())
                .connectToUser(anyLong(), any(), any(), any(), any());
    }

    /** 연결은 됐지만 로그인에 실패하면 안내만 하고 흐름을 끝낸다. */
    @Test
    void aFailedSignInAfterLinkingSendsTheUserBackToLogin() throws Exception {
        MockHttpServletRequest request = requestWith(pending());
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(userMapper.findById(17L)).thenReturn(existingUser(UserStatus.ACTIVE));
        when(socialAccountService.connectToUser(
                anyLong(), any(), any(), any(), any()))
                .thenReturn(SocialConnectionResult.CONNECTED);
        doThrow(new SocialSignupAuthenticationException("failed"))
                .when(authenticationService)
                .authenticateExistingMember(anyLong(), any(), any());

        String view = controller.confirmLink(FLOW_ID, null, request, response);

        assertThat(view).isEqualTo("redirect:/login?socialSignupError=true");
    }

    /** 이미 로그인한 회원은 이 화면을 쓰지 않는다. */
    @Test
    void anAlreadySignedInMemberIsSentHomeWithoutTouchingTheFlow() throws Exception {
        MockHttpServletRequest request = requestWith(pending());
        MockHttpServletResponse response = new MockHttpServletResponse();
        User member = existingUser(UserStatus.ACTIVE);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                new CustomUserDetails(member), null, java.util.List.of());

        assertThat(controller.confirmLink(FLOW_ID, authentication, request, response))
                .isEqualTo("redirect:/");
        assertThat(controller.linkPage(
                authentication, new MockHttpSession(), new ConcurrentModel()))
                .isEqualTo("redirect:/");
        verify(socialAccountService, never())
                .connectToUser(anyLong(), any(), any(), any(), any());
    }

    private PendingSocialLink pending() {
        Instant now = Instant.now();
        return new PendingSocialLink(
                FLOW_ID, SocialProvider.GOOGLE, "google-sub", EMAIL, 17L,
                now.minusSeconds(10), now.plusSeconds(590));
    }

    private MockHttpSession sessionWith(PendingSocialLink pending) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingSocialLink.SESSION_ATTRIBUTE, pending);
        return session;
    }

    private MockHttpServletRequest requestWith(PendingSocialLink pending) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(sessionWith(pending));
        return request;
    }

    private User existingUser(UserStatus status) {
        User user = new User();
        user.setId(17L);
        user.setUserEmail(EMAIL);
        user.setNickname("기존회원");
        user.setUserRole(UserRole.USER);
        user.setStatus(status);
        return user;
    }
}
