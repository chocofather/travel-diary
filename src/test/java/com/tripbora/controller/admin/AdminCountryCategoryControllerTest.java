package com.tripbora.controller.admin;

import com.tripbora.config.CustomLoginSuccessHandler;
import com.tripbora.config.CustomLogoutSuccessHandler;
import com.tripbora.config.SecurityConfig;
import com.tripbora.dto.CountryCategoryForm;
import com.tripbora.model.CountryCategory;
import com.tripbora.repository.user.UserMapper;
import com.tripbora.service.category.CountryCategoryAdminService;
import com.tripbora.service.category.CountryCategoryDeleteBlockedException;
import com.tripbora.service.category.CountryCategoryService;
import com.tripbora.service.category.CountryCategoryValidationException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCountryCategoryController.class)
@Import(SecurityConfig.class)
class AdminCountryCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CountryCategoryService countryCategoryService;
    @MockitoBean
    private CountryCategoryAdminService countryCategoryAdminService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void listShowsRegisterDialogWithServerParentsAndOneDeleteActionPerCard() throws Exception {
        when(countryCategoryService.getRegionsByDepthAndParent(3, 20L))
                .thenReturn(List.of(region(100L, "베를린", 20L, 3), region(101L, "뮌헨", 20L, 3)));
        when(countryCategoryService.getById(20L)).thenReturn(region(20L, "독일", 2L, 2));
        when(countryCategoryAdminService.getParentOptionGroups()).thenReturn(List.of(
                new CountryCategoryAdminService.ParentOptionGroup("유럽", false, List.of(
                        new CountryCategoryAdminService.ParentOption(2L, "유럽 → 국가", true, null),
                        new CountryCategoryAdminService.ParentOption(20L, "독일 → 도시/지역", false, null)))));

        String html = mockMvc.perform(get("/admin/region-categories")
                        .param("type", "overseas").param("depth", "3").param("parentId", "20")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.select("[data-region-dialog-open]").text()).isEqualTo("지역 등록");
        // depth 는 입력받지 않는다
        assertThat(page.select("#region-create-dialog [name=depth]")).isEmpty();
        assertThat(page.select("#region-parent option[selected]").attr("value")).isEqualTo("20");
        assertThat(page.select("#region-parent option[value=2]").attr("data-code-required")).isEqualTo("true");
        assertThat(page.select(".admin-item-card")).hasSize(2);
        assertThat(page.select(".admin-item-card form[action$=/delete]")).hasSize(2);
        assertThat(page.select("form[action=/admin/region-categories/100/delete]").attr("onsubmit"))
                .contains("이 지역을 삭제하시겠습니까?");
    }

    @Test
    void createdRegionRedirectsToTheListThatShowsIt() throws Exception {
        CountryCategory created = region(500L, "슈방가우", 20L, 3);
        when(countryCategoryAdminService.create(any(CountryCategoryForm.class))).thenReturn(created);
        when(countryCategoryService.getRegionPath(500L)).thenReturn(List.of(
                region(2L, "유럽", null, 1), region(20L, "독일", 2L, 2), created));
        when(countryCategoryService.getDomesticRootIds()).thenReturn(List.of(7L));
        when(countryCategoryService.getRegionsByDepthAndParent(3, 20L)).thenReturn(List.of(created));

        mockMvc.perform(post("/admin/region-categories")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("parentId", "20")
                        .param("regionName", "슈방가우")
                        .param("nameEn", "Schwangau")
                        .param("listType", "overseas").param("listDepth", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/region-categories?type=overseas&depth=3&parentId=20"))
                .andExpect(flash().attribute("message", "'슈방가우' 지역을 등록했습니다."));
    }

    @Test
    void invalidRegistrationReturnsToTheSameListWithTheInputAndReason() throws Exception {
        when(countryCategoryAdminService.create(any(CountryCategoryForm.class)))
                .thenThrow(new CountryCategoryValidationException("regionName", "같은 부모 아래에 이미 같은 이름의 지역이 있습니다: 베를린"));

        mockMvc.perform(post("/admin/region-categories")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("parentId", "20")
                        .param("regionName", "베를린")
                        .param("nameEn", "Berlin")
                        .param("listType", "overseas").param("listDepth", "3").param("listParentId", "20"))
                .andExpect(redirectedUrl("/admin/region-categories?type=overseas&depth=3&parentId=20"))
                .andExpect(flash().attribute("regionFormError", "같은 부모 아래에 이미 같은 이름의 지역이 있습니다: 베를린"))
                .andExpect(flash().attributeExists("regionForm"));
    }

    @Test
    void blockedDeleteShowsTheReasonOnTheList() throws Exception {
        when(countryCategoryAdminService.delete(20L))
                .thenThrow(new CountryCategoryDeleteBlockedException("하위 지역이 존재하여 삭제할 수 없습니다."));

        mockMvc.perform(post("/admin/region-categories/20/delete")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("listType", "overseas").param("listDepth", "2")
                        .param("listParentId", "2").param("listPage", "2"))
                .andExpect(redirectedUrl("/admin/region-categories?type=overseas&depth=2&parentId=2&page=2"))
                .andExpect(flash().attribute("error", "하위 지역이 존재하여 삭제할 수 없습니다."));
        verify(countryCategoryAdminService).delete(20L);
    }

    @Test
    void nonAdminCannotCreateOrDelete() throws Exception {
        mockMvc.perform(post("/admin/region-categories/20/delete")
                        .with(user("member").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/region-categories")
                        .with(user("member").roles("USER")).with(csrf())
                        .param("parentId", "20"))
                .andExpect(status().isForbidden());
    }

    private static CountryCategory region(Long id, String name, Long parentId, int depth) {
        CountryCategory region = new CountryCategory();
        region.setId(id);
        region.setRegionName(name);
        region.setParentId(parentId);
        region.setDepth(depth);
        return region;
    }
}
