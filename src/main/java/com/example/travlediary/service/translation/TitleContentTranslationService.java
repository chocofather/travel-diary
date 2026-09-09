package com.example.travlediary.service.translation;

import com.example.travlediary.service.post.PostContentSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TitleContentTranslationService {
    private static final String TITLE = "title";
    private static final String CONTENT = "content";

    private final ContentTranslationService translationService;
    private final TranslationSourceRegistry sourceRegistry;
    private final PostContentSanitizer contentSanitizer;

    public TitleContentTranslationResponse translate(
            TranslatableContentType type,
            Long contentId,
            String targetLanguage,
            String ipAddress,
            Long userId) {
        if (type != TranslatableContentType.USER_POST
                && type != TranslatableContentType.COURSE) {
            throw new IllegalArgumentException("제목/본문 번역을 지원하지 않는 콘텐츠 유형입니다.");
        }
        TranslationSourceReader sourceReader = sourceRegistry.get(type);
        Optional<TranslationSourceSnapshot> titleSource =
                sourceReader.findVisible(contentId, TITLE);
        Optional<TranslationSourceSnapshot> contentSource =
                sourceReader.findVisible(contentId, CONTENT);
        if (titleSource.isEmpty() && contentSource.isEmpty()) {
            throw new TranslationNotFoundException();
        }

        ContentTranslationResponse title = translateIfNeeded(
                type, contentId, TITLE, titleSource.orElse(null),
                targetLanguage, ipAddress, userId);
        ContentTranslationResponse content = translateIfNeeded(
                type, contentId, CONTENT, contentSource.orElse(null),
                targetLanguage, ipAddress, userId);

        String translatedTitle = readyText(title);
        String translatedContent = readyText(content);
        if (translatedContent != null) {
            translatedContent = contentSanitizer.sanitize(translatedContent);
        }
        if (isProcessing(title) || isProcessing(content)) {
            return TitleContentTranslationResponse.processing(
                    translatedTitle, translatedContent,
                    Math.max(retryAfter(title), retryAfter(content)));
        }
        return TitleContentTranslationResponse.ready(
                translatedTitle, translatedContent, isCached(title) && isCached(content));
    }

    private ContentTranslationResponse translateIfNeeded(
            TranslatableContentType type,
            Long contentId,
            String sourceField,
            TranslationSourceSnapshot source,
            String targetLanguage,
            String ipAddress,
            Long userId) {
        if (source == null
                || !TranslationVisibility.shouldOffer(source.sourceLanguage(), targetLanguage)) {
            return null;
        }
        return translationService.translate(
                type, contentId, sourceField, targetLanguage, ipAddress, userId);
    }

    private String readyText(ContentTranslationResponse response) {
        return response != null && "READY".equals(response.status())
                ? response.translatedText() : null;
    }

    private boolean isProcessing(ContentTranslationResponse response) {
        return response != null && "PROCESSING".equals(response.status());
    }

    private long retryAfter(ContentTranslationResponse response) {
        return response == null ? 0L : response.retryAfterSeconds();
    }

    private boolean isCached(ContentTranslationResponse response) {
        return response == null || response.cached();
    }
}
