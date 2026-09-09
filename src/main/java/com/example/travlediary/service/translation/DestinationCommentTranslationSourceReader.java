package com.example.travlediary.service.translation;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.translation.DestinationCommentTranslationSource;
import com.example.travlediary.repository.comment.DestinationCommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DestinationCommentTranslationSourceReader implements TranslationSourceReader {
    private final DestinationCommentMapper mapper;

    @Override
    public TranslatableContentType contentType() {
        return TranslatableContentType.DESTINATION_COMMENT;
    }

    @Override
    public Optional<TranslationSourceSnapshot> findVisible(Long contentId) {
        return toSnapshot(mapper.findVisibleTranslationSource(contentId));
    }

    @Override
    public Optional<TranslationSourceSnapshot> findVisibleForUpdate(Long contentId) {
        return toSnapshot(mapper.findVisibleTranslationSourceForUpdate(contentId));
    }

    private Optional<TranslationSourceSnapshot> toSnapshot(DestinationCommentTranslationSource source) {
        if (source == null) return Optional.empty();
        String language = source.getSourceLanguage();
        if (language == null || language.isBlank()) language = "und";
        return Optional.of(new TranslationSourceSnapshot(
                contentType(), source.getContentId(), "content", source.getSourceText(),
                language, source.getUpdatedAt()));
    }

    @Override
    public void correctSourceLanguage(TranslationSourceSnapshot snapshot, String detectedLanguage) {
        if (!"und".equals(snapshot.sourceLanguage())) return;
        if (SupportedLanguage.fromLanguageTag(detectedLanguage).isEmpty()) return;
        mapper.correctSourceLanguage(snapshot.contentId(), detectedLanguage, snapshot.updatedAt());
    }
}
