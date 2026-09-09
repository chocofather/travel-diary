package com.example.travlediary.service.translation;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.translation.UserPostTranslationSource;
import com.example.travlediary.repository.post.PostMapper;
import com.example.travlediary.service.post.PostContentSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserPostTranslationSourceReader implements TranslationSourceReader {
    private static final String TITLE = "title";
    private static final String CONTENT = "content";

    private final PostMapper mapper;
    private final PostContentSanitizer contentSanitizer;

    @Override
    public TranslatableContentType contentType() {
        return TranslatableContentType.USER_POST;
    }

    @Override
    public Optional<TranslationSourceSnapshot> findVisible(Long contentId) {
        return findVisible(contentId, CONTENT);
    }

    @Override
    public Optional<TranslationSourceSnapshot> findVisible(Long contentId, String sourceField) {
        return toSnapshot(mapper.findVisibleTranslationSource(contentId), sourceField);
    }

    @Override
    public Optional<TranslationSourceSnapshot> findVisibleForUpdate(Long contentId) {
        return findVisibleForUpdate(contentId, CONTENT);
    }

    @Override
    public Optional<TranslationSourceSnapshot> findVisibleForUpdate(
            Long contentId, String sourceField) {
        return toSnapshot(mapper.findVisibleTranslationSourceForUpdate(contentId), sourceField);
    }

    @Override
    public void correctSourceLanguage(TranslationSourceSnapshot snapshot, String detectedLanguage) {
        if (!"und".equals(snapshot.sourceLanguage())) return;
        if (SupportedLanguage.fromLanguageTag(detectedLanguage).isEmpty()) return;
        if (TITLE.equals(snapshot.sourceField())) {
            mapper.correctTitleSourceLanguage(
                    snapshot.contentId(), detectedLanguage, snapshot.updatedAt());
        } else if (CONTENT.equals(snapshot.sourceField())) {
            mapper.correctContentSourceLanguage(
                    snapshot.contentId(), detectedLanguage, snapshot.updatedAt());
        }
    }

    private Optional<TranslationSourceSnapshot> toSnapshot(
            UserPostTranslationSource source, String sourceField) {
        if (source == null) return Optional.empty();
        if (TITLE.equals(sourceField)) {
            return snapshot(source, TITLE, source.getTitle(),
                    source.getTitleSourceLanguage(), "text/plain");
        }
        if (CONTENT.equals(sourceField)) {
            return snapshot(source, CONTENT, contentSanitizer.sanitize(source.getContent()),
                    source.getContentSourceLanguage(), "text/html");
        }
        return Optional.empty();
    }

    private Optional<TranslationSourceSnapshot> snapshot(
            UserPostTranslationSource source,
            String sourceField,
            String text,
            String sourceLanguage,
            String mimeType) {
        if (text == null) return Optional.empty();
        String language = sourceLanguage == null || sourceLanguage.isBlank()
                ? "und" : sourceLanguage;
        return Optional.of(new TranslationSourceSnapshot(
                contentType(), source.getContentId(), sourceField, text,
                language, source.getUpdatedAt(), mimeType));
    }
}
