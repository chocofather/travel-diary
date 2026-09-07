package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.CategoryForm;
import com.example.travlediary.dto.CategoryTranslationForm;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.category.CategoryService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 여행지 카테고리 번역 입력 화면.
 *
 * <p>등록/수정이 같은 조각을 쓰고, 한국어는 위쪽 카테고리 이름 입력이 그대로 원문이다.
 */
@WebMvcTest(AdminCategoryController.class)
@Import(SecurityConfig.class)
class AdminCategoryTranslationFormTest {

    private static final List<String> LANGUAGES = List.of("en", "ja", "zh-CN", "zh-TW");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private CategoryService categoryService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Test
    void createFormRendersOneTabAndOneNameInputPerForeignLanguage() throws Exception {
        Document document = render(get("/admin/categories/create"));

        var tabs = document.select("[data-translation-tab]");
        assertThat(tabs.eachAttr("data-translation-tab")).containsExactlyElementsOf(LANGUAGES);
        assertThat(tabs.eachText()).containsExactly("영어", "일본어", "간체", "번체");
        // 처음에는 영어 탭만 열려 있다
        assertThat(tabs.get(0).hasClass("is-active")).isTrue();
        var panels = document.select("[data-translation-panel]");
        assertThat(panels.get(0).hasAttr("hidden")).isFalse();
        assertThat(panels.subList(1, panels.size()))
                .allSatisfy(panel -> assertThat(panel.hasAttr("hidden")).isTrue());

        // 한국어 슬롯(0번)은 번역 입력으로 그리지 않는다
        assertThat(document.select("[data-translation-name='ko']")).isEmpty();
        assertThat(document.select("[name='translations[0].name']")).isEmpty();
        for (int index = 0; index < LANGUAGES.size(); index++) {
            int slot = index + 1;
            assertThat(document.select("[data-translation-name='" + LANGUAGES.get(index) + "']")
                    .attr("name")).isEqualTo("translations[" + slot + "].name");
            assertThat(document.select("input[name='translations[" + slot + "].languageCode']")
                    .attr("value")).isEqualTo(LANGUAGES.get(index));
        }
        // 접기는 여행지 등록 화면과 같은 공통 스크립트를 쓴다
        assertThat(document.select("form[data-translation-collapsible]")).hasSize(1);
        assertThat(document.select("[data-translation-tabs-body]")).hasSize(1);
    }

    @Test
    void createSubmitsEveryLanguageSlotToTheService() throws Exception {
        mockMvc.perform(post("/admin/categories").with(user(admin())).with(csrf())
                        .param("name", "디저트")
                        .param("destinationTypes", "CAFE")
                        .param("translations[0].languageCode", "ko")
                        .param("translations[0].name", "")
                        .param("translations[1].languageCode", "en")
                        .param("translations[1].name", "Dessert")
                        .param("translations[2].languageCode", "ja")
                        .param("translations[2].name", "デザート")
                        .param("translations[3].languageCode", "zh-CN")
                        .param("translations[3].name", "")
                        .param("translations[4].languageCode", "zh-TW")
                        .param("translations[4].name", ""))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<CategoryForm> captor = ArgumentCaptor.forClass(CategoryForm.class);
        verify(categoryService).createCategory(captor.capture());
        assertThat(captor.getValue().getTranslations())
                .extracting(CategoryTranslationForm::getLanguageCode,
                        CategoryTranslationForm::getName)
                .containsExactly(tuple("ko", ""), tuple("en", "Dessert"), tuple("ja", "デザート"),
                        tuple("zh-CN", ""), tuple("zh-TW", ""));
        // 적용 대상 매핑은 그대로 실린다
        assertThat(captor.getValue().getDestinationTypes()).containsExactly(DestinationType.CAFE);
    }

    @Test
    void editFormShowsStoredTranslationsAndLeavesMissingLanguagesEmpty() throws Exception {
        CategoryForm form = new CategoryForm();
        form.setId(12L);
        form.setName("디저트");
        form.setDestinationTypes(List.of(DestinationType.CAFE));
        form.setTranslations(List.of(
                new CategoryTranslationForm("ko", ""),
                new CategoryTranslationForm("en", "Dessert"),
                new CategoryTranslationForm("ja", ""),
                new CategoryTranslationForm("zh-CN", ""),
                new CategoryTranslationForm("zh-TW", "甜點")));
        when(categoryService.getCategoryForm(12L)).thenReturn(form);

        Document document = render(get("/admin/categories/12/edit"));

        assertThat(document.select("#category-name").attr("value")).isEqualTo("디저트");
        assertThat(document.select("[data-translation-name='en']").attr("value"))
                .isEqualTo("Dessert");
        assertThat(document.select("[data-translation-name='zh-TW']").attr("value"))
                .isEqualTo("甜點");
        assertThat(document.select("[data-translation-name='ja']").attr("value")).isEmpty();
        assertThat(document.select("[data-translation-name='zh-CN']").attr("value")).isEmpty();
        assertThat(document.select("form[data-translation-collapsible]")).hasSize(1);
    }

    @Test
    void editSubmitsTheClearedLanguageAsAnEmptyValue() throws Exception {
        mockMvc.perform(post("/admin/categories/12/edit").with(user(admin())).with(csrf())
                        .param("name", "디저트")
                        .param("destinationTypes", "CAFE")
                        .param("translations[1].languageCode", "en")
                        .param("translations[1].name", "")
                        .param("translations[2].languageCode", "ja")
                        .param("translations[2].name", "デザート"))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<CategoryForm> captor = ArgumentCaptor.forClass(CategoryForm.class);
        verify(categoryService).updateCategory(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(12L);
        assertThat(captor.getValue().getTranslations())
                .extracting(CategoryTranslationForm::getLanguageCode,
                        CategoryTranslationForm::getName)
                .contains(tuple("en", ""), tuple("ja", "デザート"));
    }

    private Document render(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
                                    request) throws Exception {
        return Jsoup.parse(mockMvc.perform(request.with(user(admin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
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
