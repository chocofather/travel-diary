package com.tripbora.config;

import com.tripbora.model.User;
import com.tripbora.model.UserRole;
import com.tripbora.model.UserStatus;
import com.tripbora.repository.user.UserMapper;
import com.tripbora.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripBoraAuthenticationRestorerTest {

    @Mock private UserMapper userMapper;

    @Test
    void restoresOnlyTheDatabaseUserAndDatabaseRoleAfterFailedProviderReauthentication() {
        User user = new User();
        user.setId(7L);
        user.setUserRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        when(userMapper.findById(7L)).thenReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean restored = new TripBoraAuthenticationRestorer(userMapper)
                .restore(request, response, 7L);

        SecurityContext context = (SecurityContext) request.getSession().getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(restored).isTrue();
        assertThat(context.getAuthentication().getPrincipal())
                .isInstanceOf(CustomUserDetails.class);
        assertThat(context.getAuthentication().getName()).isEqualTo("user:7");
        assertThat(context.getAuthentication().getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_USER");
        assertThat(request.getSession().getAttribute("userId")).isEqualTo(7L);
    }

    @Test
    void doesNotRestoreADeactivatedAccount() {
        User user = new User();
        user.setId(7L);
        user.setUserRole(UserRole.USER);
        user.setStatus(UserStatus.DEACTIVATED);
        when(userMapper.findById(7L)).thenReturn(user);

        boolean restored = new TripBoraAuthenticationRestorer(userMapper).restore(
                new MockHttpServletRequest(), new MockHttpServletResponse(), 7L);

        assertThat(restored).isFalse();
    }
}
