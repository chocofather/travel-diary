package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.AdminTravelInfoListItemDto;
import com.example.travlediary.dto.FestivalCreateForm;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.category.InfoCategoryService;
import com.example.travlediary.service.travelinfo.TravelInfoService;
import com.example.travlediary.service.travelinfo.FestivalRegistrationService;
import com.example.travlediary.service.travelinfo.FestivalRegistrationResult;
import com.example.travlediary.service.travelinfo.FestivalValidationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminFestivalController.class)
@Import(SecurityConfig.class)
class AdminFestivalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TravelInfoService travelInfoService;
    @MockitoBean
    private InfoCategoryService infoCategoryService;
    @MockitoBean
    private FestivalRegistrationService festivalRegistrationService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void adminCanOpenFestivalOnlyListWithPeriodAndCategory() throws Exception {
        AdminTravelInfoListItemDto festival = new AdminTravelInfoListItemDto();
        festival.setId(20L);
        festival.setTitle("서울 재즈 페스티벌");
        festival.setScope(TravelInfoScope.DOMESTIC);
        festival.setContentType(TravelInfoContentType.FESTIVAL);
        festival.setCategoryId(4L);
        festival.setCategoryName("공연");
        festival.setStartDate(LocalDate.of(2026, 5, 1));
        festival.setEndDate(LocalDate.of(2026, 5, 3));
        festival.setViews(35);
        festival.setCreatedAt(Timestamp.valueOf("2026-04-01 10:00:00"));
        when(travelInfoService.getAdminList(TravelInfoScope.DOMESTIC,
                TravelInfoContentType.FESTIVAL, 4L)).thenReturn(List.of(festival));
        when(infoCategoryService.getAll()).thenReturn(List.of(
                category(4L, "공연", TravelInfoContentType.FESTIVAL),
                category(5L, "계절여행", TravelInfoContentType.GENERAL)));

        mockMvc.perform(get("/admin/festivals")
                        .param("scope", "DOMESTIC")
                        .param("categoryId", "4")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/festivals/list"))
                .andExpect(model().attribute("scope", TravelInfoScope.DOMESTIC))
                .andExpect(model().attribute("categoryId", 4L))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("서울 재즈 페스티벌")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("공연")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-05-01 ~ 2026-05-03")));

        verify(travelInfoService).getAdminList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.FESTIVAL, 4L);
    }

    @Test
    void nonAdminCannotOpenFestivalList() throws Exception {
        mockMvc.perform(get("/admin/festivals").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanOpenFestivalCreateFormWithFestivalCategoriesAndDomesticDefault() throws Exception {
        when(infoCategoryService.getAll()).thenReturn(List.of(
                category(4L, "공연", TravelInfoContentType.FESTIVAL),
                category(5L, "계절여행", TravelInfoContentType.GENERAL)));

        mockMvc.perform(get("/admin/festivals/create").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/festivals/form"))
                .andExpect(model().attribute("scope", TravelInfoScope.DOMESTIC))
                .andExpect(model().attributeExists("festivalForm"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("축제·행사 등록")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("공연")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("계절여행"))));

        verifyNoInteractions(travelInfoService);
    }

    @Test
    void validFestivalCreateUsesAdminIdAndRedirectsWithFlashMessage() throws Exception {
        when(festivalRegistrationService.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(7L)))
                .thenReturn(new FestivalRegistrationResult(10L, null));
        mockMvc.perform(post("/admin/festivals/create")
                        .with(user(adminDetails()))
                        .param("title", "서울 빛 축제")
                        .param("content", "<p>행사 소개</p>")
                        .param("scope", "DOMESTIC")
                        .param("categoryId", "5")
                        .param("startDate", "2026-12-01")
                        .param("endDate", "2026-12-31")
                        .param("eventPlace", "광화문광장")
                        .param("ktoFestivalContentId", "12345"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/festivals"))
                .andExpect(flash().attribute("festivalMessage", "축제·행사가 등록되었습니다."));

        ArgumentCaptor<FestivalCreateForm> captor = ArgumentCaptor.forClass(FestivalCreateForm.class);
        verify(festivalRegistrationService).create(captor.capture(), org.mockito.ArgumentMatchers.eq(7L));
        assertThat(captor.getValue().getKtoFestivalContentId()).isEqualTo("12345");
    }

    @Test
    void festivalCreateShowsImageWarningWithoutCancellingRegistration() throws Exception {
        when(festivalRegistrationService.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(7L)))
                .thenReturn(new FestivalRegistrationResult(10L,
                        "TourAPI 대표이미지를 저장하지 못해 이미지 없이 등록했습니다."));

        mockMvc.perform(post("/admin/festivals/create")
                        .with(user(adminDetails()))
                        .param("title", "이미지 경고 축제")
                        .param("scope", "DOMESTIC")
                        .param("categoryId", "5")
                        .param("startDate", "2026-12-01")
                        .param("endDate", "2026-12-31"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/festivals"))
                .andExpect(flash().attribute("festivalImageWarning",
                        "TourAPI 대표이미지를 저장하지 못해 이미지 없이 등록했습니다."));
    }

    @Test
    void festivalValidationFailureRendersSameFormWithInputAndFestivalCategories() throws Exception {
        when(infoCategoryService.getAll()).thenReturn(List.of(
                category(5L, "축제", TravelInfoContentType.FESTIVAL),
                category(8L, "여행추천", TravelInfoContentType.GENERAL)));
        doThrow(new FestivalValidationException("endDate", "행사 종료일은 시작일보다 빠를 수 없습니다."))
                .when(festivalRegistrationService).create(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(7L));

        mockMvc.perform(post("/admin/festivals/create")
                        .with(user(adminDetails()))
                        .param("title", "입력 유지 축제")
                        .param("content", "<p>소개</p>")
                        .param("scope", "DOMESTIC")
                        .param("categoryId", "5")
                        .param("startDate", "2026-12-31")
                        .param("endDate", "2026-12-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/festivals/form"))
                .andExpect(model().attributeHasFieldErrors("festivalForm", "endDate"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("입력 유지 축제")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("축제")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("여행추천"))));
    }

    private InfoCategory category(Long id, String name, TravelInfoContentType contentType) {
        InfoCategory category = new InfoCategory();
        category.setId(id);
        category.setName(name);
        category.setContentType(contentType);
        category.setIsVisible(true);
        return category;
    }

    private com.example.travlediary.security.CustomUserDetails adminDetails() {
        com.example.travlediary.model.User user = new com.example.travlediary.model.User();
        user.setId(7L);
        user.setUsername("admin");
        user.setUserPassword("password");
        user.setUserRole(com.example.travlediary.model.UserRole.ADMIN);
        return new com.example.travlediary.security.CustomUserDetails(user);
    }
}
