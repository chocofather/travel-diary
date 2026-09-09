package com.example.travlediary.service.course;

import com.example.travlediary.dto.CourseCommentDto;
import com.example.travlediary.model.CourseComment;
import com.example.travlediary.repository.course.CourseCommentImageMapper;
import com.example.travlediary.repository.course.CourseCommentMapper;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.translation.LocalContentLanguageDetector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseCommentTranslationMetadataTest {

    private final CourseCommentMapper mapper = mock(CourseCommentMapper.class);
    private final CourseCommentImageMapper imageMapper = mock(CourseCommentImageMapper.class);
    private final FileUploadService fileUploadService = mock(FileUploadService.class);
    private final CourseCommentServiceImpl service = new CourseCommentServiceImpl(
            mapper, imageMapper, fileUploadService, new LocalContentLanguageDetector());

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void createStoresTheLocallyDetectedSourceLanguage() {
        when(mapper.existsActiveCourse(10L)).thenReturn(true);
        when(mapper.insert(any(CourseComment.class))).thenAnswer(invocation -> {
            CourseComment comment = invocation.getArgument(0);
            assertThat(comment.getSourceLanguage()).isEqualTo("en");
            comment.setId(30L);
            return 1;
        });
        when(mapper.findDtoById(30L, 7L)).thenReturn(dto("en"));

        service.create(10L, 7L,
                "This course was easy to follow and very enjoyable.", null, null);
    }

    @Test
    void createKeepsUndeterminedForNumbersSymbolsAndEmojiOnly() {
        when(mapper.existsActiveCourse(10L)).thenReturn(true);
        when(mapper.insert(any(CourseComment.class))).thenAnswer(invocation -> {
            CourseComment comment = invocation.getArgument(0);
            assertThat(comment.getSourceLanguage()).isEqualTo("und");
            comment.setId(30L);
            return 1;
        });
        when(mapper.findDtoById(30L, 7L)).thenReturn(dto("und"));

        service.create(10L, 7L, "123 !? 😊", null, null);
    }

    @Test
    void updateRedetectsAndPersistsTheSourceLanguage() {
        CourseComment existing = new CourseComment();
        existing.setId(30L);
        existing.setUserId(7L);
        when(mapper.findActiveComment(30L)).thenReturn(existing);
        when(mapper.updateContent(30L, 7L, "정말 즐거운 여행 코스였습니다.", "ko"))
                .thenReturn(1);
        when(mapper.findDtoById(30L, 7L)).thenReturn(dto("ko"));

        service.update(30L, 7L, "정말 즐거운 여행 코스였습니다.");

        verify(mapper).updateContent(30L, 7L, "정말 즐거운 여행 코스였습니다.", "ko");
    }

    @Test
    void translationIsOfferedOnlyForAVisibleCommentInAnotherSupportedLanguage() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("ko"));
        CourseCommentDto same = dto("ko");
        CourseCommentDto different = dto("en");
        CourseCommentDto undetermined = dto("und");
        CourseCommentDto hidden = dto("en");
        hidden.setDeleted(true);
        hidden.setModerated(true);
        when(mapper.existsActiveCourse(10L)).thenReturn(true);
        when(mapper.findByCourseId(10L, null))
                .thenReturn(List.of(same, different, undetermined, hidden));

        service.getComments(10L, null);

        assertThat(same.isTranslationAvailable()).isFalse();
        assertThat(different.isTranslationAvailable()).isTrue();
        assertThat(undetermined.isTranslationAvailable()).isFalse();
        assertThat(hidden.isTranslationAvailable()).isFalse();
    }

    private CourseCommentDto dto(String sourceLanguage) {
        CourseCommentDto dto = new CourseCommentDto();
        dto.setId(30L);
        dto.setSourceLanguage(sourceLanguage);
        return dto;
    }
}
