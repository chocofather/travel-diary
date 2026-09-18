package com.example.travlediary.controller.user;

import com.example.travlediary.dto.SocialSignupForm;
import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.security.ClientIpResolver;
import com.example.travlediary.security.InMemoryAccountAbuseGuard;
import com.example.travlediary.service.policy.SignupPolicyFixtures;
import com.example.travlediary.service.policy.SignupPolicyService;
import com.example.travlediary.service.user.SocialEmailAccountResolver;
import com.example.travlediary.service.user.SocialLoginLinkService;
import com.example.travlediary.service.user.SocialSignupAuthenticationService;
import com.example.travlediary.service.user.SocialSignupOutcome;
import com.example.travlediary.service.user.SocialSignupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 소셜 가입 인증메일이 일반 가입과 <b>같은 IP 통</b>을 쓰는지.
 *
 * <p>한쪽만 막으면 공격자는 다른 쪽으로 돌아가면 그만이다. 두 경로가 쓰는 SMTP 자원이 하나이므로
 * 세는 통도 하나여야 한다. 여기서는 진짜 guard 하나를 공유해 그 사실을 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class SocialSignupMailThrottleTest {

    private static final int LIMIT = InMemoryAccountAbuseGuard.RECOVERY_REQUEST_LIMIT;

    @Mock private SocialSignupService socialSignupService;
    @Mock private SocialSignupAuthenticationService authenticationService;
    @Mock private SocialEmailAccountResolver socialEmailAccountResolver;
    @Mock private SocialLoginLinkService socialLoginLinkService;
    @Mock private SignupPolicyService signupPolicyService;

    /** 일반 가입 경로가 쓰는 것과 같은 구현·같은 인스턴스다. */
    private final InMemoryAccountAbuseGuard guard = new InMemoryAccountAbuseGuard();

    private SocialSignupController controller;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messages.setFallbackToSystemLocale(false);
        lenient().when(signupPolicyService.loadSignupPolicies())
                .thenReturn(SignupPolicyFixtures.activeSignupPolicies());

        controller = new SocialSignupController(
                socialSignupService, authenticationService,
                socialEmailAccountResolver, socialLoginLinkService,
                signupPolicyService, messages, guard);
    }

    /** 평범한 소셜 가입은 예전 그대로 끝난다. */
    @Test
    void anOrdinarySocialSignupStillCompletes() throws Exception {
        MockHttpServletRequest request = requestFrom("203.0.113.71");
        SocialSignupForm form = acceptedForm();
        when(socialSignupService.complete(any(), any()))
                .thenReturn(new SocialSignupOutcome(41L, "new@example.com", null));

        String view = controller.completeSignup(form, binding(form), null,
                request, new MockHttpServletResponse(), redirectAttributes(),
                new ConcurrentModel());

        assertThat(view).isNull();
        verify(socialSignupService).complete(any(), any());
    }

    /**
     * 일반 가입이 채운 통을 소셜 가입도 함께 본다.
     *
     * <p>일반 가입 Controller 가 부르는 것과 같은 자리({@code checkRecoveryRequest})로 채운다.
     */
    @Test
    void socialSignupSharesTheSameClientBudgetAsOrdinarySignup() throws Exception {
        String ipAddress = "203.0.113.72";
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            guard.checkRecoveryRequest(ipAddress);
        }

        MockHttpServletRequest request = requestFrom(ipAddress);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SocialSignupForm form = acceptedForm();

        String view = controller.completeSignup(form, binding(form), null,
                request, response, redirectAttributes(), new ConcurrentModel());

        assertThat(view).isEqualTo("social-signup");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getHeader("Retry-After")).isNotBlank();
        // 막힌 요청은 계정을 만들지도, 인증메일을 보내지도 않는다
        verify(socialSignupService, never()).complete(any(), any());
    }

    /** 한 사람이 막혀도 다른 사람의 소셜 가입은 그대로 된다. */
    @Test
    void anotherClientCanStillCompleteSocialSignup() throws Exception {
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            guard.checkRecoveryRequest("203.0.113.73");
        }
        when(socialSignupService.complete(any(), any()))
                .thenReturn(new SocialSignupOutcome(41L, "new@example.com", null));

        SocialSignupForm form = acceptedForm();
        String view = controller.completeSignup(form, binding(form), null,
                requestFrom("198.51.100.80"), new MockHttpServletResponse(),
                redirectAttributes(), new ConcurrentModel());

        assertThat(view).isNull();
    }

    /** 입력 형식 오류는 메일과 무관하므로 통을 소모하지 않는다. */
    @Test
    void bindingErrorsNeverConsumeTheMailBudget() throws Exception {
        String ipAddress = "203.0.113.74";
        SocialSignupForm form = acceptedForm();

        for (int attempt = 0; attempt <= LIMIT; attempt++) {
            BeanPropertyBindingResult rejected = binding(form);
            rejected.rejectValue("nickname", "required", "닉네임을 입력해주세요.");
            controller.completeSignup(form, rejected, null, requestFrom(ipAddress),
                    new MockHttpServletResponse(), redirectAttributes(), new ConcurrentModel());
        }

        // 통이 비어 있으므로 정상 가입 한 건은 그대로 지나간다
        when(socialSignupService.complete(any(), any()))
                .thenReturn(new SocialSignupOutcome(41L, "new@example.com", null));
        assertThat(controller.completeSignup(form, binding(form), null, requestFrom(ipAddress),
                new MockHttpServletResponse(), redirectAttributes(), new ConcurrentModel()))
                .isNull();
    }

    /** 주소 판별은 공통 resolver 결과를 쓴다. 전달 머리말을 스스로 읽지 않는다. */
    @Test
    void theClientAddressComesFromTheSharedResolver() throws Exception {
        String realAddress = "203.0.113.75";
        for (int attempt = 0; attempt < LIMIT; attempt++) {
            guard.checkRecoveryRequest(realAddress);
        }

        MockHttpServletRequest request = requestFrom(realAddress);
        // 위조 머리말을 붙여도 통을 바꿔 빠져나갈 수 없다
        request.addHeader("CF-Connecting-IP", "198.51.100.90");
        request.addHeader("X-Forwarded-For", "198.51.100.91");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SocialSignupForm form = acceptedForm();

        controller.completeSignup(form, binding(form), null, request, response,
                redirectAttributes(), new ConcurrentModel());

        assertThat(ClientIpResolver.of(request)).isEqualTo(realAddress);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    /* ===== 도우미 ===== */

    private MockHttpServletRequest requestFrom(String ipAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ipAddress);
        request.getSession().setAttribute(PendingSocialSignup.SESSION_ATTRIBUTE,
                new PendingSocialSignup("flow-123", SocialProvider.GOOGLE, "new-google-sub",
                        "new@example.com", true,
                        Instant.now().minusSeconds(10), Instant.now().plusSeconds(590)));
        return request;
    }

    private BeanPropertyBindingResult binding(SocialSignupForm form) {
        return new BeanPropertyBindingResult(form, "socialSignupForm");
    }

    private SocialSignupForm acceptedForm() {
        SocialSignupForm form = new SocialSignupForm();
        form.setBirthDate("2000-01-01");
        form.setNickname("새여행자");
        form.setAgreedPolicyVersionIds(SignupPolicyFixtures.requiredConsentIds());
        return form;
    }

    private RedirectAttributes redirectAttributes() {
        return new RedirectAttributesModelMap();
    }
}
