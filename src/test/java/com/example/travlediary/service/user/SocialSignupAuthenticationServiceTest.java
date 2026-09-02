package com.example.travlediary.service.user;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.model.PendingSocialSignup;
import com.example.travlediary.model.SocialProvider;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialSignupAuthenticationServiceTest {

    @Mock
    private UserMapper userMapper;

    private SocialSignupAuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new SocialSignupAuthenticationService(
                userMapper, new CustomLoginSuccessHandler(userMapper));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesCreatedUserWithDatabaseRoleRotatesSessionAndRemovesPending()
            throws Exception {
        User user = user(41L, UserRole.USER, UserStatus.ACTIVE);
        when(userMapper.findById(41L)).thenReturn(user);
        when(userMapper.findStatusById(41L)).thenReturn(UserStatus.ACTIVE);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(PendingSocialSignup.SESSION_ATTRIBUTE, pending());
        String originalSessionId = request.getSession().getId();
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.authenticate(41L, request, response);

        SecurityContext context = (SecurityContext) request.getSession().getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        Authentication authentication = context.getAuthentication();
        assertThat(authentication.getPrincipal()).isInstanceOf(CustomUserDetails.class);
        assertThat(((CustomUserDetails) authentication.getPrincipal()).getId()).isEqualTo(41L);
        assertThat(authentication.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_USER");
        assertThat(request.getSession().getAttribute("userId")).isEqualTo(41L);
        assertThat(request.getSession().getAttribute(PendingSocialSignup.SESSION_ATTRIBUTE)).isNull();
        assertThat(request.getSession().getId()).isNotEqualTo(originalSessionId);
        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void neverCreatesAdminAuthorityForNewUserUnlessDatabaseRoleIsAdmin() throws Exception {
        User user = user(41L, UserRole.USER, UserStatus.ACTIVE);
        when(userMapper.findById(41L)).thenReturn(user);
        when(userMapper.findStatusById(41L)).thenReturn(UserStatus.ACTIVE);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.authenticate(41L, request, response);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_USER");
    }

    @Test
    void refusesMissingOrNonActiveDatabaseUser() {
        when(userMapper.findById(41L)).thenReturn(user(41L, UserRole.USER, UserStatus.INACTIVE));

        assertThatThrownBy(() -> service.authenticate(
                41L, new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(SocialSignupAuthenticationException.class);
    }

    private User user(long id, UserRole role, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setUserRole(role);
        user.setStatus(status);
        return user;
    }

    private PendingSocialSignup pending() {
        Instant now = Instant.now();
        return new PendingSocialSignup(
                "flow", SocialProvider.GOOGLE, "sub", "new@example.com", true,
                now.minusSeconds(10), now.plusSeconds(590));
    }
}
