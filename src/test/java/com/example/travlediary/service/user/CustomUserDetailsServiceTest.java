package com.example.travlediary.service.user;

import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserSanctionService userSanctionService;

    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new CustomUserDetailsService(userMapper, userSanctionService);
    }

    @Test
    void activeUserStillLogsIn() {
        when(userMapper.findByUsername("travler")).thenReturn(user(UserStatus.ACTIVE));

        assertThat(service.loadUserByUsername("travler").getUsername()).isEqualTo("travler");
    }

    @Test
    void socialUserWithoutUsernameGetsStableInternalPrincipalNameAndDatabaseRole() {
        User user = user(UserStatus.ACTIVE);
        user.setUsername(null);

        var details = new com.example.travlediary.security.CustomUserDetails(user);

        assertThat(details.getUsername()).isEqualTo("user:5");
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void unknownUserIsRejected() {
        when(userMapper.findByUsername("nobody")).thenReturn(null);

        assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void inactiveUserKeepsTheEmailVerificationMessage() {
        when(userMapper.findByUsername("travler")).thenReturn(user(UserStatus.INACTIVE));

        assertThatThrownBy(() -> service.loadUserByUsername("travler"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("이메일 인증이 완료되지 않았습니다.");
    }

    @Test
    void deactivatedAndSuspendedUsersGetTheirOwnMessages() {
        when(userMapper.findByUsername("travler")).thenReturn(user(UserStatus.DEACTIVATED));
        assertThatThrownBy(() -> service.loadUserByUsername("travler"))
                .hasMessage("탈퇴한 계정입니다.");

        when(userMapper.findByUsername("travler")).thenReturn(user(UserStatus.SUSPENDED));
        assertThatThrownBy(() -> service.loadUserByUsername("travler"))
                .hasMessage("휴면 상태의 계정입니다. 고객센터로 문의해주세요.");
    }

    /** 탈퇴 유예 회원은 로그인할 수 없고, 탈퇴 완료와 다른 안내를 받는다. */
    @Test
    /**
     * 탈퇴 유예 회원은 인증까지만 허용한다. 서비스 이용 권한이 돌아오는 것은 아니고,
     * 격리와 안내 화면 이동은 WithdrawalPendingAccountFilter 와 로그인 성공 핸들러가 맡는다.
     */
    void withdrawalPendingUserIsAuthenticatedSoTheNoticeScreenCanHandleIt() {
        when(userMapper.findByUsername("travler"))
                .thenReturn(user(UserStatus.WITHDRAWAL_PENDING));

        var details = service.loadUserByUsername("travler");

        assertThat(details.getUsername()).isEqualTo("travler");
        assertThat(details.isEnabled()).isTrue();
    }

    /**
     * 아이디만 알면 여기까지는 누구나 올 수 있다. 비밀번호 검증은 그 다음이므로
     * 유예가 끝난 계정이라도 이 단계에서는 아무것도 지우거나 바꾸지 않는다.
     * 최종 파기는 인증에 성공한 뒤 로그인 성공 핸들러에서만 일어난다.
     */
    @Test
    void loadingAnExpiredWithdrawalAccountNeverChangesAnything() {
        User account = user(UserStatus.WITHDRAWAL_PENDING);
        account.setPurgeScheduledAt(java.time.LocalDateTime.now().minusDays(1));
        when(userMapper.findByUsername("travler")).thenReturn(account);

        assertThat(service.loadUserByUsername("travler").getUsername()).isEqualTo("travler");

        verify(userMapper).findByUsername("travler");
        org.mockito.Mockito.verifyNoMoreInteractions(userMapper);
        org.mockito.Mockito.verifyNoInteractions(userSanctionService);
    }

    @Test
    void restrictedUserIsAuthenticatedSoAccessControlCanHandleIt() {
        when(userMapper.findByUsername("travler")).thenReturn(user(UserStatus.RESTRICTED));
        when(userSanctionService.releaseIfExpired(5L)).thenReturn(false);

        var details = service.loadUserByUsername("travler");

        // 인증 자체는 성공하고, 접근 제한은 RestrictedAccountFilter 가 처리한다
        assertThat(details.getUsername()).isEqualTo("travler");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void restrictedUserPasswordIsStillHandedToTheProviderSoWrongPasswordsFailNormally() {
        when(userMapper.findByUsername("travler")).thenReturn(user(UserStatus.RESTRICTED));
        when(userSanctionService.releaseIfExpired(5L)).thenReturn(false);

        // 비밀번호 비교는 DaoAuthenticationProvider 가 수행한다.
        // 즉 비밀번호가 틀리면 제재 여부와 무관하게 기존 로그인 실패 흐름을 탄다.
        assertThat(service.loadUserByUsername("travler").getPassword()).isEqualTo("encoded");
    }

    @Test
    void expiredTemporarySanctionIsReleasedAtLoginTime() {
        when(userMapper.findByUsername("travler")).thenReturn(user(UserStatus.RESTRICTED));
        when(userSanctionService.releaseIfExpired(5L)).thenReturn(true);

        assertThat(service.loadUserByUsername("travler").getUsername()).isEqualTo("travler");
        verify(userSanctionService).releaseIfExpired(5L);
    }

    private User user(UserStatus status) {
        User user = new User();
        user.setId(5L);
        user.setUsername("travler");
        user.setUserPassword("encoded");
        user.setUserRole(UserRole.USER);
        user.setStatus(status);
        return user;
    }
}
