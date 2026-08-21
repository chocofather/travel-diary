package com.example.travlediary.service.category;

import com.example.travlediary.model.CategoryDestinationType;
import com.example.travlediary.repository.category.CategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 카테고리별 "적용 가능한 여행지 유형" 태그.
 * 화면 필터가 이름이 아니라 이 매핑으로만 동작하도록 서버에서 만들어 내려준다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryDestinationTypeTagTest {

    @Mock
    private CategoryMapper categoryMapper;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryMapper);
    }

    @Test
    void tagsAreGroupedPerCategory() {
        when(categoryMapper.findCategoryDestinationTypes()).thenReturn(List.of(
                mapping(1L, "ATTRACTION"),
                mapping(1L, "SHOP"),
                mapping(2L, "RESTAURANTS"),
                mapping(2L, "CAFE")));

        Map<Long, String> tags = categoryService.getCategoryDestinationTypeTags();

        assertThat(tags.get(1L)).isEqualTo("ATTRACTION SHOP");
        assertThat(tags.get(2L)).isEqualTo("RESTAURANTS CAFE");
    }

    @Test
    void aCategoryWithoutMappingSimplyHasNoTag() {
        when(categoryMapper.findCategoryDestinationTypes())
                .thenReturn(List.of(mapping(1L, "ACTIVITY")));

        Map<Long, String> tags = categoryService.getCategoryDestinationTypeTags();

        // 매핑이 없는 카테고리는 태그가 없어 [전체] 에서만 보인다
        assertThat(tags.get(9L)).isNull();
        assertThat(tags).containsOnlyKeys(1L);
    }

    @Test
    void emptyOrMissingMappingRowsAreSafe() {
        when(categoryMapper.findCategoryDestinationTypes()).thenReturn(null);

        assertThat(categoryService.getCategoryDestinationTypeTags()).isEmpty();
    }

    private CategoryDestinationType mapping(Long categoryId, String destinationType) {
        CategoryDestinationType mapping = new CategoryDestinationType();
        mapping.setCategoryId(categoryId);
        mapping.setDestinationType(destinationType);
        return mapping;
    }
}
