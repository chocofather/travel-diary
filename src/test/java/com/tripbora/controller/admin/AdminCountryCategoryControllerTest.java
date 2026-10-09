package com.tripbora.controller.admin;

import com.tripbora.config.CustomLoginSuccessHandler;
import com.tripbora.config.CustomLogoutSuccessHandler;
import com.tripbora.config.SecurityConfig;
import com.tripbora.dto.CountryCategoryForm;
import com.tripbora.model.CountryCategory;
import com.tripbora.repository.user.UserMapper;
import com.tripbora.service.category.CountryCategoryAdminService;
import com.tripbora.service.category.CountryCategoryBulkCreateService;
import com.tripbora.service.category.CountryCategoryBulkCreateService.Result;
import com.tripbora.service.category.CountryCategoryBulkCreateService.Row;
import com.tripbora.service.category.CountryCategoryBulkCreateService.Status;
import com.tripbora.service.category.CountryCategoryDeleteBlockedException;
import com.tripbora.service.category.CountryCategoryService;
import com.tripbora.service.category.CountryCategoryValidationException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    private CountryCategoryBulkCreateService countryCategoryBulkCreateService;
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
    void cardsCarryCurrentNamesForTheEditDialogAndTheHeaderOffersBulkAndMasterExport() throws Exception {
        CountryCategory berlin = region(100L, "베를린", 20L, 3);
        berlin.setNameEn("Berlin");
        berlin.setCode("DE-BER");
        when(countryCategoryService.getRegionsByDepthAndParent(3, 20L)).thenReturn(List.of(berlin));
        when(countryCategoryService.getById(20L)).thenReturn(region(20L, "독일", 2L, 2));
        when(countryCategoryAdminService.translationsByRegion(any()))
                .thenReturn(Map.of(100L, Map.of("ja", "ベルリン", "zh-TW", "柏林")));

        String html = mockMvc.perform(get("/admin/region-categories")
                        .param("type", "overseas").param("depth", "3").param("parentId", "20")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        Element edit = page.selectFirst(".admin-region-card-actions [data-region-edit-open]");
        assertThat(edit).isNotNull();
        assertThat(edit.attr("data-id")).isEqualTo("100");
        assertThat(edit.attr("data-name-ko")).isEqualTo("베를린");
        assertThat(edit.attr("data-name-en")).isEqualTo("Berlin");
        assertThat(edit.attr("data-name-ja")).isEqualTo("ベルリン");
        assertThat(edit.attr("data-name-zh-tw")).isEqualTo("柏林");
        assertThat(edit.hasAttr("data-name-zh-cn")).isFalse();
        assertThat(edit.attr("data-code")).isEqualTo("DE-BER");
        // 액션 순서: 아이콘 업로드 → 수정 → 삭제 (도시 목록에는 하위 보기가 없다)
        assertThat(page.select(".admin-region-card-actions > a, .admin-region-card-actions > button, "
                + ".admin-region-card-actions button[type=submit]").eachText())
                .containsExactly("아이콘 업로드", "수정", "삭제");
        // 수정 대화상자에는 부모·계층·코드 입력이 없다
        assertThat(page.select("#region-edit-dialog [name=parentId], #region-edit-dialog [name=depth], "
                + "#region-edit-dialog [name=code], #region-edit-dialog [name=subregion]")).isEmpty();
        assertThat(page.select("#region-edit-dialog").attr("data-action-base")).isEqualTo("/admin/region-categories/");
        // 마스터 내보내기는 여행지 JSON 일괄등록이 쓰는 기존 엔드포인트를 그대로 쓴다
        assertThat(page.select(".admin-page-actions a[download]").attr("href"))
                .isEqualTo("/admin/api/destinations/import-json/master");
        assertThat(page.select("[data-region-bulk-open]").text()).isEqualTo("지역 일괄 등록");
        assertThat(page.select("#region-bulk-dialog form").attr("action")).isEqualTo("/admin/region-categories/bulk");
        assertThat(page.select("#region-bulk-dialog textarea[name=json]")).hasSize(1);
    }

    @Test
    void rootCardsHaveNoEditButton() throws Exception {
        when(countryCategoryService.getRegionsByDepth(1)).thenReturn(List.of(region(2L, "유럽", null, 1)));

        String html = mockMvc.perform(get("/admin/region-categories")
                        .param("type", "overseas").param("depth", "1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).select("[data-region-edit-open]")).isEmpty();
    }

    @Test
    void bulkRegistrationShowsRowResultsAndKeepsTheInputWhenSomeRowsFail() throws Exception {
        Result result = new Result(null, List.of(
                new Row(1, "인도네시아", "중부 자바", Status.CREATED, null),
                new Row(2, "미국", "시애틀", Status.DUPLICATE, "같은 부모 아래에 이미 같은 이름의 지역이 있습니다: 시애틀")));
        when(countryCategoryBulkCreateService.create("[...]")).thenReturn(result);

        mockMvc.perform(post("/admin/region-categories/bulk")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("json", "[...]")
                        .param("listType", "overseas").param("listDepth", "3")
                        .param("listParentId", "20").param("listPage", "2"))
                .andExpect(redirectedUrl("/admin/region-categories?type=overseas&depth=3&parentId=20&page=2"))
                .andExpect(flash().attribute("bulkResult", result))
                .andExpect(flash().attribute("bulkJson", "[...]"));

        when(countryCategoryService.getRegionsByDepthAndParent(3, 20L)).thenReturn(List.of());
        String html = mockMvc.perform(get("/admin/region-categories")
                        .param("type", "overseas").param("depth", "3").param("parentId", "20")
                        .flashAttr("bulkResult", result).flashAttr("bulkJson", "[...]")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.select("#region-bulk-dialog").attr("data-open-on-load")).isEqualTo("true");
        assertThat(page.select(".admin-region-bulk-summary").text())
                .isEqualTo("총 2건 · 등록 1건 · 중복 1건 · 실패 0건");
        assertThat(page.select(".admin-region-bulk-rows li")).hasSize(2);
        assertThat(page.select(".admin-region-bulk-rows li.is-duplicate .admin-region-bulk-status").text())
                .isEqualTo("이미 존재");
        assertThat(page.select("#region-bulk-dialog textarea").text()).isEqualTo("[...]");
    }

    @Test
    void fullySuccessfulBulkRegistrationClearsTheInput() throws Exception {
        Result result = new Result(null, List.of(new Row(1, "인도네시아", "중부 자바", Status.CREATED, null)));
        when(countryCategoryBulkCreateService.create("[ok]")).thenReturn(result);

        mockMvc.perform(post("/admin/region-categories/bulk")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("json", "[ok]").param("listType", "overseas").param("listDepth", "1"))
                .andExpect(flash().attribute("bulkResult", result))
                .andExpect(flash().attributeCount(1));
    }

    @Test
    void editSavesAndReturnsToTheSameListPage() throws Exception {
        CountryCategory updated = region(100L, "베를린시", 20L, 3);
        when(countryCategoryAdminService.update(eq(100L), any(CountryCategoryForm.class))).thenReturn(updated);

        mockMvc.perform(post("/admin/region-categories/100/edit")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("regionName", "베를린시").param("nameEn", "Berlin City").param("nameJa", "ベルリン市")
                        .param("listType", "overseas").param("listDepth", "3")
                        .param("listParentId", "20").param("listPage", "2"))
                .andExpect(redirectedUrl("/admin/region-categories?type=overseas&depth=3&parentId=20&page=2"))
                .andExpect(flash().attribute("message", "'베를린시' 지역을 수정했습니다."));

        ArgumentCaptor<CountryCategoryForm> form = ArgumentCaptor.forClass(CountryCategoryForm.class);
        verify(countryCategoryAdminService).update(eq(100L), form.capture());
        assertThat(form.getValue().getNameJa()).isEqualTo("ベルリン市");
    }

    @Test
    void invalidEditReopensTheDialogWithTheInputAndReason() throws Exception {
        when(countryCategoryAdminService.update(eq(100L), any(CountryCategoryForm.class)))
                .thenThrow(new CountryCategoryValidationException("regionName", "같은 부모 아래에 이미 같은 이름의 지역이 있습니다: 뮌헨"));

        mockMvc.perform(post("/admin/region-categories/100/edit")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .param("regionName", "뮌헨").param("nameEn", "Berlin")
                        .param("listType", "overseas").param("listDepth", "3").param("listParentId", "20"))
                .andExpect(redirectedUrl("/admin/region-categories?type=overseas&depth=3&parentId=20"))
                .andExpect(flash().attribute("editFormError", "같은 부모 아래에 이미 같은 이름의 지역이 있습니다: 뮌헨"))
                .andExpect(flash().attribute("editRegionId", 100L))
                .andExpect(flash().attributeExists("editForm"));

        CountryCategoryForm submitted = new CountryCategoryForm();
        submitted.setRegionName("뮌헨");
        submitted.setNameEn("Berlin");
        when(countryCategoryService.getRegionsByDepthAndParent(3, 20L)).thenReturn(List.of());
        String html = mockMvc.perform(get("/admin/region-categories")
                        .param("type", "overseas").param("depth", "3").param("parentId", "20")
                        .flashAttr("editForm", submitted).flashAttr("editRegionId", 100L)
                        .flashAttr("editFormError", "중복")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document page = Jsoup.parse(html);
        assertThat(page.select("#region-edit-dialog").attr("data-open-on-load")).isEqualTo("true");
        assertThat(page.select("#region-edit-dialog form").attr("action")).isEqualTo("/admin/region-categories/100/edit");
        assertThat(page.select("#region-edit-name-ko").val()).isEqualTo("뮌헨");
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
        mockMvc.perform(post("/admin/region-categories/bulk")
                        .with(user("member").roles("USER")).with(csrf())
                        .param("json", "[]"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/region-categories/100/edit")
                        .with(user("member").roles("USER")).with(csrf())
                        .param("regionName", "x"))
                .andExpect(status().isForbidden());
        verify(countryCategoryBulkCreateService, never()).create(any());
        verify(countryCategoryAdminService, never()).update(any(), any());
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
