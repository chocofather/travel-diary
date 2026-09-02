package com.example.travlediary.config;

import com.example.travlediary.controller.user.LoginController;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoginController.class)
@Import({SecurityConfig.class, CustomLoginSuccessHandler.class})
class FontResourceSecurityTest {

    private static final String FONT_URL = "/fonts/SpoqaHanSansNeo-Regular.woff2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;

    @Test
    void anonymousFontRequestReturnsInlineStaticResource() throws Exception {
        mockMvc.perform(get(FONT_URL))
                .andExpect(status().isOk())
                .andExpect(content().contentType("font/woff2"))
                .andExpect(header().doesNotExist("Content-Disposition"));
    }

    @Test
    void fontRequestDoesNotOverrideLoginSuccessRedirect() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(get(FONT_URL).session(session))
                .andExpect(status().isOk());

        User user = new User();
        user.setId(7L);
        user.setUsername("member");
        user.setUserRole(UserRole.USER);
        when(userMapper.findStatusById(7L)).thenReturn(UserStatus.ACTIVE);

        MockHttpServletRequest loginRequest = new MockHttpServletRequest();
        loginRequest.setSession(session);
        loginRequest.addParameter("redirect", "/destinations?type=domestic");
        MockHttpServletResponse loginResponse = new MockHttpServletResponse();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        var authentication = new UsernamePasswordAuthenticationToken(
                userDetails, userDetails.getPassword(), userDetails.getAuthorities());

        loginSuccessHandler.onAuthenticationSuccess(
                loginRequest, loginResponse, authentication);

        assertThat(loginResponse.getRedirectedUrl())
                .isEqualTo("/destinations?type=domestic");
    }
}
