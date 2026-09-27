package com.example.travlediary.service.faq;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.FaqCategoryFilterDto;
import com.example.travlediary.dto.FaqForm;
import com.example.travlediary.dto.FaqListItemDto;
import com.example.travlediary.model.Faq;
import com.example.travlediary.model.FaqCategory;
import com.example.travlediary.model.FaqCategoryTranslation;
import com.example.travlediary.repository.faq.FaqMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FaqServiceTest {

    @Mock
    private FaqMapper faqMapper;

    private FaqService faqService;

    @BeforeEach
    void setUp() {
        faqService = new FaqService(faqMapper, new FaqLocalizationService(faqMapper),
                new FaqCategoryLocalizationService(faqMapper));
    }

    /**
     * 필터는 DB 카테고리 전체로 만든다. 공개 질문이 있는 카테고리는 관리자 노출 순서대로 앞에,
     * 공개 질문이 없는 카테고리는 뒤에 두고, 이름은 요청 언어로 바꾼다.
     */
    @Test
    void publicCategoryFiltersFollowTheFaqOrderThenAppendCategoriesWithoutPublicQuestions() {
        // 카테고리 조회는 이름순이다.
        when(faqMapper.findCategories()).thenReturn(List.of(
                category(6L, "고객센터"), category(9L, "새 카테고리"),
                category(2L, "여행정보"), category(1L, "회원/계정")));
        FaqCategoryTranslation english = new FaqCategoryTranslation();
        english.setFaqCategoryId(1L);
        english.setLanguageCode("en");
        english.setCategoryName("Account");
        when(faqMapper.findCategoryTranslationsByCategoryIds(any())).thenReturn(List.of(english));
        // 99 는 목록에만 남은 번호(카테고리 조회에 없음)라 필터로 만들지 않는다.
        List<FaqListItemDto> publicFaqs = List.of(listItem(1L), listItem(6L), listItem(2L), listItem(1L), listItem(99L));

        List<FaqCategoryFilterDto> filters = faqService.getPublicCategoryFilters(
                publicFaqs, SupportedLanguage.ENGLISH);

        assertThat(filters).containsExactly(
                new FaqCategoryFilterDto(1L, "Account"),
                new FaqCategoryFilterDto(6L, "고객센터"),
                new FaqCategoryFilterDto(2L, "여행정보"),
                new FaqCategoryFilterDto(9L, "새 카테고리"));
    }

    private static FaqCategory category(Long id, String name) {
        FaqCategory category = new FaqCategory();
        category.setId(id);
        category.setCategoryName(name);
        return category;
    }

    private static FaqListItemDto listItem(Long categoryId) {
        FaqListItemDto item = new FaqListItemDto();
        item.setCategoryId(categoryId);
        return item;
    }

    @Test
    void createUsesAuthenticatedAdminAndKeepsAnswerAsPlainText() {
        FaqForm form = form();
        form.setQuestion("  회원 탈퇴는 어떻게 하나요?  ");
        form.setAnswer("  첫 줄\n<script>alert(1)</script>  ");
        when(faqMapper.findCategoryById(3L)).thenReturn(category(3L));
        doAnswer(invocation -> {
            Faq faq = invocation.getArgument(0);
            faq.setId(10L);
            return 1;
        }).when(faqMapper).insertFaq(any(Faq.class));

        assertThat(faqService.create(form, 7L)).isEqualTo(10L);

        ArgumentCaptor<Faq> captor = ArgumentCaptor.forClass(Faq.class);
        verify(faqMapper).insertFaq(captor.capture());
        Faq saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getCategoryId()).isEqualTo(3L);
        assertThat(saved.getQuestion()).isEqualTo("회원 탈퇴는 어떻게 하나요?");
        assertThat(saved.getAnswer()).isEqualTo("첫 줄\n<script>alert(1)</script>");
        assertThat(saved.getOrderIndex()).isEqualTo(2L);
        assertThat(saved.getIsVisible()).isTrue();
    }

    @Test
    void createRejectsUnknownCategoryWithoutInsert() {
        FaqForm form = form();
        when(faqMapper.findCategoryById(3L)).thenReturn(null);

        assertThatThrownBy(() -> faqService.create(form, 7L))
                .isInstanceOfSatisfying(FaqValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo("categoryId"));

        verify(faqMapper, never()).insertFaq(any());
    }

    @Test
    void blankQuestionAnswerAndInvalidOrderAreRejected() {
        FaqForm blankQuestion = form();
        blankQuestion.setQuestion("   ");
        assertFieldError(blankQuestion, "question");

        FaqForm longQuestion = form();
        longQuestion.setQuestion("가".repeat(256));
        assertFieldError(longQuestion, "question");

        FaqForm blankAnswer = form();
        blankAnswer.setAnswer("\n  ");
        assertFieldError(blankAnswer, "answer");

        FaqForm invalidOrder = form();
        invalidOrder.setOrderIndex(0L);
        assertFieldError(invalidOrder, "orderIndex");

        verify(faqMapper, never()).insertFaq(any());
    }

    @Test
    void updatePreservesOriginalAuthorAndCreationMetadata() {
        Faq existing = faq(10L);
        existing.setUserId(3L);
        when(faqMapper.findByIdForUpdate(10L)).thenReturn(existing);
        when(faqMapper.findCategoryById(3L)).thenReturn(category(3L));
        when(faqMapper.updateFaq(existing)).thenReturn(1);

        FaqForm form = form();
        form.setQuestion("수정 질문");
        form.setVisible(false);
        faqService.update(10L, form);

        assertThat(existing.getUserId()).isEqualTo(3L);
        assertThat(existing.getQuestion()).isEqualTo("수정 질문");
        assertThat(existing.getIsVisible()).isFalse();
    }

    @Test
    void listsDelegateToSeparateAdminAndPublicQueries() {
        FaqListItemDto item = new FaqListItemDto();
        when(faqMapper.findAdminList()).thenReturn(List.of(item));
        when(faqMapper.findPublicList()).thenReturn(List.of(item));

        assertThat(faqService.getAdminList()).containsExactly(item);
        assertThat(faqService.getPublicList()).containsExactly(item);
    }

    @Test
    void missingFaqReturnsNotFoundAndDeleteIsHardDelete() {
        when(faqMapper.findById(99L)).thenReturn(null);
        assertThatThrownBy(() -> faqService.getForm(99L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        Faq faq = faq(10L);
        when(faqMapper.findByIdForUpdate(10L)).thenReturn(faq);
        when(faqMapper.deleteFaq(10L)).thenReturn(1);
        faqService.delete(10L);
        verify(faqMapper).deleteFaq(10L);
    }

    private void assertFieldError(FaqForm form, String field) {
        assertThatThrownBy(() -> faqService.create(form, 7L))
                .isInstanceOfSatisfying(FaqValidationException.class,
                        exception -> assertThat(exception.getField()).isEqualTo(field));
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

    private FaqCategory category(Long id) {
        FaqCategory category = new FaqCategory();
        category.setId(id);
        category.setCategoryName("회원/계정");
        return category;
    }
}
