package com.example.travlediary.service.translation;

import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPostTranslationServiceTest {

    @Test
    void translatesOnlyForeignFieldsAndSanitizesTranslatedHtml() {
        ContentTranslationService engine = mock(ContentTranslationService.class);
        UserPostTranslationSourceReader reader = mock(UserPostTranslationSourceReader.class);
        when(reader.findVisible(9L, "title")).thenReturn(Optional.of(
                snapshot("title", "English title", "en", "text/plain")));
        when(reader.findVisible(9L, "content")).thenReturn(Optional.of(
                snapshot("content", "<p>한국어 본문</p>", "ko", "text/html")));
        when(engine.translate(TranslatableContentType.USER_POST, 9L, "title", "ko", "203.0.113.1", 7L))
                .thenReturn(ContentTranslationResponse.ready("번역 제목", "en", "ko", false));

        UserPostTranslationResponse response = new UserPostTranslationService(
                engine, reader, new PostContentSanitizer())
                .translate(9L, "ko", "203.0.113.1", 7L);

        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.translatedTitle()).isEqualTo("번역 제목");
        assertThat(response.translatedContent()).isNull();
        verify(engine, never()).translate(
                TranslatableContentType.USER_POST, 9L, "content", "ko", "203.0.113.1", 7L);
    }

    @Test
    void preservesSafeRichTextAndRemovesUnsafeProviderMarkup() {
        ContentTranslationService engine = mock(ContentTranslationService.class);
        UserPostTranslationSourceReader reader = mock(UserPostTranslationSourceReader.class);
        when(reader.findVisible(9L, "title")).thenReturn(Optional.of(
                snapshot("title", "한국어 제목", "ko", "text/plain")));
        when(reader.findVisible(9L, "content")).thenReturn(Optional.of(
                snapshot("content", "<p>English body content</p>", "en", "text/html")));
        when(engine.translate(TranslatableContentType.USER_POST, 9L, "content", "ko", null, null))
                .thenReturn(ContentTranslationResponse.ready(
                        "<p onclick=\"evil()\">번역 <strong>본문</strong><script>bad()</script></p>",
                        "en", "ko", false));

        UserPostTranslationResponse response = new UserPostTranslationService(
                engine, reader, new PostContentSanitizer())
                .translate(9L, "ko", null, null);

        assertThat(response.translatedContent()).isEqualTo("<p>번역 <strong>본문</strong></p>");
        assertThat(response.translatedContent()).doesNotContain("onclick", "script", "bad()");
    }

    @Test
    void unavailablePostIsRejectedBeforeAnyCacheOrProviderLookup() {
        ContentTranslationService engine = mock(ContentTranslationService.class);
        UserPostTranslationSourceReader reader = mock(UserPostTranslationSourceReader.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new UserPostTranslationService(
                        engine, reader, new PostContentSanitizer())
                        .translate(99L, "ko", null, null))
                .isInstanceOf(TranslationNotFoundException.class);

        verify(engine, never()).translate(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private TranslationSourceSnapshot snapshot(
            String field, String text, String language, String mimeType) {
        return new TranslationSourceSnapshot(
                TranslatableContentType.USER_POST, 9L, field, text, language,
                Timestamp.from(Instant.parse("2026-09-09T01:00:00Z")), mimeType);
    }
}
