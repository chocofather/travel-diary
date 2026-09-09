package com.example.travlediary.service.translation;

import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TitleContentTranslationServiceTest {

    @Test
    void courseTranslatesOnlyFieldsWhoseLanguageDiffersFromTheLocale() {
        ContentTranslationService engine = mock(ContentTranslationService.class);
        TranslationSourceReader reader = reader(
                snapshot("title", "English course", "en", "text/plain"),
                snapshot("content", "<p>한국어 설명</p>", "ko", "text/html"));
        when(engine.translate(TranslatableContentType.COURSE, 9L, "title",
                "ko", "203.0.113.1", 7L))
                .thenReturn(ContentTranslationResponse.ready("번역 코스", "en", "ko", false));

        TitleContentTranslationResponse response = new TitleContentTranslationService(
                engine, new TranslationSourceRegistry(List.of(reader)), new PostContentSanitizer())
                .translate(TranslatableContentType.COURSE, 9L, "ko", "203.0.113.1", 7L);

        assertThat(response.translatedTitle()).isEqualTo("번역 코스");
        assertThat(response.translatedContent()).isNull();
        verify(engine, never()).translate(TranslatableContentType.COURSE, 9L, "content",
                "ko", "203.0.113.1", 7L);
    }

    @Test
    void courseTranslationSanitizesProviderHtmlBeforeReturningIt() {
        ContentTranslationService engine = mock(ContentTranslationService.class);
        TranslationSourceReader reader = reader(
                snapshot("title", "한국어 코스", "ko", "text/plain"),
                snapshot("content", "<p>English description</p>", "en", "text/html"));
        when(engine.translate(TranslatableContentType.COURSE, 9L, "content",
                "ko", null, null)).thenReturn(ContentTranslationResponse.ready(
                "<p onclick=\"evil()\">번역 <strong>설명</strong><script>bad()</script></p>",
                "en", "ko", false));

        TitleContentTranslationResponse response = new TitleContentTranslationService(
                engine, new TranslationSourceRegistry(List.of(reader)), new PostContentSanitizer())
                .translate(TranslatableContentType.COURSE, 9L, "ko", null, null);

        assertThat(response.translatedContent()).isEqualTo("<p>번역 <strong>설명</strong></p>");
    }

    @Test
    void unavailableCourseIsRejectedBeforeContentCacheOrSharedCacheLookup() {
        ContentTranslationService engine = mock(ContentTranslationService.class);
        TranslationSourceReader reader = reader(null, null);

        assertThatThrownBy(() -> new TitleContentTranslationService(
                engine, new TranslationSourceRegistry(List.of(reader)), new PostContentSanitizer())
                .translate(TranslatableContentType.COURSE, 99L, "ko", null, null))
                .isInstanceOf(TranslationNotFoundException.class);

        verify(engine, never()).translate(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private TranslationSourceReader reader(
            TranslationSourceSnapshot title, TranslationSourceSnapshot content) {
        return new TranslationSourceReader() {
            @Override
            public TranslatableContentType contentType() {
                return TranslatableContentType.COURSE;
            }

            @Override
            public Optional<TranslationSourceSnapshot> findVisible(Long contentId) {
                return Optional.ofNullable(content);
            }

            @Override
            public Optional<TranslationSourceSnapshot> findVisible(Long contentId, String field) {
                return Optional.ofNullable("title".equals(field) ? title
                        : "content".equals(field) ? content : null);
            }

            @Override
            public Optional<TranslationSourceSnapshot> findVisibleForUpdate(Long contentId) {
                return findVisible(contentId);
            }
        };
    }

    private TranslationSourceSnapshot snapshot(
            String field, String text, String language, String mimeType) {
        return new TranslationSourceSnapshot(
                TranslatableContentType.COURSE, 9L, field, text, language,
                Timestamp.from(Instant.parse("2026-09-09T01:00:00Z")), mimeType);
    }
}
