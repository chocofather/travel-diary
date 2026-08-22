package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.CategoryForm;
import com.example.travlediary.model.Category;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.category.CategoryInUseException;
import com.example.travlediary.service.category.CategoryService;
import com.example.travlediary.service.category.CategoryValidationException;
import com.example.travlediary.service.category.DuplicateCategoryNameException;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 사용 중인 카테고리는 삭제되지 않고, 사유가 목록에 표시된다.
 */
@WebMvcTest(AdminCategoryController.class)
@Import(SecurityConfig.class)
class AdminCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void deletingAnUnusedCategoryRedirectsWithoutAnError() throws Exception {
        mockMvc.perform(post("/admin/categories/7/delete").with(user(admin())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attributeCount(0));

        verify(categoryService).deleteCategory(7L);
    }

    @Test
    void deletingACategoryInUseShowsTheReasonOnTheList() throws Exception {
        doThrow(new CategoryInUseException(12)).when(categoryService).deleteCategory(anyLong());

        mockMvc.perform(post("/admin/categories/7/delete").with(user(admin())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attribute("error",
                        "현재 12개의 여행지에서 사용 중이라 삭제할 수 없습니다."));
    }

    @Test
    void createFormOffersEveryDestinationTypeWithKoreanLabels() throws Exception {
        mockMvc.perform(get("/admin/categories/create").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories/create"))
                .andExpect(model().attributeExists("categoryForm"))
                .andExpect(model().attribute("destinationTypes", DestinationType.values()))
                .andExpect(model().attribute("destinationTypeLabels",
                        org.hamcrest.Matchers.hasEntry(DestinationType.CAFE, "카페")))
                .andExpect(model().attribute("destinationTypeLabels",
                        org.hamcrest.Matchers.aMapWithSize(6)));
    }

    @Test
    void submitsTheNameAndSelectedTypesToTheServiceAndRedirects() throws Exception {
        mockMvc.perform(post("/admin/categories")
                        .with(user(admin())).with(csrf())
                        .param("name", "디저트")
                        .param("destinationTypes", "RESTAURANTS", "CAFE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"));

        ArgumentCaptor<CategoryForm> captor = ArgumentCaptor.forClass(CategoryForm.class);
        verify(categoryService).createCategory(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("디저트");
        assertThat(captor.getValue().getDestinationTypes())
                .containsExactly(DestinationType.RESTAURANTS, DestinationType.CAFE);
    }

    @Test
    void aDuplicateNameIsShownOnTheNameField() throws Exception {
        doThrow(new DuplicateCategoryNameException())
                .when(categoryService).createCategory(any(CategoryForm.class));

        mockMvc.perform(post("/admin/categories")
                        .with(user(admin())).with(csrf())
                        .param("name", "디저트")
                        .param("destinationTypes", "CAFE"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories/create"))
                .andExpect(model().attributeHasFieldErrors("categoryForm", "name"))
                // 입력값과 선택 옵션이 그대로 복원된다
                .andExpect(model().attribute("categoryForm",
                        org.hamcrest.Matchers.hasProperty("name",
                                org.hamcrest.Matchers.equalTo("디저트"))))
                .andExpect(model().attribute("categoryForm",
                        org.hamcrest.Matchers.hasProperty("destinationTypes",
                                org.hamcrest.Matchers.equalTo(List.of(DestinationType.CAFE)))))
                .andExpect(model().attributeExists("destinationTypes"));
    }

    @Test
    void aMissingDestinationTypeIsShownOnItsOwnField() throws Exception {
        doThrow(new CategoryValidationException("destinationTypes", "적용 대상을 1개 이상 선택해 주세요."))
                .when(categoryService).createCategory(any(CategoryForm.class));

        mockMvc.perform(post("/admin/categories")
                        .with(user(admin())).with(csrf())
                        .param("name", "디저트"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories/create"))
                .andExpect(model().attributeHasFieldErrors("categoryForm", "destinationTypes"))
                .andExpect(model().attributeExists("destinationTypes"));
    }

    @Test
    void aBlankNameIsShownOnTheNameField() throws Exception {
        doThrow(new CategoryValidationException("name", "카테고리 이름을 입력해 주세요."))
                .when(categoryService).createCategory(any(CategoryForm.class));

        mockMvc.perform(post("/admin/categories")
                        .with(user(admin())).with(csrf())
                        .param("name", "  ")
                        .param("destinationTypes", "CAFE"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("categoryForm", "name"));
    }

    @Test
    void editFormRestoresTheStoredNameAndCheckedTypes() throws Exception {
        CategoryForm stored = new CategoryForm();
        stored.setId(12L);
        stored.setName("디저트");
        stored.setDestinationTypes(List.of(DestinationType.RESTAURANTS, DestinationType.CAFE));
        when(categoryService.getCategoryForm(12L)).thenReturn(stored);

        mockMvc.perform(get("/admin/categories/12/edit").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories/edit"))
                .andExpect(model().attribute("categoryForm", stored))
                .andExpect(model().attribute("categoryId", 12L))
                .andExpect(model().attribute("destinationTypes", DestinationType.values()))
                .andExpect(model().attributeExists("destinationTypeLabels"));
    }

    @Test
    void submitsTheEditToTheServiceAndRedirects() throws Exception {
        mockMvc.perform(post("/admin/categories/12/edit")
                        .with(user(admin())).with(csrf())
                        .param("name", "디저트카페")
                        .param("destinationTypes", "CAFE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"));

        ArgumentCaptor<CategoryForm> captor = ArgumentCaptor.forClass(CategoryForm.class);
        verify(categoryService).updateCategory(captor.capture());
        // 경로의 id 를 그대로 쓴다
        assertThat(captor.getValue().getId()).isEqualTo(12L);
        assertThat(captor.getValue().getName()).isEqualTo("디저트카페");
        assertThat(captor.getValue().getDestinationTypes())
                .containsExactly(DestinationType.CAFE);
    }

    @Test
    void editValidationFailureKeepsWhatTheUserJustTyped() throws Exception {
        doThrow(new DuplicateCategoryNameException())
                .when(categoryService).updateCategory(any(CategoryForm.class));

        mockMvc.perform(post("/admin/categories/12/edit")
                        .with(user(admin())).with(csrf())
                        .param("name", "디저트카페")
                        .param("destinationTypes", "CAFE"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories/edit"))
                .andExpect(model().attributeHasFieldErrors("categoryForm", "name"))
                // DB 의 기존값이 아니라 방금 입력한 값이 남아야 한다
                .andExpect(model().attribute("categoryForm",
                        org.hamcrest.Matchers.hasProperty("name",
                                org.hamcrest.Matchers.equalTo("디저트카페"))))
                .andExpect(model().attribute("categoryForm",
                        org.hamcrest.Matchers.hasProperty("destinationTypes",
                                org.hamcrest.Matchers.equalTo(List.of(DestinationType.CAFE)))))
                .andExpect(model().attributeExists("destinationTypes"))
                .andExpect(model().attribute("categoryId", 12L));
        // 실패 경로에서 DB 값을 다시 읽어 덮어쓰지 않는다
        verify(categoryService, never()).getCategoryForm(anyLong());
    }

    @Test
    void aMissingDestinationTypeOnEditIsShownOnItsOwnField() throws Exception {
        doThrow(new CategoryValidationException("destinationTypes", "적용 대상을 1개 이상 선택해 주세요."))
                .when(categoryService).updateCategory(any(CategoryForm.class));

        mockMvc.perform(post("/admin/categories/12/edit")
                        .with(user(admin())).with(csrf())
                        .param("name", "디저트"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories/edit"))
                .andExpect(model().attributeHasFieldErrors("categoryForm", "destinationTypes"))
                .andExpect(model().attributeExists("destinationTypes"));
    }

    @Test
    void existingListAndCreateRoutesStayAvailable() throws Exception {
        Category category = new Category();
        category.setId(7L);
        category.setName("디저트");
        when(categoryService.getAll()).thenReturn(List.of(category));

        when(categoryService.getCategoryDestinationTypeTags())
                .thenReturn(Map.of(7L, "RESTAURANTS CAFE"));

        mockMvc.perform(get("/admin/categories").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories/list"))
                .andExpect(model().attribute("categories", List.of(category)))
                // badge 는 기존 태그 맵을 그대로 쓴다
                .andExpect(model().attribute("categoryTypeTags", Map.of(7L, "RESTAURANTS CAFE")))
                .andExpect(model().attribute("destinationTypeLabelsByName",
                        org.hamcrest.Matchers.hasEntry("RESTAURANTS", "식당")))
                .andExpect(model().attribute("destinationTypeLabelsByName",
                        org.hamcrest.Matchers.hasEntry("CAFE", "카페")))
                .andExpect(model().attribute("destinationTypeLabelsByName",
                        org.hamcrest.Matchers.aMapWithSize(6)));

        mockMvc.perform(get("/admin/categories/create").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories/create"))
                .andExpect(model().attributeExists("categoryForm"));
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
