package com.example.travlediary.service.post;

import com.example.travlediary.dto.PageResult;
import com.example.travlediary.dto.PostCommentDto;
import com.example.travlediary.model.PostComment;
import com.example.travlediary.model.PostCommentImage;
import com.example.travlediary.repository.post.PostCommentImageMapper;
import com.example.travlediary.repository.post.PostCommentMapper;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 게시글 댓글 조회는 post_comment_images 기준으로 사진을 채운다. */
@ExtendWith(MockitoExtension.class)
class PostCommentImageReadTest {

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
                postCommentMapper, postCommentImageMapper, fileUploadService);
    }

    @Test
    void pagedCommentsReadAllImagesInOneQueryAndKeepDisplayOrder() {
        when(postCommentMapper.existsActivePost(10L)).thenReturn(true);
        when(postCommentMapper.countRootCommentThreads(10L)).thenReturn(1);
        when(postCommentMapper.countActiveComments(10L)).thenReturn(2);
        when(postCommentMapper.findPagedRootComments(10L, null, "latest", 5, 0))
                .thenReturn(List.of(dto(30L, null, false)));
        when(postCommentMapper.findRepliesForRootComments(10L, null, List.of(30L)))
                .thenReturn(List.of(dto(31L, 30L, false)));
        when(postCommentImageMapper.findByCommentIds(List.of(30L, 31L))).thenReturn(List.of(
                image(30L, "/uploads/post-comments/a.jpg", 1),
                image(30L, "/uploads/post-comments/b.jpg", 2),
                image(30L, "/uploads/post-comments/c.jpg", 3),
                image(31L, "/uploads/post-comments/reply.jpg", 1)));

        PageResult<PostCommentDto> result = service.getCommentsPage(10L, null, 0, 5, "latest");

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getImageUrls()).containsExactly(
                "/uploads/post-comments/a.jpg",
                "/uploads/post-comments/b.jpg",
                "/uploads/post-comments/c.jpg");
        assertThat(result.getContent().get(1).getImageUrls())
                .containsExactly("/uploads/post-comments/reply.jpg");
        // 댓글마다 SELECT 하지 않는다
        verify(postCommentImageMapper, times(1)).findByCommentIds(anyList());
    }

    @Test
    void deletedAndModeratedCommentsAreExcludedFromTheImageQueryAndGetNoImages() {
        when(postCommentMapper.existsActivePost(10L)).thenReturn(true);
        when(postCommentMapper.countRootCommentThreads(10L)).thenReturn(2);
        when(postCommentMapper.countActiveComments(10L)).thenReturn(3);
        PostCommentDto userDeleted = dto(30L, null, true);
        PostCommentDto moderated = dto(31L, null, true);
        moderated.setModerated(true);
        when(postCommentMapper.findPagedRootComments(10L, null, "latest", 5, 0))
                .thenReturn(List.of(userDeleted, moderated));
        when(postCommentMapper.findRepliesForRootComments(10L, null, List.of(30L, 31L)))
                .thenReturn(List.of(dto(32L, 30L, false)));
        when(postCommentImageMapper.findByCommentIds(List.of(32L))).thenReturn(List.of());

        PageResult<PostCommentDto> result = service.getCommentsPage(10L, null, 0, 5, "latest");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(postCommentImageMapper).findByCommentIds(captor.capture());
        assertThat(captor.getValue()).containsExactly(32L);
        // 삭제·조치된 댓글은 사진 없이 내려간다 (플레이스홀더 렌더링은 그대로)
        assertThat(result.getContent().get(0).getImageUrls()).isEmpty();
        assertThat(result.getContent().get(1).getImageUrls()).isEmpty();
    }

    @Test
    void listPathAlsoFillsImageUrlsInOneQuery() {
        when(postCommentMapper.existsActivePost(10L)).thenReturn(true);
        when(postCommentMapper.findByPostId(10L, null))
                .thenReturn(List.of(dto(30L, null, false), dto(31L, null, false)));
        when(postCommentImageMapper.findByCommentIds(List.of(30L, 31L)))
                .thenReturn(List.of(image(31L, "/uploads/post-comments/only.jpg", 1)));

        List<PostCommentDto> comments = service.getComments(10L, null);

        assertThat(comments.get(0).getImageUrls()).isEmpty();
        assertThat(comments.get(1).getImageUrls())
                .containsExactly("/uploads/post-comments/only.jpg");
        verify(postCommentImageMapper, times(1)).findByCommentIds(anyList());
    }

    @Test
    void commentsWithoutImagesGetAnEmptyListNeverNull() {
        when(postCommentMapper.existsActivePost(10L)).thenReturn(true);
        when(postCommentMapper.findByPostId(10L, null)).thenReturn(List.of(dto(30L, null, false)));
        when(postCommentImageMapper.findByCommentIds(List.of(30L))).thenReturn(List.of());

        assertThat(service.getComments(10L, null).get(0).getImageUrls())
                .isNotNull()
                .isEmpty();
        // DTO 기본값도 null 이 아니다 (사진 기능을 쓰지 않는 경로 대비)
        assertThat(new PostCommentDto().getImageUrls()).isNotNull().isEmpty();
    }

    @Test
    void singleDtoPathIsReadyToReturnImagesRightAfterCreate() {
        // 등록 직후 단건 반환 경로도 같은 일괄 조회를 거친다 (사진 저장은 다음 단계)
        when(postCommentMapper.existsActivePost(10L)).thenReturn(true);
        when(postCommentMapper.insert(any(PostComment.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, PostComment.class).setId(30L);
            return 1;
        });
        when(postCommentMapper.findDtoById(30L, 7L)).thenReturn(dto(30L, null, false));
        when(postCommentImageMapper.findByCommentIds(List.of(30L)))
                .thenReturn(List.of(image(30L, "/uploads/post-comments/new.jpg", 1)));

        PostCommentDto created = service.create(10L, 7L, "새 댓글", null, null);

        assertThat(created.getImageUrls()).containsExactly("/uploads/post-comments/new.jpg");
    }

    private PostCommentDto dto(Long id, Long parentCommentId, boolean deleted) {
        PostCommentDto dto = new PostCommentDto();
        dto.setId(id);
        dto.setPostId(10L);
        dto.setParentCommentId(parentCommentId);
        dto.setDeleted(deleted);
        if (!deleted) {
            dto.setContent("내용 " + id);
            dto.setWriterNickname("작성자");
        }
        return dto;
    }

    private PostCommentImage image(long commentId, String url, int order) {
        PostCommentImage image = new PostCommentImage();
        image.setCommentId(commentId);
        image.setImageUrl(url);
        image.setDisplayOrder(order);
        return image;
    }
}
