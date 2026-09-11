package com.example.travlediary.service.post;

import com.example.travlediary.dto.PostCommentDto;
import com.example.travlediary.model.PostComment;
import com.example.travlediary.repository.post.PostCommentImageMapper;
import com.example.travlediary.repository.post.PostCommentMapper;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.translation.LocalContentLanguageDetector;
import com.example.travlediary.service.user.TestWithdrawnMemberName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostCommentLanguageSupportTest {
    private PostCommentMapper mapper;
    private PostCommentServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(PostCommentMapper.class);
        service = new PostCommentServiceImpl(
                mapper,
                mock(PostCommentImageMapper.class),
                mock(FileUploadService.class),
                new LocalContentLanguageDetector(),
                TestWithdrawnMemberName.real());
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @ParameterizedTest
    @CsvSource({
            "맛집, ko",
            "This restaurant has a wonderful view., en",
            "このレストランは景色がとてもきれいです。, ja"
    })
    void createStoresLocallyDetectedSourceLanguage(String content, String expectedLanguage) {
        when(mapper.existsActivePost(10L)).thenReturn(true);
        when(mapper.insert(any(PostComment.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, PostComment.class).setId(30L);
            return 1;
        });
        PostCommentDto latest = new PostCommentDto();
        latest.setId(30L);
        latest.setSourceLanguage(expectedLanguage);
        when(mapper.findDtoById(30L, 7L)).thenReturn(latest);

        service.create(10L, 7L, content, null, null);

        ArgumentCaptor<PostComment> captor = ArgumentCaptor.forClass(PostComment.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getSourceLanguage()).isEqualTo(expectedLanguage);
    }

    @Test
    void updateRedetectsSourceLanguageFromTheNewContent() {
        PostComment existing = new PostComment();
        existing.setId(30L);
        existing.setUserId(7L);
        existing.setSourceLanguage("ko");
        when(mapper.findActiveComment(30L)).thenReturn(existing);
        when(mapper.updateContent(
                30L, 7L, "This comment is now written in English.", "en")).thenReturn(1);
        PostCommentDto latest = new PostCommentDto();
        latest.setId(30L);
        latest.setSourceLanguage("en");
        when(mapper.findDtoById(30L, 7L)).thenReturn(latest);

        service.update(30L, 7L, "This comment is now written in English.");

        verify(mapper).updateContent(
                30L, 7L, "This comment is now written in English.", "en");
    }

    @Test
    void translationAvailabilityUsesCurrentLocaleAndHidesUndeterminedLanguage() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        when(mapper.existsActivePost(10L)).thenReturn(true);
        PostCommentDto korean = visibleDto(1L, "ko");
        PostCommentDto english = visibleDto(2L, "en");
        PostCommentDto undetermined = visibleDto(3L, "und");
        when(mapper.findByPostId(10L, null))
                .thenReturn(List.of(korean, english, undetermined));

        List<PostCommentDto> comments = service.getComments(10L, null);

        assertThat(comments).extracting(PostCommentDto::isTranslationAvailable)
                .containsExactly(false, true, false);
    }

    private PostCommentDto visibleDto(Long id, String language) {
        PostCommentDto dto = new PostCommentDto();
        dto.setId(id);
        dto.setContent("content");
        dto.setSourceLanguage(language);
        return dto;
    }
}
