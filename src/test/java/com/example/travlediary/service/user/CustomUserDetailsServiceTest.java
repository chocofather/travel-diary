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
