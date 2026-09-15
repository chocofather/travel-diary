package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.model.DiaryStickerAccessTier;
import com.example.travlediary.model.DiaryStickerCatalogItem;
import com.example.travlediary.model.DiaryStickerCategoryEntity;
import com.example.travlediary.model.DiaryStickerType;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.diary.DiaryStickerAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminDiaryStickerController.class)
@Import(SecurityConfig.class)
class AdminDiaryStickerControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiaryStickerAdminService service;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void adminListShowsPreviewAndVisibility() throws Exception {
        DiaryStickerCatalogItem sticker = new DiaryStickerCatalogItem();
        sticker.setId(4L);
        sticker.setName("별빛");
        sticker.setImageUrl("/uploads/diary-stickers/normal/star.png");
        sticker.setCategoryName("장식");
        sticker.setStickerType(DiaryStickerType.NORMAL);
        sticker.setAccessTier(DiaryStickerAccessTier.PREMIUM);
        sticker.setVisible(true);
        sticker.setDisplayOrder(1);
        when(service.getStickers(any())).thenReturn(List.of(sticker));

        mockMvc.perform(get("/admin/diary/stickers").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/diary-stickers/list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("별빛")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PREMIUM")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/uploads/diary-stickers/normal/star.png")));
    }

    @Test
    void adminListShowsCombinedFilterSelectionsAndResultCount() throws Exception {
        DiaryStickerCategoryEntity category = new DiaryStickerCategoryEntity();
        category.setId(3L);
        category.setName("장식");
        category.setVisible(true);
        category.setDisplayOrder(1);
        when(service.getStickers(any())).thenReturn(List.of());
        when(service.getCategories()).thenReturn(List.of(category));

        mockMvc.perform(get("/admin/diary/stickers")
                        .param("categoryId", "3")
                        .param("stickerType", "MASKING_TAPE")
                        .param("accessTier", "PREMIUM")
                        .param("visible", "false")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("검색 결과 0개")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "value=\"3\" selected=\"selected\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "value=\"MASKING_TAPE\" selected=\"selected\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "value=\"PREMIUM\" selected=\"selected\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "value=\"false\" selected=\"selected\"")));
    }

    @Test
    void adminListIncludesTheAsynchronousFilterScriptWithoutAnApplyButton() throws Exception {
        when(service.getStickers(any())).thenReturn(List.of());
        when(service.getCategories()).thenReturn(List.of());

        mockMvc.perform(get("/admin/diary/stickers").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/js/admin-diary-stickers.js")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(">적용<"))));
    }

    @Test
    void partialStickerListRequestRendersOnlyTheResultsArea() throws Exception {
        when(service.getStickers(any())).thenReturn(List.of());

        mockMvc.perform(get("/admin/diary/stickers")
                        .param("partial", "true")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/diary-stickers/list :: results"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("검색 결과 0개")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("스티커 관리"))));
    }

    @Test
    void regularUserCannotOpenStickerManagement() throws Exception {
        mockMvc.perform(get("/admin/diary/stickers").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanOpenStickerFormWithCatalogFields() throws Exception {
        DiaryStickerCategoryEntity category = new DiaryStickerCategoryEntity();
        category.setId(3L);
        category.setName("장식");
        category.setVisible(true);
        category.setDisplayOrder(1);
        when(service.getCategories()).thenReturn(List.of(category));

        mockMvc.perform(get("/admin/diary/stickers/new")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/diary-stickers/form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PNG 또는 WebP")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("MASKING_TAPE")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PREMIUM")));
    }

    @Test
    void adminCanOpenCategoryListAndCreateForm() throws Exception {
        when(service.getCategories()).thenReturn(List.of());

        mockMvc.perform(get("/admin/diary/stickers/categories")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/diary-stickers/category-list"));
        mockMvc.perform(get("/admin/diary/stickers/categories/new")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/diary-stickers/category-form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("카테고리명")));
    }

    @Test
    void createSendsMultipartFormToService() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "star.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});

        mockMvc.perform(multipart("/admin/diary/stickers")
                        .file(image)
                        .param("name", "별빛")
                        .param("categoryId", "3")
                        .param("stickerType", "NORMAL")
                        .param("accessTier", "FREE")
                        .param("visible", "true")
                        .param("displayOrder", "1")
                        .with(csrf())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/diary/stickers"));

        verify(service).create(any());
    }

    @Test
    void hideUsesPostAndReturnsToList() throws Exception {
        mockMvc.perform(post("/admin/diary/stickers/4/hide")
                        .with(csrf()).with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/diary/stickers"));
        verify(service).hide(4L);
    }
}
