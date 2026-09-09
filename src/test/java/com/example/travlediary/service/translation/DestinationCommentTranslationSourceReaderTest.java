package com.example.travlediary.service.translation;

import com.example.travlediary.model.translation.DestinationCommentTranslationSource;
import com.example.travlediary.repository.comment.DestinationCommentMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DestinationCommentTranslationSourceReaderTest {

    @Test
    void unknownLocalLanguageCanBeCorrectedWithSupportedProviderDetection() {
        DestinationCommentMapper mapper = mock(DestinationCommentMapper.class);
        DestinationCommentTranslationSource row = new DestinationCommentTranslationSource();
        Timestamp updatedAt = Timestamp.from(Instant.parse("2026-09-09T01:00:00Z"));
        row.setContentId(8L);
        row.setSourceText("A sufficiently clear English comment.");
        row.setSourceLanguage("und");
        row.setUpdatedAt(updatedAt);
        when(mapper.findVisibleTranslationSource(8L)).thenReturn(row);
        DestinationCommentTranslationSourceReader reader =
                new DestinationCommentTranslationSourceReader(mapper);

        TranslationSourceSnapshot snapshot = reader.findVisible(8L).orElseThrow();
        reader.correctSourceLanguage(snapshot, "en");

        assertThat(snapshot.sourceLanguage()).isEqualTo("und");
        verify(mapper).correctSourceLanguage(8L, "en", updatedAt);
    }
}
