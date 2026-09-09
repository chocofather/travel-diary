package com.example.travlediary.service.translation;

import com.example.travlediary.model.translation.PostCommentLanguageBackfillRow;
import com.example.travlediary.repository.post.PostCommentMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostCommentLanguageBackfillServiceTest {

    @Test
    void classifiesOnlyUndeterminedRowsInKeysetBatchesWithTheLocalDetector() {
        PostCommentMapper mapper = mock(PostCommentMapper.class);
        Timestamp version = Timestamp.from(Instant.parse("2026-09-09T01:00:00Z"));
        PostCommentLanguageBackfillRow korean = row(3L, "맛집", version);
        PostCommentLanguageBackfillRow ambiguous = row(8L, "123 😊", version);
        when(mapper.findUndeterminedLanguagesAfter(0L, 2))
                .thenReturn(List.of(korean, ambiguous));
        when(mapper.findUndeterminedLanguagesAfter(8L, 2)).thenReturn(List.of());
        when(mapper.updateDetectedLanguageIfUndetermined(
                3L, "ko", korean.getContent(), version)).thenReturn(1);

        PostCommentLanguageBackfillResult result = new PostCommentLanguageBackfillService(
                mapper, new LocalContentLanguageDetector(), 2).run();

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.undetermined()).isEqualTo(1);
        verify(mapper).updateDetectedLanguageIfUndetermined(
                3L, "ko", korean.getContent(), version);
        verify(mapper).findUndeterminedLanguagesAfter(8L, 2);
    }

    private PostCommentLanguageBackfillRow row(Long id, String content, Timestamp updatedAt) {
        PostCommentLanguageBackfillRow row = new PostCommentLanguageBackfillRow();
        row.setId(id);
        row.setContent(content);
        row.setUpdatedAt(updatedAt);
        return row;
    }
}
