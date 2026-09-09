package com.example.travlediary.service.translation;

import com.example.travlediary.model.translation.PostCommentTranslationSource;
import com.example.travlediary.repository.post.PostCommentMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostCommentTranslationSourceReaderTest {

    @Test
    void readsPostCommentContentByIdAndCorrectsOnlyTheSameUndeterminedVersion() {
        PostCommentMapper mapper = mock(PostCommentMapper.class);
        Timestamp updatedAt = Timestamp.from(Instant.parse("2026-09-09T01:00:00Z"));
        PostCommentTranslationSource row = new PostCommentTranslationSource();
        row.setContentId(8L);
        row.setSourceText("A sufficiently clear English comment.");
        row.setSourceLanguage("und");
        row.setUpdatedAt(updatedAt);
        when(mapper.findVisibleTranslationSource(8L)).thenReturn(row);
        PostCommentTranslationSourceReader reader =
                new PostCommentTranslationSourceReader(mapper);

        TranslationSourceSnapshot snapshot = reader.findVisible(8L).orElseThrow();
        reader.correctSourceLanguage(snapshot, "en");

        assertThat(reader.contentType()).isEqualTo(TranslatableContentType.POST_COMMENT);
        assertThat(snapshot.text()).isEqualTo("A sufficiently clear English comment.");
        verify(mapper).correctSourceLanguage(8L, "en", updatedAt);
    }

    @Test
    void hiddenOrDeletedPostCommentIsUnavailableWhenMapperReturnsNoPublicSource() {
        PostCommentMapper mapper = mock(PostCommentMapper.class);
        PostCommentTranslationSourceReader reader =
                new PostCommentTranslationSourceReader(mapper);

        assertThat(reader.findVisible(99L)).isEmpty();
        assertThat(reader.findVisibleForUpdate(99L)).isEmpty();
    }
}
