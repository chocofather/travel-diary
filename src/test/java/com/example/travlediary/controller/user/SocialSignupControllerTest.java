package com.example.travlediary.controller.user;

import com.example.travlediary.dto.SocialSignupForm;
import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.SocialSignupAuthenticationService;
import com.example.travlediary.service.user.SocialSignupFlowException;
import com.example.travlediary.service.user.SocialSignupPersistenceException;
import com.example.travlediary.service.user.SocialSignupService;
import com.example.travlediary.service.user.SocialSignupValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ConcurrentModel;
import org.springframework.validation.BeanPropertyBindingResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialSignupControllerTest {

    @Mock
    private SocialSignupService socialSignupService;
    @Mock
    private SocialSignupAuthenticationService authenticationService;

    private SocialSignupController controller;

    @BeforeEach
    void setUp() {
        controller = new SocialSignupController(socialSignupService, authenticationService);
    }

    @Test
    void validPendingSignupShowsCompletionFormWithoutProviderIdentityInModel() {
        MockHttpSession session = sessionWith(pending(
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(590)));
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.signupPage(null, session, model);

        assertThat(view).isEqualTo("social-signup");
        assertThat(model.getAttribute("provider")).isEqualTo(SocialProvider.GOOGLE);
        assertThat(model.getAttribute("providerEmail")).isEqualTo("new@example.com");
        assertThat(model.getAttribute("socialSignupForm")).isInstanceOf(SocialSignupForm.class);
        assertThat(model.asMap()).doesNotContainKeys("providerUserId", "flowId");
    }

    @Test
    void validKakaoPendingWithoutEmailUsesTheSameCompletionForm() {
        Instant now = Instant.now();
        PendingSocialSignup kakaoPending = new PendingSocialSignup(
                "kakao-flow", SocialProvider.KAKAO, "kakao-sub",
                null, null, now.minusSeconds(10), now.plusSeconds(590));
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.signupPage(
                null, sessionWith(kakaoPending), model);

        assertThat(view).isEqualTo("social-signup");
        assertThat(model.getAttribute("provider")).isEqualTo(SocialProvider.KAKAO);
        assertThat(model.getAttribute("providerEmail")).isNull();
        assertThat(model.getAttribute("providerDisplayName")).isEqualTo("카카오");
        assertThat(model.asMap()).doesNotContainKeys("providerUserId", "flowId");
    }

    @Test
    void validNaverPendingUsesTheSameCompletionForm() {
        Instant now = Instant.now();
        PendingSocialSignup naverPending = new PendingSocialSignup(
                "naver-flow", SocialProvider.NAVER, "naver-id",
                "naver@example.com", null,
                now.minusSeconds(10), now.plusSeconds(590));
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.signupPage(
                null, sessionWith(naverPending), model);

        assertThat(view).isEqualTo("social-signup");
        assertThat(model.getAttribute("provider")).isEqualTo(SocialProvider.NAVER);
        assertThat(model.getAttribute("providerEmail")).isEqualTo("naver@example.com");
        assertThat(model.getAttribute("providerDisplayName")).isEqualTo("네이버");
        assertThat(model.asMap()).doesNotContainKeys("providerUserId", "flowId");
    }

    @Test
    void expiredOrMissingPendingIsRemovedAndReturnsToLogin() {
        MockHttpSession expiredSession = sessionWith(pending(
                Instant.now().minusSeconds(700), Instant.now().minusSeconds(100)));

        assertThat(controller.signupPage(null, expiredSession, new ConcurrentModel()))
                .isEqualTo("redirect:/login?socialSignupExpired=true");
        assertThat(expiredSession.getAttribute(PendingSocialSignup.SESSION_ATTRIBUTE)).isNull();
        assertThat(controller.signupPage(null, new MockHttpSession(), new ConcurrentModel()))
                .isEqualTo("redirect:/login?socialSignupExpired=true");
    }

    @Test
    void malformedPendingIsRejected() {
        PendingSocialSignup malformed = new PendingSocialSignup(
                "", SocialProvider.GOOGLE, "", null, null,
                Instant.now(), Instant.now().plusSeconds(600));
        MockHttpSession session = sessionWith(malformed);

        assertThat(controller.signupPage(null, session, new ConcurrentModel()))
                .isEqualTo("redirect:/login?socialSignupExpired=true");
        assertThat(session.getAttribute(PendingSocialSignup.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    void alreadyAuthenticatedMemberDoesNotEnterSocialSignup() {
        assertThat(controller.signupPage(authentication(), new MockHttpSession(),
                new ConcurrentModel())).isEqualTo("redirect:/");
    }

    @Test
    void postUsesOnlyServerPendingAndAuthenticatesReturnedUser() throws Exception {
        PendingSocialSignup pending = pending(
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(590));
        MockHttpServletRequest request = requestWith(pending);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SocialSignupForm form = acceptedForm("새여행자");
        when(socialSignupService.complete(pending, form)).thenReturn(41L);

        String view = controller.completeSignup(
                form, binding(form), null, request, response, new ConcurrentModel());

        assertThat(view).isNull();
        verify(socialSignupService).complete(pending, form);
        verify(authenticationService).authenticate(41L, request, response);
        ArgumentCaptor<PendingSocialSignup> pendingCaptor =
                ArgumentCaptor.forClass(PendingSocialSignup.class);
        verify(socialSignupService).complete(pendingCaptor.capture(), any());
        assertThat(pendingCaptor.getValue().providerUserId()).isEqualTo("new-google-sub");
    }

    @Test
    void bindingAndServiceValidationRerenderFormAndKeepPending() throws Exception {
        PendingSocialSignup pending = pending(
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(590));
        MockHttpServletRequest request = requestWith(pending);
        SocialSignupForm form = acceptedForm("입력닉네임");
        BeanPropertyBindingResult binding = binding(form);
        binding.rejectValue("termsAccepted", "required", "서비스 이용약관에 동의해주세요.");

        String bindingView = controller.completeSignup(
                form, binding, null, request, new MockHttpServletResponse(),
                new ConcurrentModel());

        assertThat(bindingView).isEqualTo("social-signup");
        assertThat(form.getNickname()).isEqualTo("입력닉네임");
        assertThat(request.getSession().getAttribute(PendingSocialSignup.SESSION_ATTRIBUTE))
                .isSameAs(pending);
        verify(socialSignupService, never()).complete(any(), any());

        BeanPropertyBindingResult serviceBinding = binding(form);
        doThrow(new SocialSignupValidationException("nickname", "이미 사용 중인 닉네임입니다."))
                .when(socialSignupService).complete(pending, form);
        String serviceView = controller.completeSignup(
                form, serviceBinding, null, request, new MockHttpServletResponse(),
                new ConcurrentModel());
        assertThat(serviceView).isEqualTo("social-signup");
        assertThat(serviceBinding.getFieldError("nickname").getDefaultMessage())
                .isEqualTo("이미 사용 중인 닉네임입니다.");
    }

    @Test
    void reusedOrInvalidFlowIsRemovedAndReturnsToLogin() throws Exception {
        PendingSocialSignup pending = pending(
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(590));
        MockHttpServletRequest request = requestWith(pending);
        SocialSignupForm form = acceptedForm("새여행자");
        doThrow(new SocialSignupFlowException("internal flow details"))
                .when(socialSignupService).complete(pending, form);

        String view = controller.completeSignup(
                form, binding(form), null, request, new MockHttpServletResponse(),
                new ConcurrentModel());

        assertThat(view).isEqualTo("redirect:/login?socialSignupExpired=true");
        assertThat(request.getSession().getAttribute(PendingSocialSignup.SESSION_ATTRIBUTE)).isNull();
        verify(authenticationService, never()).authenticate(any(), any(), any());
    }

    @Test
    void persistenceFailureRerendersGenericErrorWithoutExposingInternalDetails()
            throws Exception {
        PendingSocialSignup pending = pending(
                Instant.now().minusSeconds(10), Instant.now().plusSeconds(590));
        MockHttpServletRequest request = requestWith(pending);
        SocialSignupForm form = acceptedForm("새여행자");
        BeanPropertyBindingResult binding = binding(form);
        doThrow(new SocialSignupPersistenceException("SQL provider_user_id secret"))
                .when(socialSignupService).complete(pending, form);

        String view = controller.completeSignup(
                form, binding, null, request, new MockHttpServletResponse(),
                new ConcurrentModel());

        assertThat(view).isEqualTo("social-signup");
        assertThat(binding.getGlobalError().getDefaultMessage())
                .isEqualTo("가입 정보를 저장하지 못했습니다. 잠시 후 다시 시도해주세요.")
                .doesNotContain("SQL", "provider_user_id");
        assertThat(request.getSession().getAttribute(PendingSocialSignup.SESSION_ATTRIBUTE))
                .isSameAs(pending);
    }

    private BeanPropertyBindingResult binding(SocialSignupForm form) {
        return new BeanPropertyBindingResult(form, "socialSignupForm");
    }

    private SocialSignupForm acceptedForm(String nickname) {
        SocialSignupForm form = new SocialSignupForm();
        form.setNickname(nickname);
        form.setTermsAccepted(true);
        form.setPrivacyAccepted(true);
        return form;
    }

    private MockHttpServletRequest requestWith(PendingSocialSignup pending) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(PendingSocialSignup.SESSION_ATTRIBUTE, pending);
        return request;
    }

    private MockHttpSession sessionWith(PendingSocialSignup pending) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingSocialSignup.SESSION_ATTRIBUTE, pending);
        return session;
    }

    private PendingSocialSignup pending(Instant createdAt, Instant expiresAt) {
        return new PendingSocialSignup(
                "flow-123", SocialProvider.GOOGLE, "new-google-sub",
                "new@example.com", true, createdAt, expiresAt);
    }

    private UsernamePasswordAuthenticationToken authentication() {
        User user = new User();
        user.setId(7L);
        user.setUsername("member");
        user.setUserRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        CustomUserDetails principal = new CustomUserDetails(user);
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
    }
}
