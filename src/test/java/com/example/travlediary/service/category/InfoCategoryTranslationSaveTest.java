package com.example.travlediary.service.category;

import com.example.travlediary.dto.InfoCategoryForm;
import com.example.travlediary.dto.InfoCategoryTranslationForm;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.InfoCategoryTranslation;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.repository.category.InfoCategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 정보 카테고리 번역 저장 규칙을 본다.
 *
 * <p>한국어는 늘 카테고리명 입력과 같은 값이 되고, 나머지 언어는 값이 있으면 남고
 * 비면 그 언어 줄만 사라진다.
 */
@ExtendWith(MockitoExtension.class)
class InfoCategoryTranslationSaveTest {

    private static final String BASE_NAME = "계절여행";

    @Mock private InfoCategoryMapper infoCategoryMapper;

    private InfoCategoryService service;

    @BeforeEach
    void setUp() {
        service = new InfoCategoryService(infoCategoryMapper);
    }

    @Test
    void newFormStartsWithOneEmptySlotPerCanonicalLanguage() {
        assertThat(new InfoCategoryForm().getTranslations())
                .extracting(InfoCategoryTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        assertThat(new InfoCategoryForm().getTranslations())
                .allSatisfy(slot -> assertThat(slot.getName()).isEmpty());
    }

    @Test
    void createStoresForeignTranslationsWithTheBaseName() {
        givenGeneratedId(3L);
        when(infoCategoryMapper.findTranslationsByCategoryId(3L)).thenReturn(List.of());

        InfoCategoryForm form = categoryForm();
        setSlot(form, "en", "Seasonal travel");
        setSlot(form, "zh-TW", "季節旅行");

        service.create(form);

        assertThat(captureInserts())
                .extracting(InfoCategoryTranslation::getLanguageCode,
                        InfoCategoryTranslation::getName)
                .containsExactly(
                        tuple("ko", BASE_NAME),
                        tuple("en", "Seasonal travel"),
                        tuple("zh-TW", "季節旅行"));
    }

    @Test
    void koreanRowIsInsertedFromTheBaseNameWhenItDoesNotExistYet() {
        givenGeneratedId(3L);
        when(infoCategoryMapper.findTranslationsByCategoryId(3L)).thenReturn(List.of());

        service.create(categoryForm());

        List<InfoCategoryTranslation> inserted = captureInserts();
        assertThat(inserted).hasSize(1);
        assertThat(inserted.get(0).getInfoCategoryId()).isEqualTo(3L);
        assertThat(inserted.get(0).getLanguageCode()).isEqualTo("ko");
        assertThat(inserted.get(0).getName()).isEqualTo(BASE_NAME);
    }

    @Test
    void koreanRowIsUpdatedToStayInSyncWithTheBaseName() {
        givenExistingCategory(3L);
        when(infoCategoryMapper.update(any())).thenReturn(1);
        when(infoCategoryMapper.countByNameExcludingId("봄 여행", 3L)).thenReturn(0);
        when(infoCategoryMapper.findTranslationsByCategoryId(3L)).thenReturn(List.of(
                stored(1L, 3L, "ko", "예전 이름")));

        InfoCategoryForm form = categoryForm();
        form.setName("봄 여행");

        service.update(3L, form);

        List<InfoCategoryTranslation> updated = captureUpdates();
        assertThat(updated).hasSize(1);
        assertThat(updated.get(0).getLanguageCode()).isEqualTo("ko");
        assertThat(updated.get(0).getName()).isEqualTo("봄 여행");
        verify(infoCategoryMapper, never()).insertTranslation(any());
    }

    @Test
    void koreanSlotInTheTranslationFormsIsIgnoredSoTheBaseAlwaysWins() {
        givenGeneratedId(3L);
        when(infoCategoryMapper.findTranslationsByCategoryId(3L)).thenReturn(List.of());

        InfoCategoryForm form = categoryForm();
        setSlot(form, "ko", "폼이 보낸 이름");

        service.create(form);

        List<InfoCategoryTranslation> inserted = captureInserts();
        assertThat(inserted).hasSize(1);
        assertThat(inserted.get(0).getName()).isEqualTo(BASE_NAME);
    }

    @Test
    void newForeignRowIsInsertedAndAnExistingOneIsUpdated() {
        givenExistingCategory(3L);
        when(infoCategoryMapper.update(any())).thenReturn(1);
        when(infoCategoryMapper.countByNameExcludingId(BASE_NAME, 3L)).thenReturn(0);
        when(infoCategoryMapper.findTranslationsByCategoryId(3L)).thenReturn(List.of(
                stored(1L, 3L, "ko", BASE_NAME),
                stored(2L, 3L, "en", "Old name")));

        InfoCategoryForm form = categoryForm();
        setSlot(form, "en", "Seasonal travel");
        setSlot(form, "ja", "季節の旅");

        service.update(3L, form);

        assertThat(captureUpdates())
                .extracting(InfoCategoryTranslation::getLanguageCode,
                        InfoCategoryTranslation::getName)
                .containsExactly(tuple("ko", BASE_NAME), tuple("en", "Seasonal travel"));
        assertThat(captureInserts())
                .extracting(InfoCategoryTranslation::getLanguageCode,
                        InfoCategoryTranslation::getName)
                .containsExactly(tuple("ja", "季節の旅"));
    }

    @Test
    void blankForeignNameDeletesOnlyThatLanguageRow() {
        givenExistingCategory(3L);
        when(infoCategoryMapper.update(any())).thenReturn(1);
        when(infoCategoryMapper.countByNameExcludingId(BASE_NAME, 3L)).thenReturn(0);
        when(infoCategoryMapper.findTranslationsByCategoryId(3L)).thenReturn(List.of(
                stored(1L, 3L, "ko", BASE_NAME),
                stored(2L, 3L, "en", "Old name"),
                stored(3L, 3L, "ja", "季節の旅")));

        InfoCategoryForm form = categoryForm();
        setSlot(form, "en", "   ");
        setSlot(form, "ja", "季節の旅");

        service.update(3L, form);

        verify(infoCategoryMapper, times(1)).deleteTranslation(3L, "en");
        verify(infoCategoryMapper, never()).deleteTranslation(3L, "ja");
        verify(infoCategoryMapper, never()).deleteTranslation(3L, "ko");
        assertThat(captureUpdates())
                .extracting(InfoCategoryTranslation::getLanguageCode)
                .containsExactly("ko", "ja");
    }

    @Test
    void blankForeignNameWithoutAnExistingRowDoesNothing() {
        givenGeneratedId(3L);
        when(infoCategoryMapper.findTranslationsByCategoryId(3L)).thenReturn(List.of());

        InfoCategoryForm form = categoryForm();
        setSlot(form, "en", "   ");

        service.create(form);

        assertThat(captureInserts())
                .extracting(InfoCategoryTranslation::getLanguageCode)
                .containsExactly("ko");
        verify(infoCategoryMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    @Test
    void everyForeignSlotIsOptionalSoTheBaseSaveStillWorks() {
        givenGeneratedId(3L);
        when(infoCategoryMapper.findTranslationsByCategoryId(3L)).thenReturn(List.of());

        service.create(categoryForm());

        verify(infoCategoryMapper).insert(any());
        verify(infoCategoryMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    @Test
    void unsupportedAndLegacyLanguageCodesAreNeverStored() {
        givenGeneratedId(3L);
        when(infoCategoryMapper.findTranslationsByCategoryId(3L)).thenReturn(List.of());

        InfoCategoryForm form = categoryForm();
        form.getTranslations().add(new InfoCategoryTranslationForm("zh", "季节旅行"));
        form.getTranslations().add(new InfoCategoryTranslationForm("en-US", "Seasonal"));
        form.getTranslations().add(new InfoCategoryTranslationForm("EN", "Seasonal"));
        form.getTranslations().add(new InfoCategoryTranslationForm("fr", "Voyage"));
        form.getTranslations().add(new InfoCategoryTranslationForm(null, "이름 없음"));

        service.create(form);

        assertThat(captureInserts())
                .extracting(InfoCategoryTranslation::getLanguageCode)
                .containsExactly("ko");
    }

    @Test
    void duplicateLanguageSlotsNeverCauseASecondInsert() {
        givenGeneratedId(3L);
        when(infoCategoryMapper.findTranslationsByCategoryId(3L)).thenReturn(List.of());

        InfoCategoryForm form = categoryForm();
        setSlot(form, "en", "First name");
        form.getTranslations().add(new InfoCategoryTranslationForm("en", "Second name"));

        service.create(form);

        // 같은 언어가 두 번 오면 앞의 값만 쓴다.
        assertThat(captureInserts())
                .extracting(InfoCategoryTranslation::getLanguageCode,
                        InfoCategoryTranslation::getName)
                .containsExactly(tuple("ko", BASE_NAME), tuple("en", "First name"));
    }

    @Test
    void existingTranslationsAreReadOnceNoMatterHowManyLanguagesAreSaved() {
        givenGeneratedId(3L);
        when(infoCategoryMapper.findTranslationsByCategoryId(3L)).thenReturn(List.of());

        InfoCategoryForm form = categoryForm();
        setSlot(form, "en", "Seasonal travel");
        setSlot(form, "ja", "季節の旅");
        setSlot(form, "zh-CN", "季节旅行");
        setSlot(form, "zh-TW", "季節旅行");

        service.create(form);

        verify(infoCategoryMapper, times(1)).findTranslationsByCategoryId(3L);
        verify(infoCategoryMapper, never()).findTranslationsByCategoryIds(any());
    }

    @Test
    void editScreenPreloadsStoredTranslationsAndLeavesMissingLanguagesEmpty() {
        when(infoCategoryMapper.findTranslationsByCategoryId(3L)).thenReturn(List.of(
                stored(1L, 3L, "ko", BASE_NAME),
                stored(2L, 3L, "en", "Seasonal travel")));

        List<InfoCategoryTranslationForm> slots = service.getTranslationForms(3L);

        assertThat(slots).extracting(InfoCategoryTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        assertThat(slot(slots, "en").getName()).isEqualTo("Seasonal travel");
        assertThat(slot(slots, "ja").getName()).isEmpty();
        assertThat(slot(slots, "zh-CN").getName()).isEmpty();
        assertThat(slot(slots, "zh-TW").getName()).isEmpty();
        verify(infoCategoryMapper, times(1)).findTranslationsByCategoryId(3L);
    }

    @Test
    void translationFormsForANewScreenAreAllEmptySlots() {
        assertThat(service.getTranslationForms(null))
                .extracting(InfoCategoryTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        verify(infoCategoryMapper, never()).findTranslationsByCategoryId(anyLong());
    }

    @Test
    void missingCategoryIdTouchesNothing() {
        service.saveTranslations(null, BASE_NAME,
                List.of(new InfoCategoryTranslationForm("en", "Seasonal travel")));

        verify(infoCategoryMapper, never()).findTranslationsByCategoryId(anyLong());
        verify(infoCategoryMapper, never()).insertTranslation(any());
        verify(infoCategoryMapper, never()).updateTranslation(any());
        verify(infoCategoryMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    private InfoCategoryForm categoryForm() {
        InfoCategoryForm form = new InfoCategoryForm();
        form.setName(BASE_NAME);
        form.setContentType(TravelInfoContentType.GENERAL);
        form.setDisplayOrder(1);
        form.setIsVisible(true);
        return form;
    }

    private void setSlot(InfoCategoryForm form, String languageCode, String name) {
        slot(form.getTranslations(), languageCode).setName(name);
    }

    private InfoCategoryTranslationForm slot(List<InfoCategoryTranslationForm> slots,
                                             String languageCode) {
        return slots.stream()
                .filter(candidate -> languageCode.equals(candidate.getLanguageCode()))
                .findFirst()
                .orElseThrow();
    }

    private void givenGeneratedId(Long id) {
        when(infoCategoryMapper.countByNameExcludingId(BASE_NAME, null)).thenReturn(0);
        doAnswer(invocation -> {
            invocation.getArgument(0, InfoCategory.class).setId(id);
            return 1;
        }).when(infoCategoryMapper).insert(any());
    }

    private void givenExistingCategory(Long id) {
        InfoCategory category = new InfoCategory();
        category.setId(id);
        category.setName(BASE_NAME);
        category.setContentType(TravelInfoContentType.GENERAL);
        category.setDisplayOrder(1);
        category.setIsVisible(true);
        when(infoCategoryMapper.findById(id)).thenReturn(category);
    }

    private InfoCategoryTranslation stored(Long id, Long infoCategoryId,
                                           String languageCode, String name) {
        InfoCategoryTranslation translation = new InfoCategoryTranslation();
        translation.setId(id);
        translation.setInfoCategoryId(infoCategoryId);
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        return translation;
    }

    private List<InfoCategoryTranslation> captureInserts() {
        ArgumentCaptor<InfoCategoryTranslation> captor =
                ArgumentCaptor.forClass(InfoCategoryTranslation.class);
        verify(infoCategoryMapper, atLeastOnce()).insertTranslation(captor.capture());
        return captor.getAllValues();
    }

    private List<InfoCategoryTranslation> captureUpdates() {
        ArgumentCaptor<InfoCategoryTranslation> captor =
                ArgumentCaptor.forClass(InfoCategoryTranslation.class);
        verify(infoCategoryMapper, atLeastOnce()).updateTranslation(captor.capture());
        return captor.getAllValues();
    }
}
