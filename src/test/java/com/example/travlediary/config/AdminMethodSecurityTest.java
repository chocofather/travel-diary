package com.example.travlediary.config;

import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.category.CountryCategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.ConcurrentModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 Controller 의 {@code @PreAuthorize} 가 실제로 동작하는지.
 *
 * <p>예전에는 {@code @EnableMethodSecurity} 가 없어 이 어노테이션들이 아무 일도 하지 않았다.
 * URL 규칙({@code /admin/** → hasRole('ADMIN')})이 막고 있어 우회는 없었지만, 어노테이션이
 * 동작한다고 믿게 만드는 가짜 2중 방어였다. 여기서는 두 겹이 모두 살아 있는지 확인한다.
 *
 * <p>메서드를 직접 부르는 검사는 URL 규칙을 지나지 않으므로, 어노테이션 자체가 막는지를
 * 가려낸다. URL 규칙은 MockMvc 쪽 검사가 함께 본다.
 */
@WebMvcTest(com.example.travlediary.controller.admin.AdminCountryCategoryController.class)
@Import(SecurityConfig.class)
class AdminMethodSecurityTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private com.example.travlediary.controller.admin.AdminCountryCategoryController controller;

    @MockitoBean
    private CountryCategoryService countryCategoryService;
    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;

    /** 관리자는 그대로 지나간다. */
    @Test
    void anAdministratorPassesTheMethodCheck() {
        withAuthentication(UserRole.ADMIN, () ->
                assertThatCode(() -> controller.showIconForm(7L, new ConcurrentModel()))
                        .doesNotThrowAnyException());
    }

    /**
     * 일반 회원은 어노테이션 단계에서 막힌다.
     *
     * <p>URL 규칙을 지나지 않고 메서드를 직접 불러도 막혀야 실제로 2중 방어다.
     */
    @Test
    void anOrdinaryMemberIsRejectedByTheMethodCheck() {
        withAuthentication(UserRole.USER, () ->
                assertThatThrownBy(() -> controller.showIconForm(7L, new ConcurrentModel()))
                        .isInstanceOf(AccessDeniedException.class));
    }

    /** 로그인하지 않은 호출도 막힌다. (인증이 없다는 쪽으로 걸린다) */
    @Test
    void anAnonymousCallIsRejectedByTheMethodCheck() {
        SecurityContextHolder.clearContext();
        try {
            assertThatThrownBy(() -> controller.showIconForm(7L, new ConcurrentModel()))
                    .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** 어노테이션을 켜도 기존 URL 보안은 그대로다. */
    @Test
    void theExistingUrlRuleStillBlocksNonAdministrators() throws Exception {
        CountryCategory category = new CountryCategory();
        category.setId(7L);
        category.setRegionName("서울");
        when(countryCategoryService.getById(7L)).thenReturn(category);

        mockMvc.perform(get("/admin/region-categories/7/icon")
                        .with(authentication(token(UserRole.USER))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/region-categories/7/icon"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/admin/region-categories/7/icon")
                        .with(authentication(token(UserRole.ADMIN))))
                .andExpect(status().isOk());
    }

    /** 실제로 프록시가 걸려 어노테이션이 적용되었는지. (설정이 빠지면 이 단언이 먼저 깨진다) */
    @Test
    void theControllerIsActuallyGuardedByMethodSecurity() {
        assertThat(org.springframework.aop.support.AopUtils.isAopProxy(controller)).isTrue();
    }

    /* ===== 도우미 ===== */

    private void withAuthentication(UserRole role, Runnable body) {
        SecurityContextHolder.getContext().setAuthentication(token(role));
        try {
            body.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private UsernamePasswordAuthenticationToken token(UserRole role) {
        User user = new User();
        user.setId(role == UserRole.ADMIN ? 1L : 7L);
        user.setUserRole(role);
        user.setStatus(UserStatus.ACTIVE);
        CustomUserDetails principal = new CustomUserDetails(user);
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.copyOf(principal.getAuthorities()));
    }
}
