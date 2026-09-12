package com.example.travlediary.service.user;

import com.example.travlediary.model.PendingSocialLoginLink;
import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.SocialAccount;
import com.example.travlediary.model.SocialConnectionNotice;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialLoginLinkServiceTest {

    private static final String EMAIL = "member@example.com";

    @Mock
    private UserMapper userMapper;
    @Mock
    private SocialAccountService socialAccountService;

    private SocialLoginLinkService service;

    @BeforeEach
    void setUp() {
        service = new SocialLoginLinkService(userMapper, socialAccountService);
    }

    // ---------- begin ----------

    @ParameterizedTest
    @EnumSource(value = SocialProvider.class, names = {"KAKAO", "NAVER"})
    void aLinkStartsFromTheServerPendingAndTheReCheckedTargetAccount(SocialProvider provider) {
        when(userMapper.findByEmail(EMAIL)).thenReturn(activeTarget());

        PendingSocialLoginLink link = service.begin(
                signupPending(provider), "  Member@Example.COM ");

        assertThat(link).isNotNull();
        assertThat(link.flowId()).isNotBlank();
        assertThat(link.provider()).isEqualTo(provider);
        assertThat(link.providerUserId()).isEqualTo("provider-sub");
        assertThat(link.targetUserId()).isEqualTo(25L);
        assertThat(link.normalizedTargetEmail()).isEqualTo(EMAIL);
        assertThat(Duration.between(link.createdAt(), link.expiresAt()))
                .isEqualTo(Duration.ofMinutes(10));
        // 시작 단계에서는 아무것도 저장하지 않는다.
        verify(socialAccountService, never())
                .connectToUser(anyLong(), any(), any(), any(), any());
        verify(userMapper, never()).insertUser(any());
    }

    /** Google 은 /social-link 로 따로 처리한다. 이 흐름을 타지 않는다. */
    @Test
    void googleNeverStartsThisFlow() {
        assertThat(service.begin(signupPending(SocialProvider.GOOGLE), EMAIL)).isNull();
        verify(userMapper, never()).findByEmail(anyString());
    }

    /** AJAX 결과와 무관하게 시작 시점에 이메일과 계정 상태를 다시 본다. */
    @ParameterizedTest
    @EnumSource(value = UserStatus.class,
            names = {"INACTIVE", "SUSPENDED", "RESTRICTED", "WITHDRAWAL_PENDING", "DEACTIVATED"})
    void anEmailThatIsNoLongerAnActiveAccountCannotStartALink(UserStatus status) {
        User target = activeTarget();
        target.setStatus(status);
        when(userMapper.findByEmail(EMAIL)).thenReturn(target);

        assertThat(service.begin(signupPending(SocialProvider.KAKAO), EMAIL)).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "not-an-email"})
    void amalformedEmailCannotStartALink(String email) {
        assertThat(service.begin(signupPending(SocialProvider.KAKAO), email)).isNull();
        verify(userMapper, never()).findByEmail(anyString());
    }

    @Test
    void anEmailWithNoAccountCannotStartALink() {
        assertThat(service.begin(signupPending(SocialProvider.KAKAO), EMAIL)).isNull();
    }

    /** 같은 provider 식별자가 이미 어딘가에 붙어 있으면 시작하지 않는다. */
    @Test
    void aProviderIdentityThatIsAlreadyConnectedCannotStartALink() {
        when(socialAccountService.findByProviderAndProviderUserId(
                SocialProvider.KAKAO, "provider-sub")).thenReturn(new SocialAccount());

        assertThat(service.begin(signupPending(SocialProvider.KAKAO), EMAIL)).isNull();
        verify(userMapper, never()).findByEmail(anyString());
    }

    // ---------- completeAfterLogin ----------

    @Test
    void loggingIntoTheTargetAccountConnectsThroughTheExistingConnectService() {
        MockHttpSession session = sessionWith(link(SocialProvider.KAKAO, 25L, EMAIL));
        when(userMapper.findById(25L)).thenReturn(activeTarget());
        when(socialAccountService.connectToUser(
                25L, SocialProvider.KAKAO, "provider-sub", "provider@example.com", Boolean.TRUE))
                .thenReturn(SocialConnectionResult.CONNECTED);

        assertThat(service.completeAfterLogin(session, 25L))
                .isEqualTo(SocialLoginLinkService.Outcome.CONNECTED);

        assertThat(notice(session).type())
                .isEqualTo(SocialConnectionNotice.Type.CONNECTED);
        assertThat(notice(session).provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(session.getAttribute(PendingSocialLoginLink.SESSION_ATTRIBUTE)).isNull();
        verify(userMapper, never()).insertUser(any());
    }

    @Test
    void anAlreadyConnectedProviderEndsSafelyWithoutAnError() {
        MockHttpSession session = sessionWith(link(SocialProvider.NAVER, 25L, EMAIL));
        when(userMapper.findById(25L)).thenReturn(activeTarget());
        when(socialAccountService.connectToUser(anyLong(), any(), any(), any(), any()))
                .thenReturn(SocialConnectionResult.ALREADY_CONNECTED);

        assertThat(service.completeAfterLogin(session, 25L))
                .isEqualTo(SocialLoginLinkService.Outcome.ALREADY_CONNECTED);
        assertThat(notice(session).type())
                .isEqualTo(SocialConnectionNotice.Type.ALREADY_CONNECTED);
    }

    @Test
    void aProviderOwnedByAnotherAccountIsNeverMoved() {
        MockHttpSession session = sessionWith(link(SocialProvider.KAKAO, 25L, EMAIL));
        when(userMapper.findById(25L)).thenReturn(activeTarget());
        when(socialAccountService.connectToUser(anyLong(), any(), any(), any(), any()))
                .thenReturn(SocialConnectionResult.OWNED_BY_ANOTHER_USER);

        assertThat(service.completeAfterLogin(session, 25L))
                .isEqualTo(SocialLoginLinkService.Outcome.OWNED_BY_ANOTHER_USER);
        assertThat(notice(session).type()).isEqualTo(SocialConnectionNotice.Type.ERROR);
        assertThat(session.getAttribute(PendingSocialLoginLink.SESSION_ATTRIBUTE)).isNull();
    }

    /** 대상이 아닌 계정으로 로그인했다. 로그인은 그대로 두고 연결만 하지 않는다. */
    @Test
    void loggingIntoADifferentAccountNeverConnectsThatAccount() {
        MockHttpSession session = sessionWith(link(SocialProvider.KAKAO, 25L, EMAIL));

        assertThat(service.completeAfterLogin(session, 30L))
                .isEqualTo(SocialLoginLinkService.Outcome.TARGET_MISMATCH);

        assertThat(notice(session).type())
                .isEqualTo(SocialConnectionNotice.Type.TARGET_MISMATCH);
        assertThat(session.getAttribute(PendingSocialLoginLink.SESSION_ATTRIBUTE)).isNull();
        verify(socialAccountService, never())
                .connectToUser(anyLong(), any(), any(), any(), any());
        verify(userMapper, never()).findById(anyLong());
    }

    /** 로그인 사이 대상 상태가 바뀌면 붙이지 않는다. 탈퇴 유예 우회도 막힌다. */
    @ParameterizedTest
    @EnumSource(value = UserStatus.class,
            names = {"INACTIVE", "SUSPENDED", "RESTRICTED", "WITHDRAWAL_PENDING", "DEACTIVATED"})
    void aTargetThatChangedStateBetweenTheScreensIsNotConnected(UserStatus status) {
        MockHttpSession session = sessionWith(link(SocialProvider.KAKAO, 25L, EMAIL));
        User target = activeTarget();
        target.setStatus(status);
        when(userMapper.findById(25L)).thenReturn(target);

        assertThat(service.completeAfterLogin(session, 25L))
                .isEqualTo(SocialLoginLinkService.Outcome.TARGET_UNAVAILABLE);
        verify(socialAccountService, never())
                .connectToUser(anyLong(), any(), any(), any(), any());
    }

    @Test
    void aTargetWhoseEmailChangedOrDisappearedIsNotConnected() {
        MockHttpSession moved = sessionWith(link(SocialProvider.KAKAO, 25L, EMAIL));
        User changed = activeTarget();
        changed.setUserEmail("someone-else@example.com");
        when(userMapper.findById(25L)).thenReturn(changed, (User) null);

        assertThat(service.completeAfterLogin(moved, 25L))
                .isEqualTo(SocialLoginLinkService.Outcome.TARGET_UNAVAILABLE);
        assertThat(service.completeAfterLogin(
                sessionWith(link(SocialProvider.KAKAO, 25L, EMAIL)), 25L))
                .isEqualTo(SocialLoginLinkService.Outcome.TARGET_UNAVAILABLE);
        verify(socialAccountService, never())
                .connectToUser(anyLong(), any(), any(), any(), any());
    }

    @Test
    void anExpiredOrDamagedContextIsConsumedWithoutConnecting() {
        Instant past = Instant.now().minusSeconds(700);
        MockHttpSession expired = sessionWith(new PendingSocialLoginLink(
                "flow", SocialProvider.KAKAO, "provider-sub", "provider@example.com", true,
                25L, EMAIL, past, past.plusSeconds(600)));

        assertThat(service.completeAfterLogin(expired, 25L))
                .isEqualTo(SocialLoginLinkService.Outcome.EXPIRED);
        assertThat(expired.getAttribute(PendingSocialLoginLink.SESSION_ATTRIBUTE)).isNull();

        MockHttpSession damaged = sessionWith(new PendingSocialLoginLink(
                "", SocialProvider.KAKAO, "", null, null, null, null,
                Instant.now(), Instant.now().plusSeconds(600)));
        assertThat(service.completeAfterLogin(damaged, 25L))
                .isEqualTo(SocialLoginLinkService.Outcome.EXPIRED);

        verify(socialAccountService, never())
                .connectToUser(anyLong(), any(), any(), any(), any());
    }

    /** 기다리는 연결이 없으면 아무것도 하지 않는다. 평소 로그인 흐름이 그대로 유지된다. */
    @Test
    void anOrdinaryLoginIsLeftCompletelyUntouched() {
        MockHttpSession session = new MockHttpSession();

        assertThat(service.completeAfterLogin(session, 25L))
                .isEqualTo(SocialLoginLinkService.Outcome.NONE);
        assertThat(SocialLoginLinkService.Outcome.NONE.handled()).isFalse();
        assertThat(session.getAttribute(SocialConnectionNotice.SESSION_ATTRIBUTE)).isNull();
        assertThat(service.completeAfterLogin(null, 25L))
                .isEqualTo(SocialLoginLinkService.Outcome.NONE);
        verifyNoInteractions(socialAccountService, userMapper);
    }

    @Test
    void aFailedConnectIsReportedWithoutLeavingTheContextBehind() {
        MockHttpSession session = sessionWith(link(SocialProvider.KAKAO, 25L, EMAIL));
        when(userMapper.findById(25L)).thenReturn(activeTarget());
        when(socialAccountService.connectToUser(anyLong(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("duplicate"));

        assertThat(service.completeAfterLogin(session, 25L))
                .isEqualTo(SocialLoginLinkService.Outcome.FAILED);
        assertThat(session.getAttribute(PendingSocialLoginLink.SESSION_ATTRIBUTE)).isNull();
    }


    /** 기존 계정 연결은 프로필을 건드리지 않는다. 닉네임도 약관 동의도 새로 쓰지 않는다. */
    @Test
    void linkingNeverTouchesTheExistingProfileOrConsents() {
        MockHttpSession session = sessionWith(link(SocialProvider.NAVER, 25L, EMAIL));
        User target = activeTarget();
        target.setNickname("기존닉네임");
        when(userMapper.findById(25L)).thenReturn(target);
        when(socialAccountService.connectToUser(anyLong(), any(), any(), any(), any()))
                .thenReturn(SocialConnectionResult.CONNECTED);

        assertThat(service.completeAfterLogin(session, 25L))
                .isEqualTo(SocialLoginLinkService.Outcome.CONNECTED);

        // users 는 조회만 하고 어떤 갱신도 하지 않는다.
        verify(userMapper).findById(25L);
        verify(userMapper, never()).insertUser(any());
        verifyNoMoreInteractions(userMapper);
        assertThat(target.getNickname()).isEqualTo("기존닉네임");
    }

    private PendingSocialSignup signupPending(SocialProvider provider) {
        Instant now = Instant.now();
        return new PendingSocialSignup(
                "signup-flow", provider, "provider-sub", "provider@example.com", true,
                now.minusSeconds(10), now.plusSeconds(590));
    }

    private PendingSocialLoginLink link(SocialProvider provider, Long targetUserId, String email) {
        Instant now = Instant.now();
        return new PendingSocialLoginLink(
                "link-flow", provider, "provider-sub", "provider@example.com", Boolean.TRUE,
                targetUserId, email, now.minusSeconds(10), now.plusSeconds(590));
    }

    private MockHttpSession sessionWith(PendingSocialLoginLink pending) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(PendingSocialLoginLink.SESSION_ATTRIBUTE, pending);
        return session;
    }

    private SocialConnectionNotice notice(MockHttpSession session) {
        return (SocialConnectionNotice)
                session.getAttribute(SocialConnectionNotice.SESSION_ATTRIBUTE);
    }

    private User activeTarget() {
        User user = new User();
        user.setId(25L);
        user.setUserEmail(EMAIL);
        user.setUserRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
