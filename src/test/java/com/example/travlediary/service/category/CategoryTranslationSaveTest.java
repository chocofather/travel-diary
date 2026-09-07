package com.example.travlediary.service.category;

import com.example.travlediary.dto.CategoryForm;
import com.example.travlediary.dto.CategoryTranslationForm;
import com.example.travlediary.model.Category;
import com.example.travlediary.model.CategoryTranslation;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.repository.category.CategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 여행지 카테고리 이름 번역 저장 규칙.
 *
 * <p>한국어는 categories.name 이 원문이라 번역 줄을 만들지 않는다.
 * 나머지 언어는 값이 있으면 남고(INSERT/UPDATE), 비우면 그 언어 줄만 사라진다(DELETE).
 */
@ExtendWith(MockitoExtension.class)
class CategoryTranslationSaveTest {

    @Mock
    private CategoryMapper categoryMapper;
    @InjectMocks
    private CategoryService categoryService;

    @Test
    void newFormStartsWithOneEmptySlotPerCanonicalLanguage() {
        assertThat(new CategoryForm().getTranslations())
                .extracting(CategoryTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        assertThat(new CategoryForm().getTranslations())
                .allSatisfy(slot -> assertThat(slot.getName()).isEmpty());
    }

    @Test
    void koreanOnlyCreateLeavesTheTranslationTableUntouched() {
        givenGeneratedId(9L);

        categoryService.createCategory(form("디저트"));

        verify(categoryMapper).insert(any(Category.class));
        verify(categoryMapper).insertCategoryDestinationType(9L, "CAFE");
        verify(categoryMapper, never()).insertTranslation(any());
        verify(categoryMapper, never()).updateTranslation(any());
        verify(categoryMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    @Test
    void createStoresOnlyTheFilledForeignLanguages() {
        givenGeneratedId(9L);
        CategoryForm form = form("디저트");
        setSlot(form, "en", "  Dessert  ");
        setSlot(form, "ja", "デザート");
        setSlot(form, "zh-CN", "   ");

        categoryService.createCategory(form);

        // 공백만 넣은 간체와 한국어 슬롯은 줄을 만들지 않는다. 값은 trim 해서 저장한다.
        assertThat(captureInserts())
                .extracting(CategoryTranslation::getCategoryId,
                        CategoryTranslation::getLanguageCode, CategoryTranslation::getName)
                .containsExactly(tuple(9L, "en", "Dessert"), tuple(9L, "ja", "デザート"));
        verify(categoryMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    @Test
    void createIgnoresAKoreanSlotSubmittedInTheTranslationInputs() {
        givenGeneratedId(9L);
        CategoryForm form = form("디저트");
        setSlot(form, "ko", "다른 한국어 이름");

        categoryService.createCategory(form);

        verify(categoryMapper, never()).insertTranslation(any());
    }

    @Test
    void updateInsertsMissingLanguagesAndUpdatesExistingOnes() {
        givenCategory(12L, "디저트");
        when(categoryMapper.findTranslationsByCategoryId(12L))
                .thenReturn(List.of(stored(1L, 12L, "en", "Old dessert")));

        CategoryForm form = form("디저트");
        form.setId(12L);
        setSlot(form, "en", "Dessert");
        setSlot(form, "ja", "デザート");

        categoryService.updateCategory(form);

        ArgumentCaptor<CategoryTranslation> updated =
                ArgumentCaptor.forClass(CategoryTranslation.class);
        verify(categoryMapper).updateTranslation(updated.capture());
        assertThat(updated.getValue().getLanguageCode()).isEqualTo("en");
        assertThat(updated.getValue().getName()).isEqualTo("Dessert");

        assertThat(captureInserts())
                .extracting(CategoryTranslation::getLanguageCode, CategoryTranslation::getName)
                .containsExactly(tuple("ja", "デザート"));
        verify(categoryMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    @Test
    void updateDeletesTheLanguageRowWhenTheAdminClearsIt() {
        givenCategory(12L, "디저트");
        when(categoryMapper.findTranslationsByCategoryId(12L)).thenReturn(List.of(
                stored(1L, 12L, "en", "Dessert"), stored(2L, 12L, "ja", "デザート")));

        CategoryForm form = form("디저트");
        form.setId(12L);
        setSlot(form, "en", "   ");
        setSlot(form, "ja", "デザート");

        categoryService.updateCategory(form);

        verify(categoryMapper).deleteTranslation(12L, "en");
        verify(categoryMapper, never()).deleteTranslation(12L, "ja");
        // 없던 언어를 비운 채로 두면 아무 것도 하지 않는다
        verify(categoryMapper, never()).deleteTranslation(12L, "zh-CN");
        verify(categoryMapper, never()).insertTranslation(any());
    }

    @Test
    void updateKeepsTheKoreanNameAndDestinationTypeMappingBehaviourUnchanged() {
        givenCategory(12L, "디저트");
        when(categoryMapper.findTranslationsByCategoryId(12L)).thenReturn(List.of());

        CategoryForm form = form("  디저트 카페  ");
        form.setId(12L);
        form.setDestinationTypes(List.of(DestinationType.CAFE, DestinationType.RESTAURANTS));
        setSlot(form, "en", "Dessert cafe");

        categoryService.updateCategory(form);

        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(categoryMapper).update(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("디저트 카페");
        verify(categoryMapper).deleteCategoryDestinationTypesByCategoryId(12L);
        verify(categoryMapper).insertCategoryDestinationType(12L, "CAFE");
        verify(categoryMapper).insertCategoryDestinationType(12L, "RESTAURANTS");
    }

    @Test
    void editFormLoadsStoredTranslationsIntoTheirLanguageSlots() {
        givenCategory(12L, "디저트");
        when(categoryMapper.findTranslationsByCategoryId(12L)).thenReturn(List.of(
                stored(1L, 12L, "en", "Dessert"), stored(2L, 12L, "zh-TW", "甜點")));

        CategoryForm form = categoryService.getCategoryForm(12L);

        assertThat(form.getTranslations())
                .extracting(CategoryTranslationForm::getLanguageCode,
                        CategoryTranslationForm::getName)
                .containsExactly(tuple("ko", ""), tuple("en", "Dessert"), tuple("ja", ""),
                        tuple("zh-CN", ""), tuple("zh-TW", "甜點"));
    }

    private List<CategoryTranslation> captureInserts() {
        ArgumentCaptor<CategoryTranslation> captor =
                ArgumentCaptor.forClass(CategoryTranslation.class);
        verify(categoryMapper, org.mockito.Mockito.atLeast(0)).insertTranslation(captor.capture());
        return captor.getAllValues();
    }

    private void givenGeneratedId(Long generatedId) {
        doAnswer(invocation -> {
            invocation.getArgument(0, Category.class).setId(generatedId);
            return null;
        }).when(categoryMapper).insert(any(Category.class));
    }

    private void givenCategory(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        when(categoryMapper.findById(id)).thenReturn(category);
    }

    private CategoryTranslation stored(Long id, Long categoryId, String languageCode, String name) {
        CategoryTranslation translation = new CategoryTranslation();
        translation.setId(id);
        translation.setCategoryId(categoryId);
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        return translation;
    }

    private CategoryForm form(String name) {
        CategoryForm form = new CategoryForm();
        form.setName(name);
        form.setDestinationTypes(List.of(DestinationType.CAFE));
        return form;
    }

    private void setSlot(CategoryForm form, String languageCode, String name) {
        form.getTranslations().stream()
                .filter(slot -> languageCode.equals(slot.getLanguageCode()))
                .forEach(slot -> slot.setName(name));
    }
}
