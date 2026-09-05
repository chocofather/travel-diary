package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.InfoCategoryForm;
import com.example.travlediary.dto.InfoCategoryTranslationForm;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.category.DuplicateInfoCategoryNameException;
import com.example.travlediary.service.category.InfoCategoryInUseException;
import com.example.travlediary.service.category.InfoCategoryService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminInfoCategoryController.class)
@Import(SecurityConfig.class)
class AdminInfoCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InfoCategoryService infoCategoryService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void adminCanOpenCategoryList() throws Exception {
        when(infoCategoryService.getAll()).thenReturn(List.of(category(1L, "계절여행", 1, true)));

        mockMvc.perform(get("/admin/info-categories").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/info-categories/list"))
                .andExpect(model().attributeExists("categories"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("정보 카테고리 관리")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("계절여행")));
    }

    @Test
    void createFormUsesExpectedDefaults() throws Exception {
        mockMvc.perform(get("/admin/info-categories/create").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/info-categories/form"))
                .andExpect(model().attribute("editMode", false))
                .andExpect(model().attribute("formAction", "/admin/info-categories"))
                .andExpect(result -> {
                    InfoCategoryForm form = (InfoCategoryForm) result.getModelAndView()
                            .getModel().get("infoCategoryForm");
                    assertThat(form.getDisplayOrder()).isEqualTo(1);
                    assertThat(form.getIsVisible()).isTrue();
                    assertThat(form.getContentType()).isEqualTo(TravelInfoContentType.GENERAL);
                });
    }

    @Test
    void blankNameReturnsFieldErrorAndKeepsForm() throws Exception {
        mockMvc.perform(post("/admin/info-categories")
                        .with(user("admin").roles("ADMIN"))
                        .param("name", "   ")
                        .param("displayOrder", "2")
                        .param("isVisible", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/info-categories/form"))
                .andExpect(model().attributeHasFieldErrors("infoCategoryForm", "name"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("카테고리명을 입력해 주세요.")));

        verify(infoCategoryService, never()).create(any());
    }

    @Test
    void oversizedNameReturnsFieldError() throws Exception {
        mockMvc.perform(post("/admin/info-categories")
                        .with(user("admin").roles("ADMIN"))
                        .param("name", "가".repeat(101))
                        .param("displayOrder", "1")
                        .param("isVisible", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("infoCategoryForm", "name"));

        verify(infoCategoryService, never()).create(any());
    }

    @Test
    void zeroDisplayOrderReturnsFieldError() throws Exception {
        mockMvc.perform(post("/admin/info-categories")
                        .with(user("admin").roles("ADMIN"))
                        .param("name", "여행준비")
                        .param("displayOrder", "0")
                        .param("isVisible", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("infoCategoryForm", "displayOrder"));

        verify(infoCategoryService, never()).create(any());
    }

    @Test
    void validCreateRedirectsToList() throws Exception {
        mockMvc.perform(post("/admin/info-categories")
                        .with(user("admin").roles("ADMIN"))
                        .param("name", "  여행준비  ")
                        .param("displayOrder", "3")
                        .param("contentType", "FESTIVAL")
                        .param("isVisible", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/info-categories"));

        verify(infoCategoryService).create(org.mockito.ArgumentMatchers.argThat(form ->
                form.getName().equals("여행준비")
                        && form.getDisplayOrder() == 3
                        && form.getContentType() == TravelInfoContentType.FESTIVAL
                        && !form.getIsVisible()));
    }

    @Test
    void duplicateNameReturnsNameFieldError() throws Exception {
        doThrow(new DuplicateInfoCategoryNameException())
                .when(infoCategoryService).create(any(InfoCategoryForm.class));

        mockMvc.perform(post("/admin/info-categories")
                        .with(user("admin").roles("ADMIN"))
                        .param("name", "여행준비")
                        .param("displayOrder", "1")
                        .param("isVisible", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/info-categories/form"))
                .andExpect(model().attributeHasFieldErrors("infoCategoryForm", "name"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("이미 사용 중인 카테고리명입니다.")));
    }

    @Test
    void validUpdateRedirectsToList() throws Exception {
        InfoCategory existing = category(7L, "핫플레이스", 2, true);
        when(infoCategoryService.getById(7L)).thenReturn(existing);

        mockMvc.perform(post("/admin/info-categories/edit/7")
                        .with(user("admin").roles("ADMIN"))
                        .param("name", "핫플레이스")
                        .param("displayOrder", "4")
                        .param("isVisible", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/info-categories"));

        verify(infoCategoryService).update(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.argThat(form ->
                        form.getDisplayOrder() == 4 && !form.getIsVisible()));
    }

    @Test
    void missingEditIdReturnsNotFoundForGetAndPost() throws Exception {
        when(infoCategoryService.getById(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "정보 카테고리를 찾을 수 없습니다."));

        mockMvc.perform(get("/admin/info-categories/edit/99")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/admin/info-categories/edit/99")
                        .with(user("admin").roles("ADMIN"))
                        .param("name", "여행준비")
                        .param("displayOrder", "1")
                        .param("isVisible", "true"))
                .andExpect(status().isNotFound());

        verify(infoCategoryService, never()).update(org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    void adminCanDeleteUnusedCategoryWithPost() throws Exception {
        mockMvc.perform(post("/admin/info-categories/7/delete")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/info-categories"));

        verify(infoCategoryService).delete(7L);
    }

    @Test
    void categoryInUseRedirectsWithFriendlyMessage() throws Exception {
        doThrow(new InfoCategoryInUseException()).when(infoCategoryService).delete(7L);

        mockMvc.perform(post("/admin/info-categories/7/delete")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/info-categories"))
                .andExpect(flash().attribute("error",
                        "이 카테고리를 사용하는 여행정보가 있어 삭제할 수 없습니다."));
    }

    @Test
    void missingDeleteIdReturnsNotFound() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "정보 카테고리를 찾을 수 없습니다."))
                .when(infoCategoryService).delete(99L);

        mockMvc.perform(post("/admin/info-categories/99/delete")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminUrlsRejectGuestAndNonAdminUser() throws Exception {
        mockMvc.perform(get("/admin/info-categories"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/admin/info-categories")
                        .with(user("member").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/info-categories/7/delete")
                        .with(user("member").roles("USER")))
                .andExpect(status().isForbidden());

        verify(infoCategoryService, never()).delete(7L);
    }

    @Test
    void createFormRendersEmptyForeignTranslationTabsWithoutAKoreanTab() throws Exception {
        when(infoCategoryService.getTranslationForms(null))
                .thenReturn(InfoCategoryTranslationForm.newTranslationSlots());

        String html = mockMvc.perform(get("/admin/info-categories/create")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Document document = Jsoup.parse(html);

        assertThat(document.select("[data-translation-tab]"))
                .extracting(element -> element.attr("data-translation-tab"))
                .containsExactly("en", "ja", "zh-CN", "zh-TW");
        assertThat(document.select("[data-translation-tab]"))
                .extracting(org.jsoup.nodes.Element::text)
                .containsExactly("영어", "일본어", "간체", "번체");
        assertThat(document.select("[data-translation-panel]"))
                .extracting(element -> element.attr("data-translation-panel"))
                .containsExactly("en", "ja", "zh-CN", "zh-TW");
        assertThat(document.select("[data-translation-name=en]").attr("value")).isEmpty();
        // 한국어 원본 입력은 그대로다
        assertThat(document.select("#info-category-name")).hasSize(1);
    }

    @Test
    void editFormPreloadsStoredTranslationsAndLeavesMissingLanguagesEmpty() throws Exception {
        when(infoCategoryService.getById(3L)).thenReturn(category(3L, "계절여행", 1, true));
        when(infoCategoryService.getTranslationForms(3L)).thenReturn(List.of(
                new InfoCategoryTranslationForm("ko", "계절여행"),
                new InfoCategoryTranslationForm("en", "Seasonal travel"),
                new InfoCategoryTranslationForm("ja", ""),
                new InfoCategoryTranslationForm("zh-CN", "季节旅行"),
                new InfoCategoryTranslationForm("zh-TW", "")));

        String html = mockMvc.perform(get("/admin/info-categories/edit/3")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Document document = Jsoup.parse(html);

        assertThat(document.select("#info-category-name").attr("value")).isEqualTo("계절여행");
        assertThat(document.select("[data-translation-name=en]").attr("value"))
                .isEqualTo("Seasonal travel");
        assertThat(document.select("[data-translation-name=zh-CN]").attr("value"))
                .isEqualTo("季节旅行");
        assertThat(document.select("[data-translation-name=ja]").attr("value")).isEmpty();
        assertThat(document.select("[data-translation-name=zh-TW]").attr("value")).isEmpty();
        verify(infoCategoryService).getTranslationForms(3L);
    }

    @Test
    void submittedForeignTranslationsReachTheService() throws Exception {
        mockMvc.perform(post("/admin/info-categories")
                        .with(user("admin").roles("ADMIN"))
                        .param("name", "계절여행")
                        .param("contentType", "GENERAL")
                        .param("displayOrder", "1")
                        .param("isVisible", "true")
                        .param("translations[0].languageCode", "ko")
                        .param("translations[0].name", "")
                        .param("translations[1].languageCode", "en")
                        .param("translations[1].name", "Seasonal travel")
                        .param("translations[2].languageCode", "ja")
                        .param("translations[2].name", "")
                        .param("translations[3].languageCode", "zh-CN")
                        .param("translations[3].name", "季节旅行")
                        .param("translations[4].languageCode", "zh-TW")
                        .param("translations[4].name", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/info-categories"));

        ArgumentCaptor<InfoCategoryForm> captor =
                ArgumentCaptor.forClass(InfoCategoryForm.class);
        verify(infoCategoryService).create(captor.capture());
        assertThat(captor.getValue().getTranslations())
                .extracting(InfoCategoryTranslationForm::getLanguageCode,
                        InfoCategoryTranslationForm::getName)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ko", ""),
                        org.assertj.core.groups.Tuple.tuple("en", "Seasonal travel"),
                        org.assertj.core.groups.Tuple.tuple("ja", ""),
                        org.assertj.core.groups.Tuple.tuple("zh-CN", "季节旅行"),
                        org.assertj.core.groups.Tuple.tuple("zh-TW", ""));
        // 기존 base 값 전달은 그대로다
        assertThat(captor.getValue().getName()).isEqualTo("계절여행");
        assertThat(captor.getValue().getContentType()).isEqualTo(TravelInfoContentType.GENERAL);
        assertThat(captor.getValue().getDisplayOrder()).isEqualTo(1);
        assertThat(captor.getValue().getIsVisible()).isTrue();
    }

    private InfoCategory category(Long id, String name, Integer displayOrder, Boolean isVisible) {
        InfoCategory category = new InfoCategory();
        category.setId(id);
        category.setName(name);
        category.setDisplayOrder(displayOrder);
        category.setIsVisible(isVisible);
        return category;
    }
}
