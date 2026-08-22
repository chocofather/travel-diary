package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.AmenityForm;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.amenity.AmenityValidationException;
import com.example.travlediary.service.file.UnsupportedImageFormatException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 통합 편의시설 등록 화면. 등록 책임은 전부 AmenityService 에 있고
 * Controller 는 폼 바인딩과 오류 표시만 한다.
 */
@WebMvcTest(AdminAmenityController.class)
@Import(SecurityConfig.class)
class AdminAmenityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AmenityService amenityService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void createFormOffersAnEmptyFormAndEveryDestinationType() throws Exception {
        mockMvc.perform(get("/admin/amenities/create").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/amenities/create"))
                .andExpect(model().attributeExists("amenityForm"))
                .andExpect(model().attribute("destinationTypes", DestinationType.values()))
                .andExpect(model().attributeExists("destinationTypeLabels"));
    }

    @Test
    void submitsTheWholeFormToTheServiceAndRedirectsToTheList() throws Exception {
        mockMvc.perform(multipart("/admin/amenities/create")
                        .file(iconFile())
                        .with(user(admin())).with(csrf())
                        .param("code", "FREE_WIFI")
                        .param("nameKo", "무료 와이파이")
                        .param("nameEn", "Free Wi-Fi")
                        .param("nameJa", "無料Wi-Fi")
                        .param("nameZh", "免费Wi-Fi")
                        .param("destinationTypes", "CAFE", "ACCOMMODATION"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/amenities/list"));

        ArgumentCaptor<AmenityForm> captor = ArgumentCaptor.forClass(AmenityForm.class);
        verify(amenityService).registerAmenity(captor.capture());
        AmenityForm submitted = captor.getValue();
        assertThat(submitted.getCode()).isEqualTo("FREE_WIFI");
        assertThat(submitted.getNameKo()).isEqualTo("무료 와이파이");
        assertThat(submitted.getNameEn()).isEqualTo("Free Wi-Fi");
        assertThat(submitted.getNameJa()).isEqualTo("無料Wi-Fi");
        assertThat(submitted.getNameZh()).isEqualTo("免费Wi-Fi");
        assertThat(submitted.getDestinationTypes())
                .containsExactly(DestinationType.CAFE, DestinationType.ACCOMMODATION);
        assertThat(submitted.getIcon()).isNotNull();
        assertThat(submitted.getIcon().getOriginalFilename()).isEqualTo("icon.png");
    }

    @Test
    void validationFailureRedisplaysTheFormWithTheTypedValues() throws Exception {
        doThrow(new AmenityValidationException("code", "이미 등록된 편의시설 코드입니다."))
                .when(amenityService).registerAmenity(any(AmenityForm.class));

        mockMvc.perform(multipart("/admin/amenities/create")
                        .file(iconFile())
                        .with(user(admin())).with(csrf())
                        .param("code", "FREE_WIFI")
                        .param("nameKo", "무료 와이파이")
                        .param("nameEn", "Free Wi-Fi")
                        .param("destinationTypes", "CAFE"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/amenities/create"))
                // 파일 외 입력값은 그대로 복원된다
                .andExpect(model().attribute("amenityForm",
                        org.hamcrest.Matchers.hasProperty("code",
                                org.hamcrest.Matchers.equalTo("FREE_WIFI"))))
                .andExpect(model().attribute("amenityForm",
                        org.hamcrest.Matchers.hasProperty("nameKo",
                                org.hamcrest.Matchers.equalTo("무료 와이파이"))))
                .andExpect(model().attribute("amenityForm",
                        org.hamcrest.Matchers.hasProperty("destinationTypes",
                                org.hamcrest.Matchers.equalTo(List.of(DestinationType.CAFE)))))
                .andExpect(model().attributeExists("destinationTypes"))
                .andExpect(model().attributeHasFieldErrors("amenityForm", "code"));
    }

    @Test
    void iconValidationFailureIsShownOnTheIconField() throws Exception {
        doThrow(new UnsupportedImageFormatException("PNG 이미지 파일만 업로드할 수 있습니다."))
                .when(amenityService).registerAmenity(any(AmenityForm.class));

        mockMvc.perform(multipart("/admin/amenities/create")
                        .file(new MockMultipartFile("icon", "icon.jpg", "image/jpeg", new byte[]{1}))
                        .with(user(admin())).with(csrf())
                        .param("code", "FREE_WIFI")
                        .param("nameKo", "무료 와이파이")
                        .param("destinationTypes", "CAFE"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/amenities/create"))
                .andExpect(model().attributeHasFieldErrors("amenityForm", "icon"));
    }

    @Test
    void missingDestinationTypeIsShownOnItsOwnField() throws Exception {
        doThrow(new AmenityValidationException("destinationTypes", "적용 대상을 1개 이상 선택해 주세요."))
                .when(amenityService).registerAmenity(any(AmenityForm.class));

        mockMvc.perform(multipart("/admin/amenities/create")
                        .file(iconFile())
                        .with(user(admin())).with(csrf())
                        .param("code", "FREE_WIFI")
                        .param("nameKo", "무료 와이파이"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("amenityForm", "destinationTypes"));
    }

    @Test
    void existingTranslationRoutesStayAvailable() throws Exception {
        mockMvc.perform(get("/admin/amenities/3/translations/create").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/amenities/translation-create"));
        mockMvc.perform(get("/admin/amenities/3/translations").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/amenities/translation-list"));
        mockMvc.perform(get("/admin/amenities/list").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/amenities/list"));
    }

    private MockMultipartFile iconFile() {
        return new MockMultipartFile("icon", "icon.png", "image/png", new byte[]{1, 2, 3});
    }

    private CustomUserDetails admin() {
        User user = new User();
        user.setId(7L);
        user.setUsername("admin");
        user.setUserPassword("password");
        user.setUserRole(UserRole.ADMIN);
        return new CustomUserDetails(user);
    }
}
