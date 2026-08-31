package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.AdminTravelInfoDetailDto;
import com.example.travlediary.dto.AdminTravelInfoListItemDto;
import com.example.travlediary.dto.InfoPeriodForm;
import com.example.travlediary.dto.TravelInfoForm;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.InfoPeriod;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.category.InfoCategoryService;
import com.example.travlediary.service.travelinfo.TravelInfoService;
import com.example.travlediary.service.travelinfo.TravelInfoValidationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminTravelInfoController.class)
@Import(SecurityConfig.class)
class AdminTravelInfoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TravelInfoService travelInfoService;
    @MockitoBean
    private InfoCategoryService infoCategoryService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void adminListAlwaysQueriesGeneralTravelInfo() throws Exception {
        AdminTravelInfoListItemDto item = new AdminTravelInfoListItemDto();
        item.setId(10L);
        item.setTitle("벚꽃 여행 가이드");
        item.setScope(TravelInfoScope.DOMESTIC);
        item.setContentType(TravelInfoContentType.GENERAL);
        item.setCategoryName("계절여행");
        item.setViews(12);
        item.setCreatedAt(Timestamp.valueOf("2026-04-01 10:00:00"));
        when(travelInfoService.getAdminList(TravelInfoScope.DOMESTIC,
                TravelInfoContentType.GENERAL, 3L)).thenReturn(List.of(item));
        when(infoCategoryService.getAll()).thenReturn(List.of(
                category(3L, "계절여행", true, TravelInfoContentType.GENERAL),
                category(4L, "축제", true, TravelInfoContentType.FESTIVAL)));

        mockMvc.perform(get("/admin/travel-info")
                        .param("scope", "DOMESTIC")
                        .param("contentType", "FESTIVAL")
                        .param("categoryId", "3")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/travel-info/list"))
                .andExpect(model().attribute("scope", TravelInfoScope.DOMESTIC))
                .andExpect(model().attribute("categoryId", 3L))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("벚꽃 여행 가이드")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("12")));

        verify(travelInfoService).getAdminList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.GENERAL, 3L);
    }

    @Test
    void adminCanOpenFestivalDetailWithMultiplePeriodsAndHtmlContent() throws Exception {
        AdminTravelInfoDetailDto detail = detail(TravelInfoContentType.FESTIVAL, List.of(
                infoPeriod("2026-04-01", "2026-04-03"),
                infoPeriod("2026-05-10", "2026-05-12")));
        detail.setContent("<p><span class=\"ql-font-noto-serif-kr\">축제 본문</span></p>"
                + "<img src=\"/uploads/editor/festival.png\" width=\"600\" alt=\"축제\">");
        when(travelInfoService.getAdminDetail(10L)).thenReturn(detail);

        mockMvc.perform(get("/admin/travel-info/10").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/travel-info/detail"))
                .andExpect(model().attribute("travelInfo", detail))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("벚꽃 축제")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("계절여행")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-04-01")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-04-03")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-05-10")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-05-12")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"admin-travel-info-content rich-text-content\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<p><span class=\"ql-font-noto-serif-kr\">축제 본문</span></p>"
                                + "<img src=\"/uploads/editor/festival.png\" width=\"600\" alt=\"축제\">")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("&lt;p&gt;축제 본문&lt;/p&gt;"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/admin/travel-info\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/admin/travel-info/edit/10\"")));
    }

    @Test
    void generalDetailDoesNotRenderPeriodSection() throws Exception {
        AdminTravelInfoDetailDto detail = detail(TravelInfoContentType.GENERAL, List.of());
        when(travelInfoService.getAdminDetail(10L)).thenReturn(detail);

        mockMvc.perform(get("/admin/travel-info/10").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("축제 기간"))));
    }

    @Test
    void missingDetailReturnsNotFound() throws Exception {
        when(travelInfoService.getAdminDetail(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/admin/travel-info/99").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void createFormShowsOnlyVisibleCategoriesAndGeneralDefault() throws Exception {
        when(infoCategoryService.getAll()).thenReturn(List.of(
                category(1L, "계절여행", true),
                category(2L, "숨김 분류", false)));

        mockMvc.perform(get("/admin/travel-info/create").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/travel-info/form"))
                .andExpect(model().attribute("editMode", false))
                .andExpect(model().attribute("formAction", "/admin/travel-info"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-kto-festival-autofill")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/js/admin-travel-info-festival-autofill.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("계절여행")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("숨김 분류"))))
                .andExpect(result -> {
                    TravelInfoForm form = (TravelInfoForm) result.getModelAndView().getModel()
                            .get("travelInfoForm");
                    assertThat(form.getContentType()).isEqualTo(TravelInfoContentType.GENERAL);
                });
    }

    @Test
    void createFormMarksOnlyMatchingContentTypeCategoriesAsSelectable() throws Exception {
        when(infoCategoryService.getAll()).thenReturn(List.of(
                category(1L, "일반 분류", true, TravelInfoContentType.GENERAL),
                category(2L, "축제 분류", true, TravelInfoContentType.FESTIVAL)));

        mockMvc.perform(get("/admin/travel-info/create").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = org.jsoup.Jsoup.parse(result.getResponse().getContentAsString());
                    var generalOption = document.selectFirst("#travel-info-category option[value='1']");
                    var festivalOption = document.selectFirst("#travel-info-category option[value='2']");

                    assertThat(generalOption).isNotNull();
                    assertThat(generalOption.attr("data-content-type")).isEqualTo("GENERAL");
                    assertThat(generalOption.hasAttr("disabled")).isFalse();
                    assertThat(festivalOption).isNotNull();
                    assertThat(festivalOption.attr("data-content-type")).isEqualTo("FESTIVAL");
                    assertThat(festivalOption.hasAttr("hidden")).isTrue();
                    assertThat(festivalOption.hasAttr("disabled")).isTrue();
                });
    }

    @Test
    void editFormKeepsCurrentlySelectedHiddenCategory() throws Exception {
        TravelInfoForm form = validForm();
        form.setCategoryId(2L);
        form.setContentType(TravelInfoContentType.FESTIVAL);
        InfoPeriodForm period = new InfoPeriodForm();
        period.setStartDate(LocalDate.parse("2026-04-01"));
        period.setEndDate(LocalDate.parse("2026-04-03"));
        form.setPeriods(List.of(period));
        when(travelInfoService.getForm(10L)).thenReturn(form);
        when(travelInfoService.getThumbnailUrl(10L))
                .thenReturn("/uploads/travel-info/thumbnails/current.jpg");
        when(infoCategoryService.getAll()).thenReturn(List.of(
                category(1L, "계절여행", true),
                category(2L, "기존 숨김 분류", false, TravelInfoContentType.FESTIVAL),
                category(3L, "다른 숨김 분류", false)));

        mockMvc.perform(get("/admin/travel-info/edit/10").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("editMode", true))
                .andExpect(model().attribute("formAction", "/admin/travel-info/edit/10"))
                .andExpect(model().attribute("currentThumbnailUrl",
                        "/uploads/travel-info/thumbnails/current.jpg"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "src=\"/uploads/travel-info/thumbnails/current.jpg\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("data-kto-festival-autofill"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(
                                "/js/admin-travel-info-festival-autofill.js"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("기존 숨김 분류 (숨김)")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("다른 숨김 분류"))));
    }

    @Test
    void validCreateUsesAuthenticatedAdminId() throws Exception {
        mockMvc.perform(post("/admin/travel-info")
                        .with(user(adminDetails()))
                        .param("title", "벚꽃 여행")
                        .param("content", "<p>본문</p>")
                        .param("scope", "DOMESTIC")
                        .param("contentType", "GENERAL")
                        .param("categoryId", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/travel-info"));

        verify(travelInfoService).create(any(TravelInfoForm.class), org.mockito.ArgumentMatchers.eq(7L));
    }

    @Test
    void multipartCreateBindsThumbnailToExistingTravelInfoForm() throws Exception {
        MockMultipartFile thumbnail = new MockMultipartFile(
                "thumbnailFile", "thumbnail.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});

        mockMvc.perform(multipart("/admin/travel-info")
                        .file(thumbnail)
                        .with(user(adminDetails()))
                        .param("title", "썸네일 여행")
                        .param("content", "<p>본문</p>")
                        .param("scope", "DOMESTIC")
                        .param("contentType", "GENERAL")
                        .param("categoryId", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/travel-info"));

        ArgumentCaptor<TravelInfoForm> captor = ArgumentCaptor.forClass(TravelInfoForm.class);
        verify(travelInfoService).create(captor.capture(), org.mockito.ArgumentMatchers.eq(7L));
        assertThat(captor.getValue().getThumbnailFile()).isNotNull();
        assertThat(captor.getValue().getThumbnailFile().getOriginalFilename()).isEqualTo("thumbnail.jpg");
    }

    @Test
    void bindingErrorKeepsInputAndDoesNotCallService() throws Exception {
        when(infoCategoryService.getAll()).thenReturn(List.of(category(3L, "계절여행", true)));

        mockMvc.perform(post("/admin/travel-info")
                        .with(user(adminDetails()))
                        .param("title", "입력 유지 제목")
                        .param("content", "<p>본문</p>")
                        .param("scope", "UNKNOWN")
                        .param("contentType", "GENERAL")
                        .param("categoryId", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/travel-info/form"))
                .andExpect(model().attributeHasFieldErrors("travelInfoForm", "scope"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("입력 유지 제목")));

        verify(travelInfoService, never()).create(any(), any());
    }

    @Test
    void serviceValidationReturnsFieldErrorOnSameForm() throws Exception {
        doThrow(new TravelInfoValidationException("content", "본문을 입력해 주세요."))
                .when(travelInfoService).create(any(), org.mockito.ArgumentMatchers.eq(7L));

        mockMvc.perform(post("/admin/travel-info")
                        .with(user(adminDetails()))
                        .param("title", "제목")
                        .param("content", "<p><br></p>")
                        .param("scope", "DOMESTIC")
                        .param("contentType", "GENERAL")
                        .param("categoryId", "3"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("travelInfoForm", "content"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("본문을 입력해 주세요.")));
    }

    @Test
    void validUpdateAndDeleteRedirectToList() throws Exception {
        when(travelInfoService.getById(10L)).thenReturn(new com.example.travlediary.model.TravelInfo());

        mockMvc.perform(post("/admin/travel-info/edit/10")
                        .with(user("admin").roles("ADMIN"))
                        .param("title", "수정 제목")
                        .param("content", "<p>수정 본문</p>")
                        .param("scope", "INTERNATIONAL")
                        .param("contentType", "FESTIVAL")
                        .param("categoryId", "3")
                        .param("periods[0].startDate", "2026-02-01")
                        .param("periods[0].endDate", "2026-02-10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/travel-info"));
        verify(travelInfoService).update(org.mockito.ArgumentMatchers.eq(10L), any());

        mockMvc.perform(post("/admin/travel-info/10/delete")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/travel-info"));
        verify(travelInfoService).delete(10L);
    }

    @Test
    void updateBindsThumbnailRemovalWithoutTrustingAClientImageUrl() throws Exception {
        when(travelInfoService.getById(10L)).thenReturn(new com.example.travlediary.model.TravelInfo());

        mockMvc.perform(post("/admin/travel-info/edit/10")
                        .with(user("admin").roles("ADMIN"))
                        .param("title", "수정 제목")
                        .param("content", "<p>수정 본문</p>")
                        .param("scope", "DOMESTIC")
                        .param("contentType", "GENERAL")
                        .param("categoryId", "3")
                        .param("removeThumbnail", "true")
                        .param("currentThumbnailUrl", "/uploads/travel-info/thumbnails/forged.jpg"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/travel-info"));

        ArgumentCaptor<TravelInfoForm> captor = ArgumentCaptor.forClass(TravelInfoForm.class);
        verify(travelInfoService).update(org.mockito.ArgumentMatchers.eq(10L), captor.capture());
        assertThat(captor.getValue().isRemoveThumbnail()).isTrue();
    }

    @Test
    void missingEditAndDeleteReturnNotFound() throws Exception {
        when(travelInfoService.getForm(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        when(travelInfoService.getById(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND))
                .when(travelInfoService).delete(99L);

        mockMvc.perform(get("/admin/travel-info/edit/99").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/admin/travel-info/edit/99")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/admin/travel-info/99/delete")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteIsNotAvailableByGet() throws Exception {
        mockMvc.perform(get("/admin/travel-info/10/delete")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isMethodNotAllowed());
        verify(travelInfoService, never()).delete(10L);
    }

    @Test
    void guestIsRedirectedAndRegularUserIsForbidden() throws Exception {
        mockMvc.perform(get("/admin/travel-info"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/admin/travel-info").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/travel-info/10/delete").with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
        verify(travelInfoService, never()).delete(10L);
    }

    private TravelInfoForm validForm() {
        TravelInfoForm form = new TravelInfoForm();
        form.setTitle("벚꽃 여행");
        form.setContent("<p>본문</p>");
        form.setScope(TravelInfoScope.DOMESTIC);
        form.setContentType(TravelInfoContentType.GENERAL);
        form.setCategoryId(3L);
        return form;
    }

    private AdminTravelInfoDetailDto detail(TravelInfoContentType contentType, List<InfoPeriod> periods) {
        AdminTravelInfoDetailDto detail = new AdminTravelInfoDetailDto();
        detail.setId(10L);
        detail.setTitle(contentType == TravelInfoContentType.FESTIVAL ? "벚꽃 축제" : "봄 여행 정보");
        detail.setContent("<p>본문</p>");
        detail.setScope(TravelInfoScope.DOMESTIC);
        detail.setContentType(contentType);
        detail.setCategoryId(3L);
        detail.setCategoryName("계절여행");
        detail.setViews(12);
        detail.setCreatedAt(Timestamp.valueOf("2026-04-01 10:00:00"));
        detail.setUpdatedAt(Timestamp.valueOf("2026-04-02 11:30:00"));
        detail.setPeriods(periods);
        return detail;
    }

    private InfoPeriod infoPeriod(String startDate, String endDate) {
        InfoPeriod period = new InfoPeriod();
        period.setInfoId(10L);
        period.setStartDate(LocalDate.parse(startDate));
        period.setEndDate(LocalDate.parse(endDate));
        return period;
    }

    private InfoCategory category(Long id, String name, boolean visible) {
        return category(id, name, visible, TravelInfoContentType.GENERAL);
    }

    private InfoCategory category(Long id,
                                  String name,
                                  boolean visible,
                                  TravelInfoContentType contentType) {
        InfoCategory category = new InfoCategory();
        category.setId(id);
        category.setName(name);
        category.setContentType(contentType);
        category.setDisplayOrder(id.intValue());
        category.setIsVisible(visible);
        return category;
    }

    private CustomUserDetails adminDetails() {
        User user = new User();
        user.setId(7L);
        user.setUsername("admin");
        user.setUserPassword("password");
        user.setUserRole(UserRole.ADMIN);
        return new CustomUserDetails(user);
    }
}
