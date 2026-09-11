package com.example.travlediary.service.comment;

import com.example.travlediary.dto.CommentDto;
import com.example.travlediary.dto.PageResult;
import com.example.travlediary.model.DestinationComment;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.comment.DestinationCommentImageMapper;
import com.example.travlediary.repository.comment.DestinationCommentMapper;
import com.example.travlediary.repository.destination.DestinationMapper;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.translation.LocalContentLanguageDetector;
import com.example.travlediary.service.user.TestWithdrawnMemberName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;

import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 여행지 댓글은 작성자를 users 행 그대로 중첩 매핑해서 쓴다.
 * 그래서 판정 기준이 status 인지, 그리고 익명 닉네임이 응답에 실리지 않는지를 여기서 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class DestinationCommentWithdrawnWriterTest {

    private static final String ANONYMIZED_NICKNAME = "탈퇴b62167acec";

    @Mock
    private DestinationMapper destinationMapper;
    @Mock
    private DestinationCommentMapper commentMapper;
    @Mock
    private DestinationCommentImageMapper commentImageMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private FileUploadService fileUploadService;

    private DestinationCommentService service;

    @BeforeEach
    void setUp() {
        service = new DestinationCommentService(
                destinationMapper, commentMapper, commentImageMapper, userMapper, fileUploadService,
                new LocalContentLanguageDetector(), TestWithdrawnMemberName.real());
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    /** 최종 탈퇴 전이라면 상태와 무관하게 기존 닉네임 그대로다(탈퇴 유예는 복구할 수 있는 계정이다). */
    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"ACTIVE", "RESTRICTED", "WITHDRAWAL_PENDING"})
    void aMemberThatIsNotFinallyDeletedKeepsTheirNickname(UserStatus status) {
        givenOneComment(writer("여행자민준", status));

        CommentDto comment = pagedComments().getContent().get(0);

        assertThat(comment.getWriter().getNickname()).isEqualTo("여행자민준");
        assertThat(comment.getWriter().isWithdrawn()).isFalse();
    }

    @Test
    void aFinallyDeletedMemberNeverExposesTheAnonymizedNickname() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        givenOneComment(writer(ANONYMIZED_NICKNAME, UserStatus.DEACTIVATED));

        CommentDto comment = pagedComments().getContent().get(0);

        assertThat(comment.getWriter().getNickname()).isEqualTo("탈퇴한 회원");
        // 공개 프로필이 없으므로 브라우저가 링크를 만들지 않도록 상태도 함께 내려간다.
        assertThat(comment.getWriter().isWithdrawn()).isTrue();
    }

    @Test
    void theLabelFollowsTheRequestLanguage() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("zh-TW"));
        givenOneComment(writer(ANONYMIZED_NICKNAME, UserStatus.DEACTIVATED));

        assertThat(pagedComments().getContent().get(0).getWriter().getNickname())
                .isEqualTo("已註銷會員");
    }

    /** 최종 파기에서 profile_image 는 NULL 이 된다. 화면의 기본 이미지 처리로 넘어가야 한다. */
    @Test
    void aClearedProfileImageStaysNullSoTheViewFallbackApplies() {
        User writer = writer(ANONYMIZED_NICKNAME, UserStatus.DEACTIVATED);
        writer.setProfileImage(null);
        givenOneComment(writer);

        assertThat(pagedComments().getContent().get(0).getWriter().getProfileImage()).isNull();
    }

    private PageResult<CommentDto> pagedComments() {
        return service.getCommentsPaged(10L, null, 0, 5, "latest");
    }

    private void givenOneComment(User writer) {
        when(commentMapper.countRootComments(10L)).thenReturn(1);
        when(commentMapper.countByDestinationId(10L)).thenReturn(1);
        when(commentMapper.findPagedParentComments(10L, 0, 5, "latest"))
                .thenReturn(List.of(comment(writer)));
        when(commentMapper.findRepliesForParents(10L, List.of(1L))).thenReturn(List.of());
    }

    private DestinationComment comment(User writer) {
        DestinationComment comment = new DestinationComment();
        comment.setId(1L);
        comment.setContent("좋은 여행지였어요");
        comment.setLikes(0);
        comment.setDeleted(false);
        comment.setUserId(writer.getId());
        comment.setDestinationId(10L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);
        comment.setWriter(writer);
        return comment;
    }

    private User writer(String nickname, UserStatus status) {
        User writer = new User();
        writer.setId(7L);
        writer.setNickname(nickname);
        writer.setStatus(status);
        writer.setProfileImage("/uploads/profiles/p.jpg");
        return writer;
    }
}
