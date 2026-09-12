package com.example.travlediary.controller.user;

import com.example.travlediary.model.PendingEmailCorrection;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.EmailCorrectionService;
import com.example.travlediary.service.user.MissingEmailRegistrationService;
import com.example.travlediary.service.user.MissingEmailRegistrationService.EmailAvailability;
import com.example.travlediary.service.user.MissingEmailRegistrationService.Outcome;
import com.example.travlediary.service.user.MissingEmailRegistrationService.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountEmailRequiredControllerTest {

    private static final String EMAIL = "member@example.com";

    @Mock
    private MissingEmailRegistrationService missingEmailRegistrationService;
    @Mock
    private EmailCorrectionService emailCorrectionService;

    private AccountEmailRequiredController controller;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messages.setFallbackToSystemLocale(false);
        controller = new AccountEmailRequiredController(
                missingEmailRegistrationService, emailCorrectionService, messages);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void onlyTargetMembersSeeTheEmailRegistrationScreen() {
        when(missingEmailRegistrationService.requiresEmailRegistration(7L))
                .thenReturn(true, false);

        assertThat(controller.emailRequiredPage(userDetails(), new ConcurrentModel()))
                .isEqualTo("account/email-required");
        assertThat(controller.emailRequiredPage(userDetails(), new ConcurrentModel()))
                .isEqualTo("redirect:/");
        assertThat(controller.emailRequiredPage(null, new ConcurrentModel()))
                .isEqualTo("redirect:/");
    }

    /** 상태 확인은 이 화면을 거쳐야 하는 회원에게만 답한다. */
    @Test
    void theEmailStatusEndpointAnswersOnlyTargetMembers() {
        when(missingEmailRegistrationService.requiresEmailRegistration(7L))
                .thenReturn(true, true, false);
        when(missingEmailRegistrationService.checkAvailability(EMAIL))
                .thenReturn(EmailAvailability.AVAILABLE);

        assertThat(controller.checkEmailStatus(EMAIL, userDetails()))
                .containsEntry("status", "AVAILABLE");
        assertThat(controller.checkEmailStatus("  ", userDetails()))
                .containsEntry("status", "INVALID");
        assertThat(controller.checkEmailStatus(EMAIL, userDetails()))
                .containsEntry("status", "UNKNOWN");
        assertThat(controller.checkEmailStatus(EMAIL, null))
                .containsEntry("status", "UNKNOWN");
    }

    /**
     * 등록 직후 계정은 인증 대기라 기존 인증을 그대로 두면 안 된다.
     * 인증만 비우고 인증 대기 화면이 쓰는 세션 값은 남긴다.
     */
    @Test
    void startingRegistrationClearsTheAuthenticationButKeepsTheWaitingContext() {
        MockHttpServletRequest request = authenticatedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(missingEmailRegistrationService.start(7L, EMAIL))
                .thenReturn(new Outcome(Result.STARTED, EMAIL));
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.registerEmail(
                EMAIL, userDetails(), request, response, redirectAttributes,
                new ConcurrentModel());

        assertThat(view).isEqualTo("redirect:/users/register/verify-waiting");
        // 인증 대기 화면과 재발송이 보는 세션 값은 그대로 남는다.
        assertThat(request.getSession().getAttribute(
                EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE)).isEqualTo(EMAIL);
        // 인증은 비워 일반 서비스로 들어갈 수 없다.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(savedAuthentication(request)).isNull();
        assertThat(request.getSession().getAttribute("userId")).isNull();
        assertThat(redirectAttributes.getFlashAttributes().get("verificationMessageType"))
                .isEqualTo("success");
    }

    @Test
    void aFailedVerificationMailStillLandsOnTheWaitingScreenWithAnErrorNotice() {
        MockHttpServletRequest request = authenticatedRequest();
        when(missingEmailRegistrationService.start(7L, EMAIL))
                .thenReturn(new Outcome(Result.STARTED_WITHOUT_MAIL, EMAIL));
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.registerEmail(EMAIL, userDetails(), request,
                new MockHttpServletResponse(), redirectAttributes, new ConcurrentModel());

        assertThat(view).isEqualTo("redirect:/users/register/verify-waiting");
        assertThat(redirectAttributes.getFlashAttributes().get("verificationMessageType"))
                .isEqualTo("error");
    }

    /** 이미 쓰는 이메일과 형식 오류는 화면으로 돌아가 사유를 보여준다. */
    @Test
    void aTakenOrMalformedEmailReturnsToTheScreenWithoutClearingTheLogin() {
        MockHttpServletRequest request = authenticatedRequest();
        when(missingEmailRegistrationService.start(7L, EMAIL))
                .thenReturn(new Outcome(Result.EMAIL_TAKEN, null));
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.registerEmail(EMAIL, userDetails(), request,
                new MockHttpServletResponse(), new RedirectAttributesModelMap(), model);

        assertThat(view).isEqualTo("account/email-required");
        assertThat(model.getAttribute("enteredEmail")).isEqualTo(EMAIL);
        assertThat((String) model.getAttribute("emailError")).isNotBlank();
        assertThat(request.getSession().getAttribute(
                EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void aMalformedEmailShowsTheFormatMessage() {
        MockHttpServletRequest request = authenticatedRequest();
        when(missingEmailRegistrationService.start(7L, "nope"))
                .thenReturn(new Outcome(Result.INVALID_EMAIL, null));
        ConcurrentModel model = new ConcurrentModel();

        assertThat(controller.registerEmail("nope", userDetails(), request,
                new MockHttpServletResponse(), new RedirectAttributesModelMap(), model))
                .isEqualTo("account/email-required");
        assertThat((String) model.getAttribute("emailError"))
                .isEqualTo("올바른 이메일 주소를 입력해주세요.");
    }

    /** 화면을 열어 둔 사이 대상에서 벗어났으면 등록하지 않고 화면을 닫는다. */
    @Test
    void anAccountThatIsNoLongerATargetIsSentAwayWithoutAnyChange() {
        MockHttpServletRequest request = authenticatedRequest();
        when(missingEmailRegistrationService.start(7L, EMAIL))
                .thenReturn(new Outcome(Result.NOT_ELIGIBLE, null));

        assertThat(controller.registerEmail(EMAIL, userDetails(), request,
                new MockHttpServletResponse(), new RedirectAttributesModelMap(),
                new ConcurrentModel())).isEqualTo("redirect:/");
        assertThat(request.getSession().getAttribute(
                EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    void anUnauthenticatedPostIsSentToLogin() {
        assertThat(controller.registerEmail(EMAIL, null, new MockHttpServletRequest(),
                new MockHttpServletResponse(), new RedirectAttributesModelMap(),
                new ConcurrentModel())).isEqualTo("redirect:/login");
        verify(missingEmailRegistrationService, never()).start(any(), any());
    }


    /* ---------- 잘못 입력한 이메일 고치기 ---------- */

    /**
     * 등록 성공 후 세션에는 대기 이메일만 남는다.
     * 예전 회원 보완이라는 사실은 DB(email_verification_purpose)가 들고 있다.
     */
    @Test
    void startingRegistrationLeavesOnlyThePendingEmailInTheSession() {
        MockHttpServletRequest request = authenticatedRequest();
        when(missingEmailRegistrationService.start(7L, EMAIL))
                .thenReturn(new Outcome(Result.STARTED, EMAIL));

        controller.registerEmail(EMAIL, userDetails(), request,
                new MockHttpServletResponse(), new RedirectAttributesModelMap(),
                new ConcurrentModel());

        assertThat(java.util.Collections.list(request.getSession().getAttributeNames()))
                .containsExactly(EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE);
    }

    /** 변경 시작 판정은 서비스가 DB 로 한다. 컨트롤러는 대기 이메일만 넘긴다. */
    @Test
    void theCorrectionStartsOnlyFromALegacyPendingSessionAndGoesToReauthentication() {
        MockHttpServletRequest request = legacyWaitingRequest();
        PendingEmailCorrection correction = correction(false);
        when(emailCorrectionService.beginSocial(EMAIL, SocialProvider.KAKAO))
                .thenReturn(correction);

        assertThat(controller.beginSocialEmailCorrection("kakao", request))
                .isEqualTo("redirect:/oauth2/authorization/kakao");
        assertThat(request.getSession().getAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE))
                .isSameAs(correction);
    }

    /** purpose 가 없는 인증 대기(일반 가입·신규 소셜 가입)는 서비스가 null 로 막는다. */
    @Test
    void aPlainVerificationWaitingSessionCannotStartTheCorrection() {
        MockHttpServletRequest request = legacyWaitingRequest();
        when(emailCorrectionService.beginSocial(EMAIL, SocialProvider.KAKAO)).thenReturn(null);

        assertThat(controller.beginSocialEmailCorrection("kakao", request))
                .isEqualTo("redirect:/users/register/verify-waiting");
        assertThat(request.getSession().getAttribute(
                PendingEmailCorrection.SESSION_ATTRIBUTE)).isNull();
    }

    /** 대기 이메일 자체가 없으면 서비스가 null 을 주고 아무 문맥도 생기지 않는다. */
    @Test
    void aSessionWithoutAPendingEmailCannotStartTheCorrection() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        when(emailCorrectionService.beginSocial(null, SocialProvider.KAKAO)).thenReturn(null);

        assertThat(controller.beginSocialEmailCorrection("kakao", request))
                .isEqualTo("redirect:/users/register/verify-waiting");
        assertThat(request.getSession().getAttribute(
                PendingEmailCorrection.SESSION_ATTRIBUTE)).isNull();
    }

    /** provider 이름이 잘못되면 서비스를 부르지도 않는다. */
    @Test
    void anUnknownProviderCannotStartTheCorrection() {
        MockHttpServletRequest request = legacyWaitingRequest();

        assertThat(controller.beginSocialEmailCorrection("facebook", request))
                .isEqualTo("redirect:/users/register/verify-waiting");
        verify(emailCorrectionService, never()).beginSocial(any(), any());
    }

    /** 승인된 문맥이 있어야 변경 화면과 상태 확인이 열린다. */
    @Test
    void theChangeScreenAndItsStatusEndpointNeedAnAuthorizedContext() {
        MockHttpSession authorized = sessionWith(correction(true));
        ConcurrentModel model = new ConcurrentModel();

        assertThat(controller.changeEmailPage(authorized, model))
                .isEqualTo("account/email-change");
        // 현재 이메일은 가려서만 보여주고 대상 id 는 내보내지 않는다.
        assertThat(model.getAttribute("maskedCurrentEmail")).isEqualTo("mem***@example.com");
        assertThat(model.asMap()).doesNotContainKeys("targetUserId", "expectedCurrentEmail");

        assertThat(controller.changeEmailPage(sessionWith(correction(false)),
                new ConcurrentModel())).isEqualTo("redirect:/users/register/verify-waiting");
        assertThat(controller.changeEmailPage(new MockHttpSession(), new ConcurrentModel()))
                .isEqualTo("redirect:/users/register/verify-waiting");

        when(emailCorrectionService.checkAvailability(NEW_EMAIL))
                .thenReturn(EmailCorrectionService.Availability.AVAILABLE);
        assertThat(controller.checkNewEmailStatus(NEW_EMAIL, authorized))
                .containsEntry("status", "AVAILABLE");
        assertThat(controller.checkNewEmailStatus(NEW_EMAIL, sessionWith(correction(false))))
                .containsEntry("status", "UNKNOWN");
        assertThat(controller.checkNewEmailStatus("  ", authorized))
                .containsEntry("status", "INVALID");
    }

    /** 변경에 성공하면 대기 이메일과 표시가 새 주소로 옮겨간다. */
    @Test
    void aSuccessfulChangeMovesTheWaitingContextToTheNewAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(sessionWith(correction(true)));
        when(emailCorrectionService.change(any(), eq(NEW_EMAIL)))
                .thenReturn(new EmailCorrectionService.Outcome(
                        EmailCorrectionService.Result.CHANGED, NEW_EMAIL));
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.changeEmail(
                NEW_EMAIL, request, redirectAttributes, new ConcurrentModel());

        assertThat(view).isEqualTo("redirect:/users/register/verify-waiting");
        assertThat(request.getSession().getAttribute(
                EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE))
                .isEqualTo(NEW_EMAIL);
        // purpose 는 DB 가 그대로 들고 있으므로 세션에 표시를 다시 심지 않는다.
        assertThat(java.util.Collections.list(request.getSession().getAttributeNames()))
                .containsExactly(EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE);
        assertThat(request.getSession().getAttribute(
                PendingEmailCorrection.SESSION_ATTRIBUTE)).isNull();
        assertThat(redirectAttributes.getFlashAttributes().get("verificationMessageType"))
                .isEqualTo("success");
    }

    /** 사용 중이거나 형식이 틀린 주소는 화면으로 돌아가고 권한은 유지한다. */
    @Test
    void aRejectedNewAddressKeepsTheCorrectionRightAndShowsTheReason() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = sessionWith(correction(true));
        request.setSession(session);
        when(emailCorrectionService.change(any(), eq(NEW_EMAIL)))
                .thenReturn(new EmailCorrectionService.Outcome(
                        EmailCorrectionService.Result.EMAIL_TAKEN, null));
        ConcurrentModel model = new ConcurrentModel();

        assertThat(controller.changeEmail(
                NEW_EMAIL, request, new RedirectAttributesModelMap(), model))
                .isEqualTo("account/email-change");
        assertThat((String) model.getAttribute("emailError")).isNotBlank();
        assertThat(session.getAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE)).isNotNull();
        assertThat(session.getAttribute(
                EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE)).isNull();
    }

    /** 권한이 사라졌거나 대상 상태가 바뀌었으면 문맥을 정리하고 대기 화면으로 돌아간다. */
    @Test
    void aLostRightSendsTheUserBackToTheWaitingScreen() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = sessionWith(correction(true));
        request.setSession(session);
        when(emailCorrectionService.change(any(), eq(NEW_EMAIL)))
                .thenReturn(new EmailCorrectionService.Outcome(
                        EmailCorrectionService.Result.NOT_AUTHORIZED, null));

        assertThat(controller.changeEmail(
                NEW_EMAIL, request, new RedirectAttributesModelMap(), new ConcurrentModel()))
                .isEqualTo("redirect:/users/register/verify-waiting");
        assertThat(session.getAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE)).isNull();
    }

    /** 취소는 이메일도 토큰도 건드리지 않는다. */
    @Test
    void cancellingLeavesTheEmailAndTokenExactlyAsTheyWere() {
        MockHttpSession session = sessionWith(correction(true));
        session.setAttribute(
                EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE, EMAIL);

        assertThat(controller.cancelEmailCorrection(session))
                .isEqualTo("redirect:/users/register/verify-waiting");
        assertThat(session.getAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE)).isNull();
        assertThat(session.getAttribute(
                EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE)).isEqualTo(EMAIL);
        verify(emailCorrectionService, never()).change(any(), any());
    }

    private static final String NEW_EMAIL = "correct@example.com";

    private PendingEmailCorrection correction(boolean authorized) {
        Instant now = Instant.now();
        PendingEmailCorrection pending = new PendingEmailCorrection(
                "correction-flow", 7L, EMAIL, PendingEmailCorrection.Method.SOCIAL,
                SocialProvider.KAKAO, false, now.minusSeconds(10), now.plusSeconds(590));
        return authorized ? pending.authorize() : pending;
    }

    private MockHttpSession sessionWith(PendingEmailCorrection correction) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE, correction);
        return session;
    }

    private MockHttpServletRequest legacyWaitingRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        request.getSession().setAttribute(
                EmailVerificationController.PENDING_EMAIL_SESSION_ATTRIBUTE, EMAIL);
        return request;
    }


    /* ---------- 비밀번호 재확인 (일반 회원) ---------- */

    @Test
    void thePasswordPathOpensTheCheckScreenWithAnUnauthorizedContext() {
        MockHttpServletRequest request = legacyWaitingRequest();
        PendingEmailCorrection correction = localCorrection(false);
        when(emailCorrectionService.beginLocal(EMAIL)).thenReturn(correction);

        assertThat(controller.beginLocalEmailCorrection(request))
                .isEqualTo("redirect:/account/email-required/change/password");
        assertThat(request.getSession().getAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE))
                .isSameAs(correction);
    }

    /** 비밀번호가 없는 소셜 전용 계정은 서비스가 막는다. */
    @Test
    void aSocialOnlyAccountCannotOpenThePasswordCheckScreen() {
        MockHttpServletRequest request = legacyWaitingRequest();
        when(emailCorrectionService.beginLocal(EMAIL)).thenReturn(null);

        assertThat(controller.beginLocalEmailCorrection(request))
                .isEqualTo("redirect:/users/register/verify-waiting");
        assertThat(request.getSession().getAttribute(
                PendingEmailCorrection.SESSION_ATTRIBUTE)).isNull();
    }

    /** 화면에는 가려진 현재 이메일만 나가고 대상 id 는 나가지 않는다. */
    @Test
    void thePasswordScreenShowsOnlyTheMaskedAddress() {
        ConcurrentModel model = new ConcurrentModel();

        assertThat(controller.passwordCheckPage(sessionWith(localCorrection(false)), model))
                .isEqualTo("account/email-change-password");
        assertThat(model.getAttribute("maskedCurrentEmail")).isEqualTo("mem***@example.com");
        assertThat(model.asMap()).doesNotContainKeys("targetUserId", "expectedCurrentEmail");

        // 소셜 문맥이나 빈 세션으로는 열리지 않는다.
        assertThat(controller.passwordCheckPage(
                sessionWith(correction(false)), new ConcurrentModel()))
                .isEqualTo("redirect:/users/register/verify-waiting");
        assertThat(controller.passwordCheckPage(new MockHttpSession(), new ConcurrentModel()))
                .isEqualTo("redirect:/users/register/verify-waiting");
    }

    @Test
    void theCorrectPasswordStoresAnAuthorizedContextAndOpensTheChangeScreen() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = sessionWith(localCorrection(false));
        request.setSession(session);
        PendingEmailCorrection authorized = localCorrection(true);
        when(emailCorrectionService.authorizeLocal(any(), eq("secret"))).thenReturn(authorized);

        assertThat(controller.checkPassword("secret", request, new ConcurrentModel()))
                .isEqualTo("redirect:/account/email-required/change");
        assertThat(session.getAttribute(PendingEmailCorrection.SESSION_ATTRIBUTE))
                .isSameAs(authorized);
    }

    /** 틀린 비밀번호는 권한을 주지 않고 일반적인 안내만 남긴다. */
    @Test
    void aWrongPasswordKeepsTheContextUnauthorizedAndShowsAGenericNotice() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = sessionWith(localCorrection(false));
        request.setSession(session);
        when(emailCorrectionService.authorizeLocal(any(), eq("wrong"))).thenReturn(null);
        ConcurrentModel model = new ConcurrentModel();

        assertThat(controller.checkPassword("wrong", request, model))
                .isEqualTo("account/email-change-password");
        assertThat((String) model.getAttribute("passwordError")).isNotBlank();
        // 계정 정보를 흘리지 않는다.
        assertThat(model.asMap()).doesNotContainKeys("targetUserId", "expectedCurrentEmail");
        // 문맥은 승인되지 않은 상태 그대로다.
        assertThat(((PendingEmailCorrection) session.getAttribute(
                PendingEmailCorrection.SESSION_ATTRIBUTE)).authorized()).isFalse();
    }

    /** 비밀번호를 거치지 않고 변경 화면에 바로 들어갈 수 없다. */
    @Test
    void theChangeScreenIsClosedToAnUnauthorizedLocalContext() {
        assertThat(controller.changeEmailPage(
                sessionWith(localCorrection(false)), new ConcurrentModel()))
                .isEqualTo("redirect:/users/register/verify-waiting");
    }

    private PendingEmailCorrection localCorrection(boolean authorized) {
        Instant now = Instant.now();
        PendingEmailCorrection pending = new PendingEmailCorrection(
                "local-flow", 7L, EMAIL, PendingEmailCorrection.Method.LOCAL, null, false,
                now.minusSeconds(10), now.plusSeconds(590));
        return authorized ? pending.authorize() : pending;
    }

    private CustomUserDetails userDetails() {
        User user = new User();
        user.setId(7L);
        user.setUsername("member");
        user.setNickname("기존닉네임");
        user.setUserRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return new CustomUserDetails(user);
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        CustomUserDetails principal = userDetails();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        request.getSession().setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        request.getSession().setAttribute("userId", 7L);
        return request;
    }

    private Object savedAuthentication(MockHttpServletRequest request) {
        Object stored = request.getSession().getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        return stored instanceof SecurityContext context ? context.getAuthentication() : null;
    }
}
