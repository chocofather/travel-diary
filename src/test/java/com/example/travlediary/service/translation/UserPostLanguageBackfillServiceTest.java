package com.example.travlediary.service.translation;

import com.example.travlediary.model.translation.UserPostLanguageBackfillRow;
import com.example.travlediary.repository.post.PostMapper;
import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class UserPostLanguageBackfillServiceTest {

    @Test
    void fillsOnlyUndeterminedFieldsWithLocalDetectionAndExactVersionChecks() {
        PostMapper mapper = mock(PostMapper.class);
        Timestamp version = Timestamp.from(Instant.parse("2026-09-09T01:00:00Z"));
        UserPostLanguageBackfillRow row = new UserPostLanguageBackfillRow();
        row.setId(5L);
        row.setTitle("Useful travel information for everyone");
        row.setContent("<p>この旅行先は本当に素晴らしい場所でした。</p>");
        row.setTitleSourceLanguage("und");
        row.setContentSourceLanguage("und");
        row.setUpdatedAt(version);
        when(mapper.findUndeterminedPostLanguagesAfter(0L, 2)).thenReturn(List.of(row));
        when(mapper.findUndeterminedPostLanguagesAfter(5L, 2)).thenReturn(List.of());
        when(mapper.updateTitleLanguageIfUndetermined(
                5L, "en", row.getTitle(), version)).thenReturn(1);
        when(mapper.updateContentLanguageIfUndetermined(
                5L, "ja", row.getContent(), version)).thenReturn(1);

        UserPostLanguageBackfillResult result = new UserPostLanguageBackfillService(
                mapper, new LocalContentLanguageDetector(), new PostContentSanitizer(), 2).run();

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.titleUpdated()).isEqualTo(1);
        assertThat(result.contentUpdated()).isEqualTo(1);
        verify(mapper).updateTitleLanguageIfUndetermined(5L, "en", row.getTitle(), version);
        verify(mapper).updateContentLanguageIfUndetermined(5L, "ja", row.getContent(), version);
        verify(mapper).findUndeterminedPostLanguagesAfter(5L, 2);
    }

    @Test
    void leavesAmbiguousFieldsUndeterminedWithoutAnyUpdate() {
        PostMapper mapper = mock(PostMapper.class);
        UserPostLanguageBackfillRow row = new UserPostLanguageBackfillRow();
        row.setId(7L);
        row.setTitle("123 😊");
        row.setContent("<p>!? 456</p>");
        row.setTitleSourceLanguage("und");
        row.setContentSourceLanguage("und");
        row.setUpdatedAt(Timestamp.from(Instant.parse("2026-09-09T01:00:00Z")));
        when(mapper.findUndeterminedPostLanguagesAfter(0L, 500)).thenReturn(List.of(row));
        when(mapper.findUndeterminedPostLanguagesAfter(7L, 500)).thenReturn(List.of());

        UserPostLanguageBackfillResult result = new UserPostLanguageBackfillService(
                mapper, new LocalContentLanguageDetector(), new PostContentSanitizer(), 500).run();

        assertThat(result.undeterminedFields()).isEqualTo(2);
        verify(mapper).findUndeterminedPostLanguagesAfter(0L, 500);
        verify(mapper).findUndeterminedPostLanguagesAfter(7L, 500);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void skipsAFieldWhoseLanguageWasAlreadyFilled() {
        PostMapper mapper = mock(PostMapper.class);
        Timestamp version = Timestamp.from(Instant.parse("2026-09-09T01:00:00Z"));
        UserPostLanguageBackfillRow row = new UserPostLanguageBackfillRow();
        row.setId(11L);
        row.setTitle("이미 분류된 제목");
        row.setContent("<p>English body with enough words for reliable detection.</p>");
        row.setTitleSourceLanguage("ko");
        row.setContentSourceLanguage("und");
        row.setUpdatedAt(version);
        when(mapper.findUndeterminedPostLanguagesAfter(0L, 500)).thenReturn(List.of(row));
        when(mapper.findUndeterminedPostLanguagesAfter(11L, 500)).thenReturn(List.of());
        when(mapper.updateContentLanguageIfUndetermined(
                11L, "en", row.getContent(), version)).thenReturn(1);

        UserPostLanguageBackfillResult result = new UserPostLanguageBackfillService(
                mapper, new LocalContentLanguageDetector(), new PostContentSanitizer(), 500).run();

        assertThat(result.titleUpdated()).isZero();
        assertThat(result.contentUpdated()).isEqualTo(1);
        verify(mapper, org.mockito.Mockito.never()).updateTitleLanguageIfUndetermined(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        verify(mapper).updateContentLanguageIfUndetermined(11L, "en", row.getContent(), version);
    }
}
