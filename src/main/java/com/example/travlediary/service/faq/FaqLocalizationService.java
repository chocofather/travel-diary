package com.example.travlediary.service.faq;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.FaqTranslation;
import com.example.travlediary.repository.faq.FaqMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 자주 묻는 질문의 번역을 읽어 공개 화면에 쓸 질문·답변을 만든다.
 *
 * <p>대체는 <b>필드마다 따로</b>, 그리고 <b>요청 언어 → 한국어 원문</b> 한 단계까지만 한다.
 * 요청 언어 줄이 없다고 다른 언어(en → ja 등) 번역을 찾아가지 않는다. (공지사항과 같은 정책)
 *
 * <p>한국어 요청은 번역을 아예 읽지 않고 원문을 그대로 쓴다.
 * 답변은 서식 없는 일반 텍스트라 HTML 판정이나 정제를 하지 않고 공백 여부만 본다.
 *
 * <p><b>공개 화면 전용이다.</b> 표시할 값만 만들어 돌려주고 관리자 경로가 읽는 원문은 건드리지 않는다.
 */
@Service
@RequiredArgsConstructor
public class FaqLocalizationService {

    private final FaqMapper faqMapper;

    /**
     * 목록용. 번역을 <b>한 번의 조회</b>로 모아 읽어 질문마다 표시용 질문·답변을 만든다.
     *
     * @param baseQuestions 질문 번호 → 원문 질문. 여기 담긴 번호가 조회 대상이다.
     * @param baseAnswers   질문 번호 → 원문 답변.
     * @return 질문 번호 → 표시용 값. 번역이 없는 필드에는 원문이 그대로 들어 있다.
     */
    @Transactional(readOnly = true)
    public Map<Long, FaqTranslation> resolveLocalizedTextsByFaqIds(
            Map<Long, String> baseQuestions,
            Map<Long, String> baseAnswers,
            SupportedLanguage requestedLanguage) {
        Set<Long> faqIds = new LinkedHashSet<>();
        if (baseQuestions != null) {
            baseQuestions.keySet().stream().filter(Objects::nonNull).forEach(faqIds::add);
        }
        if (baseAnswers != null) {
            baseAnswers.keySet().stream().filter(Objects::nonNull).forEach(faqIds::add);
        }
        if (faqIds.isEmpty()) {
            // 볼 질문이 없으면 번역도 읽지 않는다.
            return Map.of();
        }

        Map<Long, FaqTranslation> requested = isKorean(requestedLanguage)
                // 한국어 요청은 번역을 읽지 않는다.
                ? Map.of()
                : requestedTranslations(List.copyOf(faqIds), requestedLanguage.getLanguageTag());

        Map<Long, FaqTranslation> localized = new LinkedHashMap<>();
        for (Long faqId : faqIds) {
            FaqTranslation translation = requested.get(faqId);
            localized.put(faqId, display(
                    localizedField(translation == null ? null : translation.getQuestion(),
                            baseQuestions == null ? null : baseQuestions.get(faqId)),
                    localizedField(translation == null ? null : translation.getAnswer(),
                            baseAnswers == null ? null : baseAnswers.get(faqId))));
        }
        return localized;
    }

    /** 요청 언어 줄만 질문 번호별로 모은다. 다른 언어 줄은 대체 후보가 아니다. */
    private Map<Long, FaqTranslation> requestedTranslations(List<Long> faqIds,
                                                            String languageCode) {
        List<FaqTranslation> stored = faqMapper.findTranslationsByFaqIds(faqIds);
        if (stored == null) {
            return Map.of();
        }
        Map<Long, FaqTranslation> byFaqId = new LinkedHashMap<>();
        for (FaqTranslation translation : stored) {
            if (translation == null || translation.getFaqId() == null
                    || !languageCode.equals(translation.getLanguageCode())) {
                continue;
            }
            byFaqId.putIfAbsent(translation.getFaqId(), translation);
        }
        return byFaqId;
    }

    private boolean isKorean(SupportedLanguage requestedLanguage) {
        return requestedLanguage == null || requestedLanguage == SupportedLanguage.KOREAN;
    }

    /** 번역 값이 비어 있으면(공백만이어도) 한국어 원문을 쓴다. */
    private String localizedField(String translated, String base) {
        return translated == null || translated.isBlank() ? base : translated.strip();
    }

    private FaqTranslation display(String question, String answer) {
        FaqTranslation display = new FaqTranslation();
        display.setQuestion(question);
        display.setAnswer(answer);
        return display;
    }
}
