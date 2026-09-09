package com.example.travlediary.service.translation;

import com.example.travlediary.model.translation.ContentTranslationCache;
import com.example.travlediary.model.translation.SharedTranslationCache;
import com.example.travlediary.repository.translation.ContentTranslationCacheMapper;
import com.example.travlediary.repository.translation.SharedTranslationCacheMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SharedTranslationCacheBackfillServiceTest {

    @Test
    void copiesOnlyCurrentSupportedReadyTranslationsWithoutCallingGoogle() {
        ContentTranslationCacheMapper contentMapper = mock(ContentTranslationCacheMapper.class);
        SharedTranslationCacheMapper sharedMapper = mock(SharedTranslationCacheMapper.class);
        TranslationSourceSnapshot destination = source(
                TranslatableContentType.DESTINATION_COMMENT, 100L, "hello", "en");
        TranslationSourceSnapshot post = source(
                TranslatableContentType.POST_COMMENT, 200L, "hello", "en");
        TranslationSourceSnapshot changed = source(
                TranslatableContentType.DESTINATION_COMMENT, 300L, "hello!", "en");
        TranslationSourceRegistry registry = new TranslationSourceRegistry(List.of(
                new FixedReader(TranslatableContentType.DESTINATION_COMMENT,
                        Map.of(100L, destination, 300L, changed)),
                new FixedReader(TranslatableContentType.POST_COMMENT, Map.of(200L, post))));
        List<ContentTranslationCache> rows = List.of(
                ready(1L, "DESTINATION_COMMENT", 100L, "hello"),
                ready(2L, "POST_COMMENT", 200L, "hello"),
                ready(3L, "DESTINATION_COMMENT", 300L, "old text"),
                ready(4L, "USER_POST", 400L, "hello"));
        when(contentMapper.findReadyAfter(0L, 10)).thenReturn(rows);
        when(contentMapper.findReadyAfter(4L, 10)).thenReturn(List.of());
        when(sharedMapper.insertReadyIfAbsent(org.mockito.ArgumentMatchers.any()))
                .thenReturn(1, 0);
        SharedTranslationCacheBackfillService service =
                new SharedTranslationCacheBackfillService(
                        contentMapper, sharedMapper, registry,
                        new TranslationProviderMetadata("google-v3-general-v1"), 10);

        SharedTranslationCacheBackfillResult result = service.run();

        assertThat(result.scanned()).isEqualTo(4);
        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(3);
        ArgumentCaptor<SharedTranslationCache> inserted =
                ArgumentCaptor.forClass(SharedTranslationCache.class);
        verify(sharedMapper, org.mockito.Mockito.times(2))
                .insertReadyIfAbsent(inserted.capture());
        assertThat(inserted.getAllValues())
                .allSatisfy(cache -> {
                    assertThat(cache.getSourceLanguage()).isEqualTo("en");
                    assertThat(cache.getTargetLanguage()).isEqualTo("ko");
                    assertThat(cache.getProvider()).isEqualTo("GOOGLE");
                    assertThat(cache.getTranslationProfile()).isEqualTo("google-v3-general-v1");
                    assertThat(cache.getTranslatedText()).isEqualTo("안녕하세요");
                });
        verify(contentMapper).findReadyAfter(4L, 10);
    }

    private ContentTranslationCache ready(Long id, String type, Long contentId, String sourceText) {
        ContentTranslationCache cache = new ContentTranslationCache();
        cache.setId(id);
        cache.setContentType(type);
        cache.setContentId(contentId);
        cache.setSourceField("content");
        cache.setTargetLanguage("ko");
        cache.setSourceHash(ContentTranslationService.sourceHash(sourceText));
        cache.setDetectedSourceLanguage("en");
        cache.setTranslatedText("안녕하세요");
        cache.setProvider("GOOGLE");
        cache.setStatus("READY");
        cache.setCreatedAt(Timestamp.from(Instant.parse("2026-09-09T01:00:00Z")));
        cache.setUpdatedAt(Timestamp.from(Instant.parse("2026-09-09T01:00:00Z")));
        return cache;
    }

    private TranslationSourceSnapshot source(
            TranslatableContentType type, Long id, String text, String sourceLanguage) {
        return new TranslationSourceSnapshot(type, id, "content", text, sourceLanguage,
                Timestamp.from(Instant.parse("2026-09-09T01:00:00Z")));
    }

    private static final class FixedReader implements TranslationSourceReader {
        private final TranslatableContentType type;
        private final Map<Long, TranslationSourceSnapshot> sources;

        private FixedReader(TranslatableContentType type,
                            Map<Long, TranslationSourceSnapshot> sources) {
            this.type = type;
            this.sources = sources;
        }

        @Override
        public TranslatableContentType contentType() {
            return type;
        }

        @Override
        public Optional<TranslationSourceSnapshot> findVisible(Long contentId) {
            return Optional.ofNullable(sources.get(contentId));
        }

        @Override
        public Optional<TranslationSourceSnapshot> findVisibleForUpdate(Long contentId) {
            return findVisible(contentId);
        }
    }
}
