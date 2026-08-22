package com.example.travlediary.service.category;

import com.example.travlediary.dto.CategoryForm;
import com.example.travlediary.model.Category;
import com.example.travlediary.model.CategoryDestinationType;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.repository.category.CategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 카테고리 수정: 이름과 적용 대상을 한 트랜잭션에서 갱신한다.
 * 매핑은 복합 PK 뿐이라 전체 삭제 후 선택값을 다시 넣는다.
 */
@ExtendWith(MockitoExtension.class)
class CategoryUpdateServiceTest {

    @Mock
    private CategoryMapper categoryMapper;
    @InjectMocks
    private CategoryService categoryService;

    @Test
    void editFormRestoresTheNameAndCheckedTypes() {
        givenCategory(12L, "디저트");
        when(categoryMapper.findCategoryDestinationTypesByCategoryId(12L)).thenReturn(List.of(
                mapping("RESTAURANTS"), mapping("CAFE")));

        CategoryForm form = categoryService.getCategoryForm(12L);

        assertThat(form.getId()).isEqualTo(12L);
        assertThat(form.getName()).isEqualTo("디저트");
        assertThat(form.getDestinationTypes())
                .containsExactly(DestinationType.RESTAURANTS, DestinationType.CAFE);
    }

    @Test
    void editFormOfACategoryWithoutAnyMappingComesBackEmpty() {
        givenCategory(12L, "디저트");
        when(categoryMapper.findCategoryDestinationTypesByCategoryId(12L)).thenReturn(List.of());

        assertThat(categoryService.getCategoryForm(12L).getDestinationTypes()).isEmpty();
    }

    @Test
    void updatesTheNameAndReplacesEveryMapping() {
        givenCategory(12L, "디저트");

        categoryService.updateCategory(form(12L, "  디저트카페  ",
                List.of(DestinationType.RESTAURANTS, DestinationType.CAFE)));

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryMapper).update(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(12L);
        assertThat(captor.getValue().getName()).isEqualTo("디저트카페");

        // 기존 매핑(ATTRACTION/SHOP 등)이 무엇이었든 선택값만 남는다
        verify(categoryMapper).deleteCategoryDestinationTypesByCategoryId(12L);
        verify(categoryMapper).insertCategoryDestinationType(12L, "RESTAURANTS");
        verify(categoryMapper).insertCategoryDestinationType(12L, "CAFE");
        verify(categoryMapper, times(2)).insertCategoryDestinationType(anyLong(), anyString());
    }

    @Test
    void keepingTheSameNameIsNotTreatedAsADuplicate() {
        givenCategory(12L, "디저트");
        // 자기 자신을 제외하므로 0건이다
        when(categoryMapper.countByNameExcludingId("디저트", 12L)).thenReturn(0);

        categoryService.updateCategory(form(12L, "디저트", List.of(DestinationType.CAFE)));

        verify(categoryMapper).countByNameExcludingId(eq("디저트"), eq(12L));
        verify(categoryMapper).update(any(Category.class));
    }

    @Test
    void rejectsANameAlreadyUsedByAnotherCategory() {
        givenCategory(12L, "디저트");
        when(categoryMapper.countByNameExcludingId("카페", 12L)).thenReturn(1);

        assertThatThrownBy(() ->
                categoryService.updateCategory(form(12L, "카페", List.of(DestinationType.CAFE))))
                .isInstanceOf(DuplicateCategoryNameException.class);

        verify(categoryMapper, never()).update(any());
        verify(categoryMapper, never()).deleteCategoryDestinationTypesByCategoryId(anyLong());
        verify(categoryMapper, never()).insertCategoryDestinationType(anyLong(), anyString());
    }

    @Test
    void translatesTheUniqueConstraintViolationIntoTheSameUserFacingError() {
        givenCategory(12L, "디저트");
        doAnswer(invocation -> {
            throw new DuplicateKeyException("Duplicate entry for key 'name_UNIQUE'");
        }).when(categoryMapper).update(any(Category.class));

        assertThatThrownBy(() ->
                categoryService.updateCategory(form(12L, "카페", List.of(DestinationType.CAFE))))
                .isInstanceOf(DuplicateCategoryNameException.class);

        verify(categoryMapper, never()).deleteCategoryDestinationTypesByCategoryId(anyLong());
    }

    @Test
    void insertsEachTypeOnlyOnceEvenWhenSubmittedTwice() {
        givenCategory(12L, "디저트");

        categoryService.updateCategory(form(12L, "디저트", Arrays.asList(
                DestinationType.CAFE, DestinationType.CAFE, null, DestinationType.SHOP)));

        verify(categoryMapper, times(1)).insertCategoryDestinationType(12L, "CAFE");
        verify(categoryMapper, times(1)).insertCategoryDestinationType(12L, "SHOP");
        verify(categoryMapper, times(2)).insertCategoryDestinationType(anyLong(), anyString());
    }

    @Test
    void rejectsInvalidInputBeforeWritingAnything() {
        givenCategory(12L, "디저트");

        for (String name : new String[]{null, "", "   ", "가".repeat(101)}) {
            assertThatThrownBy(() ->
                    categoryService.updateCategory(form(12L, name, List.of(DestinationType.CAFE))))
                    .as("name=%s", name)
                    .isInstanceOf(CategoryValidationException.class)
                    .extracting("field").isEqualTo("name");
        }

        assertThatThrownBy(() -> categoryService.updateCategory(form(12L, "디저트", List.of())))
                .isInstanceOf(CategoryValidationException.class)
                .extracting("field").isEqualTo("destinationTypes");

        CategoryForm nullTypes = form(12L, "디저트", List.of());
        nullTypes.setDestinationTypes(null);
        assertThatThrownBy(() -> categoryService.updateCategory(nullTypes))
                .isInstanceOf(CategoryValidationException.class)
                .extracting("field").isEqualTo("destinationTypes");

        verify(categoryMapper, never()).update(any());
        verify(categoryMapper, never()).deleteCategoryDestinationTypesByCategoryId(anyLong());
    }

    @Test
    void rejectsAnUnknownCategoryWithTheExistingNotFoundPolicy() {
        when(categoryMapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() ->
                categoryService.updateCategory(form(99L, "디저트", List.of(DestinationType.CAFE))))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> categoryService.getCategoryForm(99L))
                .isInstanceOf(ResponseStatusException.class);

        CategoryForm noId = form(null, "디저트", List.of(DestinationType.CAFE));
        assertThatThrownBy(() -> categoryService.updateCategory(noId))
                .isInstanceOf(CategoryValidationException.class);

        verify(categoryMapper, never()).update(any());
    }

    private void givenCategory(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        when(categoryMapper.findById(id)).thenReturn(category);
    }

    private CategoryDestinationType mapping(String destinationType) {
        CategoryDestinationType mapping = new CategoryDestinationType();
        mapping.setCategoryId(12L);
        mapping.setDestinationType(destinationType);
        return mapping;
    }

    private CategoryForm form(Long id, String name, List<DestinationType> types) {
        CategoryForm form = new CategoryForm();
        form.setId(id);
        form.setName(name);
        form.setDestinationTypes(types);
        return form;
    }
}
