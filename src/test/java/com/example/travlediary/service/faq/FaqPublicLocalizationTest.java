package com.example.travlediary.service.faq;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.FaqListItemDto;
import com.example.travlediary.model.FaqCategoryTranslation;
import com.example.travlediary.model.FaqTranslation;
import com.example.travlediary.repository.faq.FaqMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 공개 자주 묻는 질문의 언어 대체.
 *
 * <p>대체는 필드마다 따로, <b>요청 언어 → 한국어 원문</b> 한 단계까지만 한다.
 * 답변은 서식 없는 일반 텍스트라 HTML 정제를 거치지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class FaqPublicLocalizationTest {

    private static final String BASE_CATEGORY_NAME = "회원/계정";
    private static final String BASE_QUESTION = "회원 탈퇴는 어떻게 하나요?";
    private static final String BASE_ANSWER = "회원정보 수정에서 탈퇴할 수 있습니다.";

    @Mock
    private FaqMapper faqMapper;

    private FaqService faqService;

    @BeforeEach
    void setUp() {
        faqService = new FaqService(faqMapper, new FaqLocalizationService(faqMapper),
                new FaqCategoryLocalizationService(faqMapper));
    }

    @Test
    void koreanRequestKeepsTheBaseTextAndNeverReadsTranslations() {
        List<FaqListItemDto> faqs = List.of(item(10L));

        faqService.localizePublicList(faqs, SupportedLanguage.KOREAN);

        assertThat(faqs).extracting(FaqListItemDto::getQuestion, FaqListItemDto::getAnswer)
                .containsExactly(tuple(BASE_QUESTION, BASE_ANSWER));
        verifyNoMoreInteractions(faqMapper);
    }

    @Test
    void englishRequestUsesBothTranslatedFields() {
        givenTranslations(translation(10L, "en", "How do I close my account?",
                "Open the settings page."));
        List<FaqListItemDto> faqs = List.of(item(10L));

        faqService.localizePublicList(faqs, SupportedLanguage.ENGLISH);

        assertThat(faqs).extracting(FaqListItemDto::getQuestion, FaqListItemDto::getAnswer)
                .containsExactly(tuple("How do I close my account?", "Open the settings page."));
    }

    @Test
    void onlyTranslatedQuestionLeavesTheKoreanAnswer() {
        givenTranslations(translation(10L, "en", "How do I close my account?", null));
        List<FaqListItemDto> faqs = List.of(item(10L));

        faqService.localizePublicList(faqs, SupportedLanguage.ENGLISH);

        assertThat(faqs).extracting(FaqListItemDto::getQuestion, FaqListItemDto::getAnswer)
                .containsExactly(tuple("How do I close my account?", BASE_ANSWER));
    }

    @Test
    void onlyTranslatedAnswerLeavesTheKoreanQuestion() {
        givenTranslations(translation(10L, "en", null, "Open the settings page."));
        List<FaqListItemDto> faqs = List.of(item(10L));

        faqService.localizePublicList(faqs, SupportedLanguage.ENGLISH);

        assertThat(faqs).extracting(FaqListItemDto::getQuestion, FaqListItemDto::getAnswer)
                .containsExactly(tuple(BASE_QUESTION, "Open the settings page."));
    }

    @Test
    void whitespaceOnlyTranslationFieldsFallBackToKorean() {
        givenTranslations(translation(10L, "en", "   ", "\n  \t"));
        List<FaqListItemDto> faqs = List.of(item(10L));

        faqService.localizePublicList(faqs, SupportedLanguage.ENGLISH);

        assertThat(faqs).extracting(FaqListItemDto::getQuestion, FaqListItemDto::getAnswer)
                .containsExactly(tuple(BASE_QUESTION, BASE_ANSWER));
    }

    @Test
    void missingTranslationRowFallsBackToKorean() {
        givenTranslations();
        List<FaqListItemDto> faqs = List.of(item(10L));

        faqService.localizePublicList(faqs, SupportedLanguage.ENGLISH);

        assertThat(faqs).extracting(FaqListItemDto::getQuestion, FaqListItemDto::getAnswer)
                .containsExactly(tuple(BASE_QUESTION, BASE_ANSWER));
    }

    @Test
    void anotherLanguageIsNeverUsedAsAFallback() {
        givenTranslations(translation(10L, "ja", "退会はどうすればいいですか?", "会員情報の修正から退会できます。"));
        List<FaqListItemDto> faqs = List.of(item(10L));

        faqService.localizePublicList(faqs, SupportedLanguage.ENGLISH);

        assertThat(faqs).extracting(FaqListItemDto::getQuestion, FaqListItemDto::getAnswer)
                .containsExactly(tuple(BASE_QUESTION, BASE_ANSWER));
    }

    @Test
    void manyFaqsAreLocalizedWithOneTranslationQueryAndKeepTheirOtherFields() {
        when(faqMapper.findTranslationsByFaqIds(List.of(1L, 2L, 3L))).thenReturn(List.of(
                translation(1L, "en", "First question", "First answer"),
                // 요청 언어가 아닌 줄은 쓰지 않는다
                translation(2L, "ja", "二番目の質問", "二番目の回答"),
                translation(3L, "en", "Third question", null)));
        List<FaqListItemDto> faqs = List.of(item(1L), item(2L), item(3L));

        faqService.localizePublicList(faqs, SupportedLanguage.ENGLISH);

        assertThat(faqs)
                .extracting(FaqListItemDto::getId, FaqListItemDto::getQuestion,
                        FaqListItemDto::getAnswer)
                .containsExactly(
                        tuple(1L, "First question", "First answer"),
                        tuple(2L, BASE_QUESTION, BASE_ANSWER),
                        tuple(3L, "Third question", BASE_ANSWER));
        // 질문마다 한 번씩 읽지 않는다
        verify(faqMapper, times(1)).findTranslationsByFaqIds(any());
        verify(faqMapper, never()).findTranslationsByFaqId(anyLong());
        // 카테고리·정렬·노출 값은 그대로 둔다 (카테고리 번역은 다음 단계다)
        assertThat(faqs).extracting(FaqListItemDto::getCategoryName, FaqListItemDto::getOrderIndex,
                        FaqListItemDto::isVisible)
                .containsOnly(tuple("회원/계정", 1L, true));
    }

    @Test
    void koreanRequestKeepsTheBaseCategoryNameAndNeverReadsCategoryTranslations() {
        List<FaqListItemDto> faqs = List.of(item(10L));

        faqService.localizePublicList(faqs, SupportedLanguage.KOREAN);

        assertThat(faqs).extracting(FaqListItemDto::getCategoryName)
                .containsExactly(BASE_CATEGORY_NAME);
        verify(faqMapper, never()).findCategoryTranslationsByCategoryIds(any());
        verify(faqMapper, never()).findCategoryTranslationsByCategoryId(anyLong());
    }

    @Test
    void englishRequestUsesTheTranslatedCategoryName() {
        givenTranslations();
        givenCategoryTranslations(List.of(1L), categoryTranslation(1L, "en", "Account"));
        List<FaqListItemDto> faqs = List.of(item(10L));

        faqService.localizePublicList(faqs, SupportedLanguage.ENGLISH);

        assertThat(faqs).extracting(FaqListItemDto::getCategoryName).containsExactly("Account");
        // 번호와 뱃지는 표시 이름과 무관하게 그대로다
        assertThat(faqs).extracting(FaqListItemDto::getCategoryId, FaqListItemDto::getCategoryBadge)
                .containsExactly(tuple(1L, "is-account"));
    }

    @Test
    void missingBlankOrOtherLanguageCategoryTranslationsFallBackToKorean() {
        for (FaqCategoryTranslation[] stored : List.of(
                new FaqCategoryTranslation[]{},
                new FaqCategoryTranslation[]{categoryTranslation(1L, "en", "   ")},
                new FaqCategoryTranslation[]{categoryTranslation(1L, "ja", "アカウント")})) {
            org.mockito.Mockito.reset(faqMapper);
            givenTranslations();
            givenCategoryTranslations(List.of(1L), stored);
            List<FaqListItemDto> faqs = List.of(item(10L));

            faqService.localizePublicList(faqs, SupportedLanguage.ENGLISH);

            assertThat(faqs).extracting(FaqListItemDto::getCategoryName)
                    .containsExactly(BASE_CATEGORY_NAME);
        }
    }

    @Test
    void categoryTranslationsAreReadOnceForDistinctCategoriesOnly() {
        when(faqMapper.findTranslationsByFaqIds(List.of(1L, 2L, 3L))).thenReturn(List.of());
        // 같은 카테고리를 두 FAQ 가 써도 번호는 한 번만 넘긴다
        when(faqMapper.findCategoryTranslationsByCategoryIds(List.of(1L, 2L))).thenReturn(List.of(
                categoryTranslation(1L, "en", "Account"),
                categoryTranslation(2L, "en", "Travel info")));
        List<FaqListItemDto> faqs = List.of(
                item(1L, 1L, "회원/계정"), item(2L, 1L, "회원/계정"), item(3L, 2L, "여행정보"));

        faqService.localizePublicList(faqs, SupportedLanguage.ENGLISH);

        assertThat(faqs)
                .extracting(FaqListItemDto::getCategoryId, FaqListItemDto::getCategoryName,
                        FaqListItemDto::getCategoryBadge)
                .containsExactly(tuple(1L, "Account", "is-account"),
                        tuple(1L, "Account", "is-account"),
                        tuple(2L, "Travel info", "is-travel"));
        verify(faqMapper, times(1)).findCategoryTranslationsByCategoryIds(any());
        verify(faqMapper, never()).findCategoryTranslationsByCategoryId(anyLong());
    }

    /** 뱃지는 카테고리 번호로만 정한다. 기존 다섯 카테고리는 쓰던 클래스를 그대로 유지한다. */
    @Test
    void badgeClassesComeFromTheCategoryIdOnly() {
        List<FaqListItemDto> faqs = List.of(
                item(1L, 1L, "회원/계정"), item(2L, 2L, "여행정보"), item(3L, 3L, "커뮤니티"),
                item(4L, 4L, "서비스 이용"), item(5L, 5L, "기타"));

        faqService.localizePublicList(faqs, SupportedLanguage.KOREAN);

        assertThat(faqs)
                .extracting(FaqListItemDto::getCategoryId, FaqListItemDto::getCategoryBadge)
                .containsExactly(tuple(1L, "is-account"), tuple(2L, "is-travel"),
                        tuple(3L, "is-community"), tuple(4L, "is-service"), tuple(5L, "is-etc"));
    }

    /** 같은 번호면 이름을 무엇으로 바꾸든(번역·이름 수정 포함) 같은 뱃지다. */
    @Test
    void theSameCategoryIdKeepsItsBadgeWhateverTheNameIs() {
        List<FaqListItemDto> faqs = List.of(
                item(1L, 1L, "회원/계정"), item(2L, 1L, "Account"),
                item(3L, 1L, "アカウント"), item(4L, 1L, "회원 및 계정"));

        faqService.localizePublicList(faqs, SupportedLanguage.KOREAN);

        assertThat(faqs).extracting(FaqListItemDto::getCategoryBadge)
                .containsOnly("is-account");
    }

    /** 관리자가 새로 만든 카테고리와 번호가 없는 줄은 중립 뱃지로 둔다. */
    @Test
    void unknownOrMissingCategoryIdsGetTheNeutralBadge() {
        List<FaqListItemDto> faqs = List.of(
                item(10L, 9L, "새 카테고리"),
                // 이름이 기존 카테고리와 같아도 번호가 없으면 중립이다
                item(11L, null, "회원/계정"));

        faqService.localizePublicList(faqs, SupportedLanguage.KOREAN);

        assertThat(faqs).extracting(FaqListItemDto::getCategoryBadge)
                .containsOnly(FaqCategoryBadge.DEFAULT);
    }

    private void givenCategoryTranslations(List<Long> categoryIds,
                                           FaqCategoryTranslation... translations) {
        when(faqMapper.findCategoryTranslationsByCategoryIds(categoryIds))
                .thenReturn(List.of(translations));
    }

    private FaqCategoryTranslation categoryTranslation(Long categoryId, String languageCode,
                                                       String categoryName) {
        FaqCategoryTranslation translation = new FaqCategoryTranslation();
        translation.setFaqCategoryId(categoryId);
        translation.setLanguageCode(languageCode);
        translation.setCategoryName(categoryName);
        return translation;
    }

    private void givenTranslations(FaqTranslation... translations) {
        when(faqMapper.findTranslationsByFaqIds(List.of(10L))).thenReturn(List.of(translations));
    }

    /** 기본 픽스처는 실제 데이터와 같은 회원/계정(1번) 카테고리를 쓴다. */
    private FaqListItemDto item(Long id) {
        return item(id, 1L, BASE_CATEGORY_NAME);
    }

    private FaqListItemDto item(Long id, Long categoryId, String categoryName) {
        FaqListItemDto item = new FaqListItemDto();
        item.setId(id);
        item.setQuestion(BASE_QUESTION);
        item.setAnswer(BASE_ANSWER);
        item.setCategoryId(categoryId);
        item.setCategoryName(categoryName);
        item.setOrderIndex(1L);
        item.setVisible(true);
        return item;
    }

    private FaqTranslation translation(Long faqId, String languageCode,
                                       String question, String answer) {
        FaqTranslation translation = new FaqTranslation();
        translation.setFaqId(faqId);
        translation.setLanguageCode(languageCode);
        translation.setQuestion(question);
        translation.setAnswer(answer);
        return translation;
    }
}
