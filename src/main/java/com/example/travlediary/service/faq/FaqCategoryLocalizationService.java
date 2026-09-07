package com.example.travlediary.service.faq;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.FaqCategoryTranslation;
import com.example.travlediary.repository.faq.FaqMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * FAQ 카테고리 이름의 번역을 읽어 공개 화면에 쓸 이름을 만든다.
 *
 * <p>대체는 <b>요청 언어 → 한국어 원문</b> 한 단계까지만 한다. 요청 언어 줄이 없거나 비어 있으면
 * 한국어 이름을 쓰고, 다른 언어(en → ja 등) 번역을 찾아가지 않는다. (FAQ 질문·답변과 같은 정책)
 *
 * <p>한국어 요청은 번역을 아예 읽지 않는다. 여러 카테고리는 한 번의 조회로 모아 읽는다.
 */
@Service
@RequiredArgsConstructor
public class FaqCategoryLocalizationService {

    private final FaqMapper faqMapper;

    /**
     * @param baseNames 카테고리 번호 → 한국어 원문 이름. 여기 담긴 번호가 조회 대상이다.
     * @return 카테고리 번호 → 표시용 이름. 번역이 없으면 한국어 이름이 그대로 들어 있다.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> resolveLocalizedNamesByCategoryIds(
            Map<Long, String> baseNames, SupportedLanguage requestedLanguage) {
        if (baseNames == null || baseNames.isEmpty()) {
            // 볼 카테고리가 없으면 번역도 읽지 않는다.
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>(baseNames);
        if (isKorean(requestedLanguage)) {
            // 한국어 요청은 번역을 읽지 않는다.
            return names;
        }

        // 같은 카테고리를 여러 FAQ 가 써도 번호는 한 번씩만 넘긴다.
        List<Long> categoryIds = baseNames.keySet().stream().filter(Objects::nonNull).toList();
        if (categoryIds.isEmpty()) {
            return names;
        }

        String languageCode = requestedLanguage.getLanguageTag();
        List<FaqCategoryTranslation> stored =
                faqMapper.findCategoryTranslationsByCategoryIds(categoryIds);
        if (stored == null) {
            return names;
        }
        for (FaqCategoryTranslation translation : stored) {
            // 요청 언어 줄만 본다. 다른 언어 줄은 대체 후보가 아니다.
            if (translation == null || translation.getFaqCategoryId() == null
                    || !languageCode.equals(translation.getLanguageCode())) {
                continue;
            }
            String name = translation.getCategoryName();
            if (name != null && !name.isBlank()
                    && names.containsKey(translation.getFaqCategoryId())) {
                names.put(translation.getFaqCategoryId(), name.strip());
            }
        }
        return names;
    }

    private boolean isKorean(SupportedLanguage requestedLanguage) {
        return requestedLanguage == null || requestedLanguage == SupportedLanguage.KOREAN;
    }
}
