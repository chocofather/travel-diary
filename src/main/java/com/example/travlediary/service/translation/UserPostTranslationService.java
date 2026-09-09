package com.example.travlediary.service.translation;

import com.example.travlediary.service.post.PostContentSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserPostTranslationService {
    private static final String TITLE = "title";
    private static final String CONTENT = "content";

    private final ContentTranslationService translationService;
    private final UserPostTranslationSourceReader sourceReader;
    private final PostContentSanitizer contentSanitizer;

    public UserPostTranslationResponse translate(
            Long postId, String targetLanguage, String ipAddress, Long userId) {
        Optional<TranslationSourceSnapshot> titleSource =
                sourceReader.findVisible(postId, TITLE);
        Optional<TranslationSourceSnapshot> contentSource =
                sourceReader.findVisible(postId, CONTENT);
        if (titleSource.isEmpty() && contentSource.isEmpty()) {
            throw new TranslationNotFoundException();
        }

        ContentTranslationResponse title = translateIfNeeded(
                postId, TITLE, titleSource.orElse(null), targetLanguage, ipAddress, userId);
        ContentTranslationResponse content = translateIfNeeded(
                postId, CONTENT, contentSource.orElse(null), targetLanguage, ipAddress, userId);

        String translatedTitle = readyText(title);
        String translatedContent = readyText(content);
        if (translatedContent != null) {
            translatedContent = contentSanitizer.sanitize(translatedContent);
        }
        if (isProcessing(title) || isProcessing(content)) {
            return UserPostTranslationResponse.processing(
                    translatedTitle, translatedContent,
                    Math.max(retryAfter(title), retryAfter(content)));
        }
        return UserPostTranslationResponse.ready(
                translatedTitle, translatedContent, isCached(title) && isCached(content));
    }

    private ContentTranslationResponse translateIfNeeded(
            Long postId,
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
                TranslatableContentType.USER_POST, postId, sourceField,
                targetLanguage, ipAddress, userId);
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
