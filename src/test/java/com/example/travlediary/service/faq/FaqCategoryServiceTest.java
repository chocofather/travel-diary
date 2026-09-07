package com.example.travlediary.service.faq;

import com.example.travlediary.dto.FaqCategoryForm;
import com.example.travlediary.dto.FaqCategoryTranslationForm;
import com.example.travlediary.model.FaqCategory;
import com.example.travlediary.model.FaqCategoryTranslation;
import com.example.travlediary.repository.faq.FaqMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 FAQ 카테고리 등록·수정·삭제와 이름 번역 저장 규칙.
 *
 * <p>한국어 이름은 faq_categories 원문이고 번역은 en / ja / zh-CN / zh-TW 만 담는다.
 * 사용 중인 카테고리는 지우지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class FaqCategoryServiceTest {

    @Mock
    private FaqMapper faqMapper;

    private FaqCategoryService faqCategoryService;

    @BeforeEach
    void setUp() {
        faqCategoryService = new FaqCategoryService(faqMapper);
    }

    @Test
    void newFormStartsWithOneEmptySlotPerCanonicalLanguage() {
        assertThat(new FaqCategoryForm().getTranslations())
                .extracting(FaqCategoryTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
    }

    @Test
    void createStoresTheStrippedKoreanNameWithoutAnyTranslationRow() {
        givenGeneratedId(5L);

        assertThat(faqCategoryService.create(form("  회원/계정  "))).isEqualTo(5L);

        // 사전 중복 검사는 trim 된 이름으로 한다 (신규 등록이라 excludeId 는 null)
        verify(faqMapper).countCategoriesByNameExcludingId(eq("회원/계정"), isNull());
        ArgumentCaptor<FaqCategory> captor = ArgumentCaptor.forClass(FaqCategory.class);
        verify(faqMapper).insertCategory(captor.capture());
        assertThat(captor.getValue().getCategoryName()).isEqualTo("회원/계정");
        verify(faqMapper, never()).insertCategoryTranslation(any());
    }

    @Test
    void blankOrTooLongKoreanNameIsRejectedBeforeInserting() {
        for (String name : new String[]{null, "", "   ", "가".repeat(101)}) {
            assertThatThrownBy(() -> faqCategoryService.create(form(name)))
                    .as("name=%s", name)
                    .isInstanceOfSatisfying(FaqValidationException.class,
                            exception -> assertThat(exception.getField()).isEqualTo("categoryName"));
        }
        verify(faqMapper, never()).insertCategory(any());
    }

    @Test
    void duplicateKoreanNameFailsAsFieldValidationNotAsADatabaseError() {
        when(faqMapper.countCategoriesByNameExcludingId("회원/계정", null)).thenReturn(1);

        assertThatThrownBy(() -> faqCategoryService.create(form("회원/계정")))
                .isInstanceOfSatisfying(FaqValidationException.class, exception -> {
                    assertThat(exception.getField()).isEqualTo("categoryName");
                    assertThat(exception.getMessage()).contains("이미 등록된");
                });
        verify(faqMapper, never()).insertCategory(any());
    }

    @Test
    void updateExcludesItselfFromTheDuplicateCheck() {
        givenCategory(5L);

        faqCategoryService.update(5L, form("회원/계정"));

        verify(faqMapper).countCategoriesByNameExcludingId("회원/계정", 5L);
        ArgumentCaptor<FaqCategory> captor = ArgumentCaptor.forClass(FaqCategory.class);
        verify(faqMapper).updateCategory(captor.capture());
        assertThat(captor.getValue().getCategoryName()).isEqualTo("회원/계정");
    }

    @Test
    void createStoresOnlyTheFilledLanguagesAndNeverKorean() {
        givenGeneratedId(5L);
        FaqCategoryForm form = form("회원/계정");
        setSlot(form, "ko", "무시되는 이름");
        setSlot(form, "en", "  Account  ");
        setSlot(form, "ja", "アカウント");
        setSlot(form, "zh-CN", "   ");

        faqCategoryService.create(form);

        assertThat(captureInserts())
                .extracting(FaqCategoryTranslation::getFaqCategoryId,
                        FaqCategoryTranslation::getLanguageCode,
                        FaqCategoryTranslation::getCategoryName)
                .containsExactly(tuple(5L, "en", "Account"), tuple(5L, "ja", "アカウント"));
        verify(faqMapper, never()).deleteCategoryTranslation(anyLong(), anyString());
    }

    @Test
    void aTooLongTranslatedNameIsRejected() {
        givenGeneratedId(5L);
        FaqCategoryForm form = form("회원/계정");
        setSlot(form, "en", "A".repeat(101));

        assertThatThrownBy(() -> faqCategoryService.create(form))
                .isInstanceOfSatisfying(FaqValidationException.class,
                        exception -> assertThat(exception.getMessage()).contains("100자"));
        verify(faqMapper, never()).insertCategoryTranslation(any());
    }

    @Test
    void unsupportedLanguageCodesAreIgnored() {
        givenGeneratedId(5L);
        FaqCategoryForm form = form("회원/계정");
        form.getTranslations().add(new FaqCategoryTranslationForm("fr", "Compte"));
        form.getTranslations().add(new FaqCategoryTranslationForm(null, "Account"));

        faqCategoryService.create(form);

        verify(faqMapper, never()).insertCategoryTranslation(any());
    }

    @Test
    void updateInsertsMissingLanguagesUpdatesExistingOnesAndClearsEmptiedOnes() {
        givenCategory(5L);
        when(faqMapper.findCategoryTranslationsByCategoryId(5L)).thenReturn(List.of(
                stored(1L, 5L, "en", "Old account"),
                stored(2L, 5L, "zh-CN", "旧账户"),
                stored(3L, 5L, "zh-TW", "帳戶")));

        FaqCategoryForm form = form("회원/계정 관리");
        setSlot(form, "en", "Account");     // UPDATE
        setSlot(form, "ja", "アカウント");    // INSERT
        setSlot(form, "zh-CN", "   ");      // DELETE
        setSlot(form, "zh-TW", "帳戶");      // 그대로 UPDATE

        faqCategoryService.update(5L, form);

        ArgumentCaptor<FaqCategory> saved = ArgumentCaptor.forClass(FaqCategory.class);
        verify(faqMapper).updateCategory(saved.capture());
        assertThat(saved.getValue().getCategoryName()).isEqualTo("회원/계정 관리");

        ArgumentCaptor<FaqCategoryTranslation> updated =
                ArgumentCaptor.forClass(FaqCategoryTranslation.class);
        verify(faqMapper, atLeast(1)).updateCategoryTranslation(updated.capture());
        assertThat(updated.getAllValues())
                .extracting(FaqCategoryTranslation::getLanguageCode,
                        FaqCategoryTranslation::getCategoryName)
                .containsExactly(tuple("en", "Account"), tuple("zh-TW", "帳戶"));

        assertThat(captureInserts())
                .extracting(FaqCategoryTranslation::getLanguageCode,
                        FaqCategoryTranslation::getCategoryName)
                .containsExactly(tuple("ja", "アカウント"));

        // 비운 언어 한 줄만 지운다. 다른 언어는 건드리지 않는다.
        verify(faqMapper).deleteCategoryTranslation(5L, "zh-CN");
        verify(faqMapper, never()).deleteCategoryTranslation(5L, "en");
        verify(faqMapper, never()).deleteCategoryTranslation(5L, "ja");
        verify(faqMapper, never()).deleteCategoryTranslation(5L, "zh-TW");
        verify(faqMapper, never()).deleteCategoryTranslation(5L, "ko");
    }

    @Test
    void editFormLoadsStoredTranslationsByLanguageCodeNotByRowOrder() {
        FaqCategory category = new FaqCategory();
        category.setId(5L);
        category.setCategoryName("회원/계정");
        when(faqMapper.findCategoryById(5L)).thenReturn(category);
        // 조회 순서가 뒤섞여 들어와도 언어 코드로 슬롯을 찾는다
        when(faqMapper.findCategoryTranslationsByCategoryId(5L)).thenReturn(List.of(
                stored(3L, 5L, "zh-TW", "帳戶"), stored(1L, 5L, "en", "Account")));

        FaqCategoryForm form = faqCategoryService.getForm(5L);

        assertThat(form.getCategoryName()).isEqualTo("회원/계정");
        assertThat(form.getTranslations())
                .extracting(FaqCategoryTranslationForm::getLanguageCode,
                        FaqCategoryTranslationForm::getCategoryName)
                .containsExactly(tuple("ko", ""), tuple("en", "Account"), tuple("ja", ""),
                        tuple("zh-CN", ""), tuple("zh-TW", "帳戶"));
    }

    @Test
    void deletingACategoryInUseIsBlockedAndKeepsTheCategory() {
        givenCategory(5L);
        when(faqMapper.countFaqsByCategoryId(5L)).thenReturn(3);

        assertThatThrownBy(() -> faqCategoryService.delete(5L))
                .isInstanceOf(FaqCategoryInUseException.class)
                .hasMessageContaining("3개의 FAQ");

        verify(faqMapper, never()).deleteCategory(anyLong());
        verify(faqMapper, never()).deleteFaq(anyLong());
    }

    @Test
    void deletingAnUnusedCategoryRemovesItAndLeavesTranslationsToTheDatabase() {
        givenCategory(5L);
        when(faqMapper.countFaqsByCategoryId(5L)).thenReturn(0);
        when(faqMapper.deleteCategory(5L)).thenReturn(1);

        faqCategoryService.delete(5L);

        verify(faqMapper).deleteCategory(5L);
        // 번역은 FK ON DELETE CASCADE 로 지워지므로 애플리케이션이 반복 삭제하지 않는다
        verify(faqMapper, never()).deleteCategoryTranslation(anyLong(), anyString());
    }

    private List<FaqCategoryTranslation> captureInserts() {
        ArgumentCaptor<FaqCategoryTranslation> captor =
                ArgumentCaptor.forClass(FaqCategoryTranslation.class);
        verify(faqMapper, org.mockito.Mockito.atLeast(0))
                .insertCategoryTranslation(captor.capture());
        return captor.getAllValues();
    }

    private void givenGeneratedId(Long generatedId) {
        doAnswer(invocation -> {
            invocation.getArgument(0, FaqCategory.class).setId(generatedId);
            return 1;
        }).when(faqMapper).insertCategory(any(FaqCategory.class));
    }

    private void givenCategory(Long id) {
        FaqCategory category = new FaqCategory();
        category.setId(id);
        category.setCategoryName("기존 카테고리");
        when(faqMapper.findCategoryById(id)).thenReturn(category);
        org.mockito.Mockito.lenient().when(faqMapper.updateCategory(category)).thenReturn(1);
    }

    private FaqCategoryTranslation stored(Long id, Long categoryId, String languageCode,
                                          String categoryName) {
        FaqCategoryTranslation translation = new FaqCategoryTranslation();
        translation.setId(id);
        translation.setFaqCategoryId(categoryId);
        translation.setLanguageCode(languageCode);
        translation.setCategoryName(categoryName);
        return translation;
    }

    private FaqCategoryForm form(String categoryName) {
        FaqCategoryForm form = new FaqCategoryForm();
        form.setCategoryName(categoryName);
        return form;
    }

    private void setSlot(FaqCategoryForm form, String languageCode, String categoryName) {
        form.getTranslations().stream()
                .filter(slot -> languageCode.equals(slot.getLanguageCode()))
                .forEach(slot -> slot.setCategoryName(categoryName));
    }
}
