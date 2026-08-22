package com.example.travlediary.service.category;

import com.example.travlediary.model.Category;
import com.example.travlediary.repository.category.CategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * categories -> destination_categories FK 가 ON DELETE CASCADE 라
 * DB 는 사용 중 카테고리 삭제를 막아주지 않는다. 사용 건수 확인이 유일한 방어선이다.
 */
@ExtendWith(MockitoExtension.class)
class CategoryDeleteServiceTest {

    @Mock
    private CategoryMapper categoryMapper;
    @InjectMocks
    private CategoryService categoryService;

    @Test
    void deletesACategoryThatNoDestinationUses() {
        givenCategory(7L);
        when(categoryMapper.countDestinationsByCategoryId(7L)).thenReturn(0);

        categoryService.deleteCategory(7L);

        verify(categoryMapper).deleteById(7L);
    }

    @Test
    void refusesToDeleteACategoryThatDestinationsStillUse() {
        givenCategory(7L);
        when(categoryMapper.countDestinationsByCategoryId(7L)).thenReturn(12);

        assertThatThrownBy(() -> categoryService.deleteCategory(7L))
                .isInstanceOf(CategoryInUseException.class)
                .hasMessage("현재 12개의 여행지에서 사용 중이라 삭제할 수 없습니다.")
                .extracting("usageCount").isEqualTo(12);

        // 검사에서 걸리면 DELETE 자체를 실행하지 않는다
        verify(categoryMapper, never()).deleteById(anyLong());
    }

    @Test
    void masterTypeMappingAloneDoesNotBlockDeletion() {
        givenCategory(7L);
        // category_destination_types 매핑이 있어도 여행지 사용 건수가 0이면 지울 수 있다.
        // 매핑은 DB CASCADE 로 정리되므로 서비스가 따로 지우지 않는다.
        when(categoryMapper.countDestinationsByCategoryId(7L)).thenReturn(0);

        categoryService.deleteCategory(7L);

        verify(categoryMapper).deleteById(7L);
        verify(categoryMapper, never()).deleteCategoryDestinationTypesByCategoryId(anyLong());
    }

    @Test
    void rejectsAnUnknownCategoryBeforeCountingAnything() {
        when(categoryMapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> categoryService.deleteCategory(99L))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> categoryService.deleteCategory(null))
                .isInstanceOf(ResponseStatusException.class);

        verify(categoryMapper, never()).countDestinationsByCategoryId(anyLong());
        verify(categoryMapper, never()).deleteById(anyLong());
    }

    @Test
    void theFormCarriesTheFieldsTheNextStepsNeed() {
        com.example.travlediary.dto.CategoryForm form = new com.example.travlediary.dto.CategoryForm();

        // 아직 화면에서 쓰지 않지만 등록/수정 단계의 기반이다
        assertThat(form.getId()).isNull();
        assertThat(form.getName()).isNull();
        assertThat(form.getDestinationTypes()).isEmpty();
    }

    private void givenCategory(Long id) {
        Category category = new Category();
        category.setId(id);
        category.setName("디저트");
        when(categoryMapper.findById(id)).thenReturn(category);
    }
}
