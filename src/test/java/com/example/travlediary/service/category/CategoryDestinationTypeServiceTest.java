package com.example.travlediary.service.category;

import com.example.travlediary.model.Category;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.repository.category.CategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 유형별 카테고리 조회는 DestinationType enum 값을 그대로 매핑 조건으로 넘긴다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryDestinationTypeServiceTest {

    @Mock
    private CategoryMapper categoryMapper;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryMapper);
    }

    @Test
    void everyDestinationTypeIsQueriedWithItsOwnEnumName() {
        for (DestinationType type : DestinationType.values()) {
            when(categoryMapper.findByDestinationType(type.name()))
                    .thenReturn(List.of(category(1L, "역사유적")));

            assertThat(categoryService.getByDestinationType(type))
                    .extracting(Category::getName)
                    .containsExactly("역사유적");
            verify(categoryMapper).findByDestinationType(type.name());
        }
    }

    @Test
    void severalTypesAreMergedWithoutDuplicatesAndKeepIdOrder() {
        when(categoryMapper.findByDestinationType("RESTAURANTS"))
                .thenReturn(List.of(category(3L, "맛집"), category(7L, "야식")));
        when(categoryMapper.findByDestinationType("CAFE"))
                .thenReturn(List.of(category(3L, "맛집"), category(1L, "디저트")));

        List<Category> merged = categoryService.getByDestinationTypes(
                DestinationType.RESTAURANTS, DestinationType.CAFE);

        assertThat(merged).extracting(Category::getId).containsExactly(1L, 3L, 7L);
    }

    @Test
    void missingMappingsSimplyYieldAnEmptyTypeList() {
        when(categoryMapper.findByDestinationType("SHOP")).thenReturn(null);

        assertThat(categoryService.getByDestinationType(DestinationType.SHOP)).isEmpty();
    }

    @Test
    void theFullCategoryListKeepsItsExistingBehaviour() {
        when(categoryMapper.findAll())
                .thenReturn(List.of(category(1L, "역사유적"), category(9L, "미분류 카테고리")));

        assertThat(categoryService.getAll())
                .extracting(Category::getId)
                .containsExactly(1L, 9L);
    }

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }
}
