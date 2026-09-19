package com.example.travlediary.controller;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.config.i18n.I18nConfig;
import com.example.travlediary.model.PolicyType;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.policy.SignupPolicy;
import com.example.travlediary.service.policy.SignupPolicyService;
import com.example.travlediary.service.policy.SignupPolicySet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(PolicyController.class)
@Import({SecurityConfig.class, I18nConfig.class})
class PolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SignupPolicyService signupPolicyService;
    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;

    @Test
    void anonymousUserCanReadTheActiveTermsOfService() throws Exception {
        SignupPolicy terms = policy(1L, PolicyType.TERMS_OF_SERVICE, "서비스 이용약관");
        SignupPolicy privacy = policy(2L, PolicyType.PRIVACY_POLICY, "개인정보처리방침");
        when(signupPolicyService.loadSignupPolicies())
                .thenReturn(new SignupPolicySet(List.of(terms, privacy)));

        mockMvc.perform(get("/terms"))
                .andExpect(status().isOk())
                .andExpect(view().name("policy/detail"))
                .andExpect(model().attribute("policy", terms))
                .andExpect(result -> {
                    var document = org.jsoup.Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select("main .policy-page h1").text())
                            .isEqualTo("서비스 이용약관");
                    assertThat(document.select(".policy-document").text())
                            .contains("TERMS_OF_SERVICE 본문");
                    assertThat(document.select(".footer-operations")).isEmpty();
                });
    }

    @Test
    void anonymousUserCanReadTheActivePrivacyPolicy() throws Exception {
        SignupPolicy terms = policy(1L, PolicyType.TERMS_OF_SERVICE, "서비스 이용약관");
        SignupPolicy privacy = policy(2L, PolicyType.PRIVACY_POLICY, "개인정보처리방침");
        when(signupPolicyService.loadSignupPolicies())
                .thenReturn(new SignupPolicySet(List.of(terms, privacy)));

        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(view().name("policy/detail"))
                .andExpect(model().attribute("policy", privacy))
                .andExpect(result -> {
                    var document = org.jsoup.Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select("main .policy-page h1").text())
                            .isEqualTo("개인정보처리방침");
                    assertThat(document.select(".policy-document").text())
                            .contains("PRIVACY_POLICY 본문");
                });
    }

    private SignupPolicy policy(long id, PolicyType type, String title) {
        return new SignupPolicy(
                id,
                type,
                "1.0",
                type != PolicyType.PRIVACY_POLICY,
                type == PolicyType.TERMS_OF_SERVICE,
                title,
                "<p>" + type.name() + " 본문</p>",
                "ko",
                LocalDateTime.of(2026, 9, 1, 0, 0));
    }
}
