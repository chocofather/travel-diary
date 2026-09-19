package com.example.travlediary.service.comment;

import com.example.travlediary.dto.CommentDto;
import com.example.travlediary.dto.PageResult;
import com.example.travlediary.model.DestinationComment;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.comment.DestinationCommentImageMapper;
import com.example.travlediary.repository.comment.DestinationCommentMapper;
import com.example.travlediary.repository.destination.DestinationMapper;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.translation.LocalContentLanguageDetector;
import com.example.travlediary.service.user.TestWithdrawnMemberName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 보고 있는 사람이 관리자인지는 댓글 수와 상관없는 값 하나다.
 * 그 값을 댓글마다 다시 읽지 않는지, 그리고 읽는 방식을 바꾼 뒤에도
 * 관리자/일반/비로그인의 판정 결과가 그대로인지 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class DestinationCommentAdminFlagLookupTest {

    private static final long DESTINATION_ID = 10L;
    private static final long VIEWER_ID = 99L;

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

    @Test
    void viewerAdminFlagIsReadOnceNoMatterHowManyCommentsThePageHas() {
        givenPageOf(6, 12);
        when(userMapper.findById(VIEWER_ID)).thenReturn(viewer(UserRole.USER));

        PageResult<CommentDto> result =
                service.getCommentsPaged(DESTINATION_ID, VIEWER_ID, 0, 20, "latest");

        assertThat(result.getContent()).hasSize(18);
        // 댓글 18건을 그려도 users 조회는 한 번뿐이어야 한다.
        verify(userMapper, times(1)).findById(VIEWER_ID);
    }

    @Test
    void regularViewerGetsNoAdminFlagOnAnyComment() {
        givenPageOf(3, 3);
        when(userMapper.findById(VIEWER_ID)).thenReturn(viewer(UserRole.USER));

        PageResult<CommentDto> result =
                service.getCommentsPaged(DESTINATION_ID, VIEWER_ID, 0, 20, "latest");

        assertThat(result.getContent())
                .isNotEmpty()
                .allSatisfy(dto -> {
                    assertThat(dto.isAdmin()).isFalse();
                    assertThat(dto.getIsLoggedIn()).isTrue();
                });
    }

    @Test
    void adminViewerGetsTheAdminFlagOnEveryComment() {
        givenPageOf(3, 3);
        when(userMapper.findById(VIEWER_ID)).thenReturn(viewer(UserRole.ADMIN));

        PageResult<CommentDto> result =
                service.getCommentsPaged(DESTINATION_ID, VIEWER_ID, 0, 20, "latest");

        assertThat(result.getContent())
                .isNotEmpty()
                .allSatisfy(dto -> assertThat(dto.isAdmin()).isTrue());
        verify(userMapper, times(1)).findById(VIEWER_ID);
    }

    @Test
    void anonymousViewerNeverReadsTheUsersTable() {
        givenPageOf(3, 3);

        PageResult<CommentDto> result =
                service.getCommentsPaged(DESTINATION_ID, null, 0, 20, "latest");

        assertThat(result.getContent())
                .isNotEmpty()
                .allSatisfy(dto -> {
                    assertThat(dto.isAdmin()).isFalse();
                    assertThat(dto.getIsLoggedIn()).isFalse();
                });
        verify(userMapper, never()).findById(anyLong());
    }

    /** 부모 {@code roots} 건과 그 아래 대댓글이 달린 한 쪽을 준비한다. */
    private void givenPageOf(int roots, int replies) {
        List<DestinationComment> rootComments = new ArrayList<>();
        List<Long> rootIds = new ArrayList<>();
        for (int i = 1; i <= roots; i++) {
            rootComments.add(comment(i, null));
            rootIds.add((long) i);
        }
        List<DestinationComment> replyComments = new ArrayList<>();
        for (int i = 1; i <= replies; i++) {
            replyComments.add(comment(100 + i, rootIds.get(i % roots)));
        }

        when(commentMapper.countRootComments(DESTINATION_ID)).thenReturn(roots);
        when(commentMapper.countByDestinationId(DESTINATION_ID)).thenReturn(roots + replies);
        when(commentMapper.findPagedParentComments(DESTINATION_ID, 0, 20, "latest"))
                .thenReturn(rootComments);
        when(commentMapper.findRepliesForParents(DESTINATION_ID, rootIds))
                .thenReturn(replyComments);
        when(commentImageMapper.findByCommentIds(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
    }

    private User viewer(UserRole role) {
        User user = new User();
        user.setId(VIEWER_ID);
        user.setUserRole(role);
        return user;
    }

    private DestinationComment comment(long id, Long parentId) {
        DestinationComment comment = new DestinationComment();
        comment.setId(id);
        comment.setParentCommentId(parentId);
        comment.setContent("내용 " + id);
        comment.setLikes(0);
        comment.setDeleted(false);
        comment.setModerated(false);
        comment.setUserId(7L);
        comment.setDestinationId(DESTINATION_ID);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        comment.setCreatedAt(now);
        comment.setUpdatedAt(now);

        User writer = new User();
        writer.setId(7L);
        writer.setNickname("여행자");
        comment.setWriter(writer);
        return comment;
    }
}
