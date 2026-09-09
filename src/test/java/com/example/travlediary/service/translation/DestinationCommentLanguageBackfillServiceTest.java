package com.example.travlediary.service.translation;

import com.example.travlediary.model.translation.DestinationCommentLanguageBackfillRow;
import com.example.travlediary.repository.comment.DestinationCommentMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DestinationCommentLanguageBackfillServiceTest {

    @Test
    void classifiesOnlyUndeterminedRowsInKeysetBatchesWithoutExternalTranslation() {
        DestinationCommentMapper mapper = mock(DestinationCommentMapper.class);
        Timestamp version = Timestamp.from(Instant.parse("2026-09-09T01:00:00Z"));
        DestinationCommentLanguageBackfillRow korean = row(3L, "제주 바다가 정말 아름다웠어요.", version);
        DestinationCommentLanguageBackfillRow ambiguous = row(8L, "OK", version);
        when(mapper.findUndeterminedLanguagesAfter(0L, 2))
                .thenReturn(List.of(korean, ambiguous));
        when(mapper.findUndeterminedLanguagesAfter(8L, 2)).thenReturn(List.of());
        when(mapper.updateDetectedLanguageIfUndetermined(
                3L, "ko", korean.getContent(), version)).thenReturn(1);
        DestinationCommentLanguageBackfillService service =
                new DestinationCommentLanguageBackfillService(
                        mapper, new LocalContentLanguageDetector(), 2);

        DestinationCommentLanguageBackfillResult result = service.run();

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.undetermined()).isEqualTo(1);
        verify(mapper).updateDetectedLanguageIfUndetermined(
                3L, "ko", korean.getContent(), version);
        verify(mapper).findUndeterminedLanguagesAfter(8L, 2);
    }

    private DestinationCommentLanguageBackfillRow row(Long id, String content, Timestamp updatedAt) {
        DestinationCommentLanguageBackfillRow row = new DestinationCommentLanguageBackfillRow();
        row.setId(id);
        row.setContent(content);
        row.setUpdatedAt(updatedAt);
        return row;
    }
}
