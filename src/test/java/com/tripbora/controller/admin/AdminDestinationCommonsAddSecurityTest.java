package com.tripbora.controller.admin;

import com.tripbora.config.CustomLoginSuccessHandler;
import com.tripbora.config.CustomLogoutSuccessHandler;
import com.tripbora.config.SecurityConfig;
import com.tripbora.repository.user.UserMapper;
import com.tripbora.service.destination.DestinationCommonsImageManagementService;
import com.tripbora.service.destination.DestinationImageService;
import com.tripbora.service.destination.DestinationKtoImageManagementService;
import com.tripbora.service.destination.DestinationService;
import com.tripbora.service.file.DestinationCardThumbnailService;
import com.tripbora.service.kto.KtoSelectedPhotoRequestParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 검색으로 고른 Commons 사진 추가도 기존 저장 요청과 같이 ADMIN 과 CSRF 토큰이 있어야 한다. */
@WebMvcTest(AdminDestinationImageController.class)
@Import(SecurityConfig.class)
class AdminDestinationCommonsAddSecurityTest {

    private static final String URL = "/admin/destinations/10/images/commons";
    private static final String SEARCH_JSON =
            "{\"source\":\"SEARCH\",\"photos\":[{\"fileName\":\"Petronas Towers.jpg\",\"main\":false}]}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DestinationImageService destinationImageService;
    @MockitoBean private DestinationService destinationService;
    @MockitoBean private KtoSelectedPhotoRequestParser ktoSelectedPhotoRequestParser;
    @MockitoBean private DestinationKtoImageManagementService ktoImageManagementService;
    @MockitoBean private DestinationCommonsImageManagementService commonsImageManagementService;
    @MockitoBean private DestinationCardThumbnailService cardThumbnailService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Test
    void onlyAdministratorsWithTheCsrfTokenCanAddCommonsPhotos() throws Exception {
        mockMvc.perform(post(URL).param("commonsSelectedPhotosJson", SEARCH_JSON)
                        .with(user("user").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(URL).param("commonsSelectedPhotosJson", SEARCH_JSON)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        verify(commonsImageManagementService, never()).addPhotos(any(), any());

        when(commonsImageManagementService.addPhotos(10L, SEARCH_JSON)).thenReturn(1);
        mockMvc.perform(post(URL).param("commonsSelectedPhotosJson", SEARCH_JSON)
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/destinations/10/images#commons-add"));
        verify(commonsImageManagementService).addPhotos(10L, SEARCH_JSON);
    }
}
