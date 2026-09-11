package com.example.travlediary.service.post;

import com.example.travlediary.dto.PostCommentDto;
import com.example.travlediary.repository.post.PostCommentImageMapper;
import com.example.travlediary.repository.post.PostCommentMapper;
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
 * 게시글 댓글은 브라우저가 JSON 으로 받아 그린다.
 * 그래서 최종 탈퇴 회원의 내부 익명 닉네임은 응답에 실리기 전에 공통 문구로 바뀌어야 한다.
 */
@ExtendWith(MockitoExtension.class)
class PostCommentWithdrawnWriterTest {

    private static final String ANONYMIZED_NICKNAME = "탈퇴b62167acec";

    @Mock
    private PostCommentMapper postCommentMapper;
    @Mock
    private PostCommentImageMapper postCommentImageMapper;
    @Mock
    private FileUploadService fileUploadService;

    private PostCommentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PostCommentServiceImpl(
                postCommentMapper, postCommentImageMapper, fileUploadService,
                new LocalContentLanguageDetector(), TestWithdrawnMemberName.real());
        when(postCommentMapper.existsActivePost(10L)).thenReturn(true);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    /** 아직 탈퇴하지 않은 회원(ACTIVE·탈퇴 유예 포함)은 기존 닉네임 그대로다. */
    @Test
    void aMemberThatIsNotFinallyDeletedKeepsTheirNickname() {
        when(postCommentMapper.findByPostId(10L, null))
                .thenReturn(List.of(comment("여행자민준", false)));

        PostCommentDto comment = service.getComments(10L, null).get(0);

        assertThat(comment.getWriterNickname()).isEqualTo("여행자민준");
        assertThat(comment.isWriterWithdrawn()).isFalse();
    }

    @Test
    void aFinallyDeletedMemberNeverExposesTheAnonymizedNickname() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        when(postCommentMapper.findByPostId(10L, null))
                .thenReturn(List.of(comment(ANONYMIZED_NICKNAME, true)));

        PostCommentDto comment = service.getComments(10L, null).get(0);

        assertThat(comment.getWriterNickname()).isEqualTo("탈퇴한 회원");
        // 브라우저가 프로필 링크를 만들지 않도록 상태도 함께 내려간다.
        assertThat(comment.isWriterWithdrawn()).isTrue();
    }

    @Test
    void theLabelFollowsTheRequestLanguage() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        when(postCommentMapper.findByPostId(10L, null))
                .thenReturn(List.of(comment(ANONYMIZED_NICKNAME, true)));

        assertThat(service.getComments(10L, null).get(0).getWriterNickname())
                .isEqualTo("Former member");
    }

    /** 대댓글의 @멘션도 같은 규칙을 따른다. */
    @Test
    void theReplyMentionIsReplacedToo() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        PostCommentDto reply = comment("여행자민준", false);
        reply.setReplyToNickname(ANONYMIZED_NICKNAME);
        reply.setReplyToWithdrawn(true);
        when(postCommentMapper.findByPostId(10L, null)).thenReturn(List.of(reply));

        PostCommentDto comment = service.getComments(10L, null).get(0);

        assertThat(comment.getReplyToNickname()).isEqualTo("탈퇴한 회원");
        assertThat(comment.getWriterNickname()).isEqualTo("여행자민준");
    }

    private PostCommentDto comment(String nickname, boolean writerWithdrawn) {
        PostCommentDto dto = new PostCommentDto();
        dto.setId(30L);
        dto.setPostId(10L);
        dto.setWriterNickname(nickname);
        dto.setWriterWithdrawn(writerWithdrawn);
        return dto;
    }
}
