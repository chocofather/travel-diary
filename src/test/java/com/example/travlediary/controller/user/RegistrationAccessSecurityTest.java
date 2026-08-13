package com.example.travlediary.controller.user;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest({UserController.class, LoginController.class})
@Import(SecurityConfig.class)
class RegistrationAccessSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserService userService;
    @MockitoBean private UserMapper userMapper;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;

    @Test
    void anonymousUserCanOpenRegistration() throws Exception {
        mockMvc.perform(get("/users/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));
    }

    @Test
    void authenticatedUserIsRedirectedAwayFromRegistration() throws Exception {
        mockMvc.perform(get("/users/register").with(user("member").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void authenticatedAdminIsRedirectedAwayFromRegistration() throws Exception {
        mockMvc.perform(get("/users/register").with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void authenticatedUserIsRedirectedAwayFromLogin() throws Exception {
        mockMvc.perform(get("/login").with(user("member").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }
}
