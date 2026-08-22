package com.example.travlediary.service.category;

import com.example.travlediary.dto.CategoryForm;
import com.example.travlediary.model.Category;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.repository.category.CategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 카테고리 통합 등록: categories 와 category_destination_types 를 한 번에 저장한다.
 */
@ExtendWith(MockitoExtension.class)
class CategoryCreateServiceTest {

    @Mock
    private CategoryMapper categoryMapper;
    @InjectMocks
    private CategoryService categoryService;

    @Test
    void savesTheNameAndEverySelectedDestinationType() {
        givenGeneratedId(9L);

        categoryService.createCategory(
                form("  디저트  ", List.of(DestinationType.RESTAURANTS, DestinationType.CAFE)));

        // trim 된 이름으로 중복 검사한다 (신규 등록이라 excludeId 는 null)
        verify(categoryMapper).countByNameExcludingId(eq("디저트"), isNull());

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("디저트");

        // 생성된 id 로 매핑을 넣고, enum 이름을 그대로 저장한다
        verify(categoryMapper).insertCategoryDestinationType(9L, "RESTAURANTS");
        verify(categoryMapper).insertCategoryDestinationType(9L, "CAFE");
        verify(categoryMapper, times(2)).insertCategoryDestinationType(anyLong(), anyString());
    }

    @Test
    void insertsEachTypeOnlyOnceEvenWhenSubmittedTwice() {
        givenGeneratedId(9L);

        categoryService.createCategory(form("디저트", Arrays.asList(
                DestinationType.CAFE, DestinationType.CAFE, null, DestinationType.RESTAURANTS)));

        verify(categoryMapper, times(1)).insertCategoryDestinationType(9L, "CAFE");
        verify(categoryMapper, times(1)).insertCategoryDestinationType(9L, "RESTAURANTS");
        verify(categoryMapper, times(2)).insertCategoryDestinationType(anyLong(), anyString());
    }

    @Test
    void rejectsAMissingOrTooLongNameBeforeInserting() {
        for (String name : new String[]{null, "", "   ", "가".repeat(101)}) {
            assertThatThrownBy(() ->
                    categoryService.createCategory(form(name, List.of(DestinationType.CAFE))))
                    .as("name=%s", name)
                    .isInstanceOf(CategoryValidationException.class)
                    .extracting("field").isEqualTo("name");
        }
        assertThatThrownBy(() -> categoryService.createCategory(null))
                .isInstanceOf(CategoryValidationException.class);

        verifyNoInteractions(categoryMapper);
    }

    @Test
    void rejectsAnEmptyDestinationTypeSelectionBeforeInserting() {
        assertThatThrownBy(() -> categoryService.createCategory(form("디저트", List.of())))
                .isInstanceOf(CategoryValidationException.class)
                .hasMessageContaining("적용 대상")
                .extracting("field").isEqualTo("destinationTypes");

        CategoryForm nullTypes = form("디저트", List.of());
        nullTypes.setDestinationTypes(null);
        assertThatThrownBy(() -> categoryService.createCategory(nullTypes))
                .isInstanceOf(CategoryValidationException.class)
                .extracting("field").isEqualTo("destinationTypes");

        verifyNoInteractions(categoryMapper);
    }

    @Test
    void rejectsADuplicateNameFoundByThePreCheck() {
        when(categoryMapper.countByNameExcludingId(eq("디저트"), isNull())).thenReturn(1);

        assertThatThrownBy(() ->
                categoryService.createCategory(form("디저트", List.of(DestinationType.CAFE))))
                .isInstanceOf(DuplicateCategoryNameException.class)
                .hasMessage("이미 등록된 카테고리 이름입니다.");

        verify(categoryMapper, never()).insert(any());
        verify(categoryMapper, never()).insertCategoryDestinationType(anyLong(), anyString());
    }

    @Test
    void translatesTheUniqueConstraintViolationIntoTheSameUserFacingError() {
        // 사전 검사와 INSERT 사이의 경합 상황
        doAnswer(invocation -> {
            throw new DuplicateKeyException("Duplicate entry for key 'name_UNIQUE'");
        }).when(categoryMapper).insert(any(Category.class));

        assertThatThrownBy(() ->
                categoryService.createCategory(form("디저트", List.of(DestinationType.CAFE))))
                .isInstanceOf(DuplicateCategoryNameException.class);

        verify(categoryMapper, never()).insertCategoryDestinationType(anyLong(), anyString());
    }

    private void givenGeneratedId(Long generatedId) {
        doAnswer(invocation -> {
            invocation.getArgument(0, Category.class).setId(generatedId);
            return null;
        }).when(categoryMapper).insert(any(Category.class));
    }

    private CategoryForm form(String name, List<DestinationType> types) {
        CategoryForm form = new CategoryForm();
        form.setName(name);
        form.setDestinationTypes(types);
        return form;
    }
}
