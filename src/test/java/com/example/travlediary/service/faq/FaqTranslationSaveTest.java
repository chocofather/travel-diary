package com.example.travlediary.service.faq;

import com.example.travlediary.dto.FaqForm;
import com.example.travlediary.dto.FaqTranslationForm;
import com.example.travlediary.model.Faq;
import com.example.travlediary.model.FaqCategory;
import com.example.travlediary.model.FaqTranslation;
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
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 FAQ 번역 저장 규칙.
 *
 * <p>한국어는 faqs 원문이 갖고, 나머지 언어는 값이 있으면 남고(INSERT/UPDATE)
 * 질문·답변을 모두 비우면 그 언어 줄만 사라진다(DELETE). ko 줄은 만들지 않는다.
 * 답변은 서식 없는 일반 텍스트라 HTML 정제를 하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class FaqTranslationSaveTest {

    @Mock
    private FaqMapper faqMapper;

    private FaqService faqService;

    @BeforeEach
    void setUp() {
        faqService = new FaqService(faqMapper, new FaqLocalizationService(faqMapper),
                new FaqCategoryLocalizationService(faqMapper));
    }

    @Test
    void newFormStartsWithOneEmptySlotPerCanonicalLanguage() {
        assertThat(new FaqForm().getTranslations())
                .extracting(FaqTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
    }

    @Test
    void koreanOnlyCreateLeavesTheTranslationTableUntouched() {
        givenCategory();
        givenGeneratedId(10L);

        faqService.create(form(), 7L);

        verify(faqMapper).insertFaq(any(Faq.class));
        verify(faqMapper, never()).insertTranslation(any());
        verify(faqMapper, never()).updateTranslation(any());
        verify(faqMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    @Test
    void createStoresOnlyTheFilledLanguagesAndNeverKorean() {
        givenCategory();
        givenGeneratedId(10L);
        FaqForm form = form();
        setSlot(form, "ko", "무시되는 질문", "무시되는 답변");
        setSlot(form, "en", "  How do I close my account?  ", "  Open the settings page.  ");
        setSlot(form, "zh-CN", "   ", "  ");

        faqService.create(form, 7L);

        assertThat(captureInserts())
                .extracting(FaqTranslation::getFaqId, FaqTranslation::getLanguageCode,
                        FaqTranslation::getQuestion, FaqTranslation::getAnswer)
                .containsExactly(tuple(10L, "en", "How do I close my account?",
                        "Open the settings page."));
        verify(faqMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    /** 부분 번역도 저장한다. 질문만, 답변만 넣어도 그 언어 줄이 남는다. */
    @Test
    void partialTranslationsAreStoredWithTheMissingFieldLeftEmpty() {
        givenCategory();
        givenGeneratedId(10L);
        FaqForm form = form();
        setSlot(form, "en", "Question only", "  ");
        setSlot(form, "ja", "", "回答のみ");

        faqService.create(form, 7L);

        assertThat(captureInserts())
                .extracting(FaqTranslation::getLanguageCode, FaqTranslation::getQuestion,
                        FaqTranslation::getAnswer)
                .containsExactly(tuple("en", "Question only", null),
                        tuple("ja", null, "回答のみ"));
    }

    @Test
    void translatedAnswerKeepsItsLineBreaksAndIsNotSanitized() {
        givenCategory();
        givenGeneratedId(10L);
        FaqForm form = form();
        setSlot(form, "en", "Question", "  First line\nSecond <b>line</b>  ");

        faqService.create(form, 7L);

        assertThat(captureInserts()).singleElement()
                .satisfies(translation -> assertThat(translation.getAnswer())
                        .isEqualTo("First line\nSecond <b>line</b>"));
    }

    @Test
    void aTooLongTranslatedQuestionIsRejectedBeforeAnyTranslationIsWritten() {
        givenCategory();
        givenGeneratedId(10L);
        FaqForm form = form();
        setSlot(form, "en", "Q".repeat(256), "Answer");

        assertThatThrownBy(() -> faqService.create(form, 7L))
                .isInstanceOfSatisfying(FaqValidationException.class,
                        exception -> assertThat(exception.getMessage()).contains("255자"));
        verify(faqMapper, never()).insertTranslation(any());
    }

    @Test
    void updateInsertsMissingLanguagesUpdatesExistingOnesAndClearsEmptiedOnes() {
        givenCategory();
        givenFaq(10L);
        when(faqMapper.findTranslationsByFaqId(10L)).thenReturn(List.of(
                stored(1L, 10L, "en", "Old question", "Old answer"),
                stored(2L, 10L, "zh-CN", "旧问题", "旧答案"),
                stored(3L, 10L, "zh-TW", "舊問題", "舊答案")));

        FaqForm form = form();
        setSlot(form, "en", "New question", "New answer");  // UPDATE
        setSlot(form, "ja", "新しい質問", "新しい回答");        // INSERT
        setSlot(form, "zh-CN", "  ", "   ");                // DELETE
        setSlot(form, "zh-TW", "舊問題", "舊答案");           // 그대로 UPDATE

        faqService.update(10L, form);

        ArgumentCaptor<FaqTranslation> updated = ArgumentCaptor.forClass(FaqTranslation.class);
        verify(faqMapper, atLeast(1)).updateTranslation(updated.capture());
        assertThat(updated.getAllValues())
                .extracting(FaqTranslation::getLanguageCode, FaqTranslation::getQuestion)
                .containsExactly(tuple("en", "New question"), tuple("zh-TW", "舊問題"));

        assertThat(captureInserts())
                .extracting(FaqTranslation::getLanguageCode, FaqTranslation::getQuestion)
                .containsExactly(tuple("ja", "新しい質問"));

        // 비운 언어 한 줄만 지운다. 다른 언어는 건드리지 않는다.
        verify(faqMapper).deleteTranslation(10L, "zh-CN");
        verify(faqMapper, never()).deleteTranslation(10L, "en");
        verify(faqMapper, never()).deleteTranslation(10L, "ja");
        verify(faqMapper, never()).deleteTranslation(10L, "zh-TW");
        verify(faqMapper, never()).deleteTranslation(10L, "ko");
    }

    /** 슬롯에 없는 언어 코드가 제출돼도 저장하지 않는다. */
    @Test
    void unsupportedLanguageCodesAreIgnored() {
        givenCategory();
        givenGeneratedId(10L);
        FaqForm form = form();
        form.getTranslations().add(new FaqTranslationForm("fr", "Question", "Réponse"));
        form.getTranslations().add(new FaqTranslationForm(null, "Question", "Answer"));

        faqService.create(form, 7L);

        verify(faqMapper, never()).insertTranslation(any());
    }

    @Test
    void editFormLoadsStoredTranslationsByLanguageCodeNotByRowOrder() {
        when(faqMapper.findById(10L)).thenReturn(faq(10L));
        // 조회 순서가 뒤섞여 들어와도 언어 코드로 슬롯을 찾는다
        when(faqMapper.findTranslationsByFaqId(10L)).thenReturn(List.of(
                stored(3L, 10L, "zh-TW", "繁體問題", "繁體答案"),
                stored(1L, 10L, "en", "English question", "English answer")));

        FaqForm form = faqService.getForm(10L);

        assertThat(form.getQuestion()).isEqualTo("기존 질문");
        assertThat(form.getTranslations())
                .extracting(FaqTranslationForm::getLanguageCode, FaqTranslationForm::getQuestion,
                        FaqTranslationForm::getAnswer)
                .containsExactly(
                        tuple("ko", "", ""),
                        tuple("en", "English question", "English answer"),
                        tuple("ja", "", ""),
                        tuple("zh-CN", "", ""),
                        tuple("zh-TW", "繁體問題", "繁體答案"));
    }

    private List<FaqTranslation> captureInserts() {
        ArgumentCaptor<FaqTranslation> captor = ArgumentCaptor.forClass(FaqTranslation.class);
        verify(faqMapper, org.mockito.Mockito.atLeast(0)).insertTranslation(captor.capture());
        return captor.getAllValues();
    }

    private void givenCategory() {
        FaqCategory category = new FaqCategory();
        category.setId(3L);
        category.setCategoryName("회원/계정");
        when(faqMapper.findCategoryById(3L)).thenReturn(category);
    }

    private void givenGeneratedId(Long generatedId) {
        doAnswer(invocation -> {
            invocation.getArgument(0, Faq.class).setId(generatedId);
            return 1;
        }).when(faqMapper).insertFaq(any(Faq.class));
    }

    private void givenFaq(Long id) {
        Faq faq = faq(id);
        when(faqMapper.findByIdForUpdate(id)).thenReturn(faq);
        when(faqMapper.updateFaq(faq)).thenReturn(1);
    }

    private Faq faq(Long id) {
        Faq faq = new Faq();
        faq.setId(id);
        faq.setQuestion("기존 질문");
        faq.setAnswer("기존 답변");
        faq.setOrderIndex(1L);
        faq.setIsVisible(true);
        faq.setCategoryId(3L);
        return faq;
    }

    private FaqTranslation stored(Long id, Long faqId, String languageCode,
                                  String question, String answer) {
        FaqTranslation translation = new FaqTranslation();
        translation.setId(id);
        translation.setFaqId(faqId);
        translation.setLanguageCode(languageCode);
        translation.setQuestion(question);
        translation.setAnswer(answer);
        return translation;
    }

    private FaqForm form() {
        FaqForm form = new FaqForm();
        form.setCategoryId(3L);
        form.setQuestion("회원 탈퇴는 어떻게 하나요?");
        form.setAnswer("회원정보 수정에서 탈퇴할 수 있습니다.");
        form.setOrderIndex(2L);
        form.setVisible(true);
        return form;
    }

    private void setSlot(FaqForm form, String languageCode, String question, String answer) {
        form.getTranslations().stream()
                .filter(slot -> languageCode.equals(slot.getLanguageCode()))
                .forEach(slot -> {
                    slot.setQuestion(question);
                    slot.setAnswer(answer);
                });
    }
}
