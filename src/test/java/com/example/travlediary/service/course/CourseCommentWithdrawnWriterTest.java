package com.example.travlediary.service.course;

import com.example.travlediary.dto.CourseCommentDto;
import com.example.travlediary.repository.course.CourseCommentImageMapper;
import com.example.travlediary.repository.course.CourseCommentMapper;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.translation.LocalContentLanguageDetector;
import com.example.travlediary.service.user.TestWithdrawnMemberName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 여행코스 댓글도 게시글 댓글과 같은 규칙을 쓴다.
 * 최종 탈퇴 회원의 내부 익명 닉네임은 JSON 응답에 실리지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class CourseCommentWithdrawnWriterTest {

    private static final String ANONYMIZED_NICKNAME = "탈퇴b62167acec";

    @Mock
    private CourseCommentMapper mapper;
    @Mock
    private CourseCommentImageMapper imageMapper;
    @Mock
    private FileUploadService fileUploadService;

    private CourseCommentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CourseCommentServiceImpl(
                mapper, imageMapper, fileUploadService,
                new LocalContentLanguageDetector(), TestWithdrawnMemberName.real());
        when(mapper.existsActiveCourse(10L)).thenReturn(true);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void aMemberThatIsNotFinallyDeletedKeepsTheirNickname() {
        when(mapper.findByCourseId(10L, null))
                .thenReturn(List.of(comment("여행자민준", false)));

        CourseCommentDto comment = service.getComments(10L, null).get(0);

        assertThat(comment.getWriterNickname()).isEqualTo("여행자민준");
        assertThat(comment.isWriterWithdrawn()).isFalse();
    }

    @Test
    void aFinallyDeletedMemberNeverExposesTheAnonymizedNickname() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        when(mapper.findByCourseId(10L, null))
                .thenReturn(List.of(comment(ANONYMIZED_NICKNAME, true)));

        CourseCommentDto comment = service.getComments(10L, null).get(0);

        assertThat(comment.getWriterNickname()).isEqualTo("탈퇴한 회원");
        assertThat(comment.isWriterWithdrawn()).isTrue();
    }

    @Test
    void theReplyMentionIsReplacedToo() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        CourseCommentDto reply = comment("여행자민준", false);
        reply.setReplyToNickname(ANONYMIZED_NICKNAME);
        reply.setReplyToWithdrawn(true);
        when(mapper.findByCourseId(10L, null)).thenReturn(List.of(reply));

        assertThat(service.getComments(10L, null).get(0).getReplyToNickname())
                .isEqualTo("退会した会員");
    }

    private CourseCommentDto comment(String nickname, boolean writerWithdrawn) {
        CourseCommentDto dto = new CourseCommentDto();
        dto.setId(30L);
        dto.setCourseId(10L);
        dto.setWriterNickname(nickname);
        dto.setWriterWithdrawn(writerWithdrawn);
        return dto;
    }
}
