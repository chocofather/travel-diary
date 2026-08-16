package com.example.travlediary.service.course;

import com.example.travlediary.dto.CourseCommentDto;
import com.example.travlediary.dto.PageResult;
import com.example.travlediary.model.CourseComment;
import com.example.travlediary.model.CourseCommentImage;
import com.example.travlediary.repository.course.CourseCommentImageMapper;
import com.example.travlediary.repository.course.CourseCommentMapper;
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

/** 코스 댓글 조회는 course_comment_images 기준으로 사진을 채운다. */
@ExtendWith(MockitoExtension.class)
class CourseCommentImageReadTest {

    @Mock
    private CourseCommentMapper courseCommentMapper;
    @Mock
    private CourseCommentImageMapper courseCommentImageMapper;
    @Mock
    private FileUploadService fileUploadService;

    private CourseCommentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CourseCommentServiceImpl(
                courseCommentMapper, courseCommentImageMapper, fileUploadService);
    }

    @Test
    void pagedCommentsReadAllImagesInOneQueryAndKeepDisplayOrder() {
        when(courseCommentMapper.existsActiveCourse(10L)).thenReturn(true);
        when(courseCommentMapper.countRootCommentThreads(10L)).thenReturn(1);
        when(courseCommentMapper.countActiveComments(10L)).thenReturn(2);
        when(courseCommentMapper.findPagedRootComments(10L, null, "latest", 5, 0))
                .thenReturn(List.of(dto(30L, null, false)));
        when(courseCommentMapper.findRepliesForRootComments(10L, null, List.of(30L)))
                .thenReturn(List.of(dto(31L, 30L, false)));
        when(courseCommentImageMapper.findByCommentIds(List.of(30L, 31L))).thenReturn(List.of(
                image(30L, "/uploads/course-comments/a.jpg", 1),
                image(30L, "/uploads/course-comments/b.jpg", 2),
                image(30L, "/uploads/course-comments/c.jpg", 3),
                image(31L, "/uploads/course-comments/reply.jpg", 1)));

        PageResult<CourseCommentDto> result = service.getCommentsPage(10L, null, 0, 5, "latest");

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getImageUrls()).containsExactly(
                "/uploads/course-comments/a.jpg",
                "/uploads/course-comments/b.jpg",
                "/uploads/course-comments/c.jpg");
        assertThat(result.getContent().get(1).getImageUrls())
                .containsExactly("/uploads/course-comments/reply.jpg");
        // 댓글마다 SELECT 하지 않는다
        verify(courseCommentImageMapper, times(1)).findByCommentIds(anyList());
    }

    @Test
    void deletedAndModeratedCommentsAreExcludedFromTheImageQueryAndGetNoImages() {
        when(courseCommentMapper.existsActiveCourse(10L)).thenReturn(true);
        when(courseCommentMapper.countRootCommentThreads(10L)).thenReturn(2);
        when(courseCommentMapper.countActiveComments(10L)).thenReturn(3);
        CourseCommentDto userDeleted = dto(30L, null, true);
        CourseCommentDto moderated = dto(31L, null, true);
        moderated.setModerated(true);
        when(courseCommentMapper.findPagedRootComments(10L, null, "latest", 5, 0))
                .thenReturn(List.of(userDeleted, moderated));
        when(courseCommentMapper.findRepliesForRootComments(10L, null, List.of(30L, 31L)))
                .thenReturn(List.of(dto(32L, 30L, false)));
        when(courseCommentImageMapper.findByCommentIds(List.of(32L))).thenReturn(List.of());

        PageResult<CourseCommentDto> result = service.getCommentsPage(10L, null, 0, 5, "latest");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(courseCommentImageMapper).findByCommentIds(captor.capture());
        assertThat(captor.getValue()).containsExactly(32L);
        // 삭제·조치된 댓글은 사진 없이 내려간다 (플레이스홀더 렌더링은 그대로)
        assertThat(result.getContent().get(0).getImageUrls()).isEmpty();
        assertThat(result.getContent().get(1).getImageUrls()).isEmpty();
    }

    @Test
    void listPathAlsoFillsImageUrlsInOneQuery() {
        when(courseCommentMapper.existsActiveCourse(10L)).thenReturn(true);
        when(courseCommentMapper.findByCourseId(10L, null))
                .thenReturn(List.of(dto(30L, null, false), dto(31L, null, false)));
        when(courseCommentImageMapper.findByCommentIds(List.of(30L, 31L)))
                .thenReturn(List.of(image(31L, "/uploads/course-comments/only.jpg", 1)));

        List<CourseCommentDto> comments = service.getComments(10L, null);

        assertThat(comments.get(0).getImageUrls()).isEmpty();
        assertThat(comments.get(1).getImageUrls())
                .containsExactly("/uploads/course-comments/only.jpg");
        verify(courseCommentImageMapper, times(1)).findByCommentIds(anyList());
    }

    @Test
    void commentsWithoutImagesGetAnEmptyListNeverNull() {
        when(courseCommentMapper.existsActiveCourse(10L)).thenReturn(true);
        when(courseCommentMapper.findByCourseId(10L, null)).thenReturn(List.of(dto(30L, null, false)));
        when(courseCommentImageMapper.findByCommentIds(List.of(30L))).thenReturn(List.of());

        assertThat(service.getComments(10L, null).get(0).getImageUrls())
                .isNotNull()
                .isEmpty();
        // DTO 기본값도 null 이 아니다 (사진 기능을 쓰지 않는 경로 대비)
        assertThat(new CourseCommentDto().getImageUrls()).isNotNull().isEmpty();
    }

    @Test
    void singleDtoPathIsReadyToReturnImagesRightAfterCreate() {
        // 등록/수정 직후 단건 반환 경로도 같은 일괄 조회를 거친다 (사진 저장은 다음 단계)
        when(courseCommentMapper.existsActiveCourse(10L)).thenReturn(true);
        when(courseCommentMapper.insert(any(CourseComment.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, CourseComment.class).setId(30L);
            return 1;
        });
        when(courseCommentMapper.findDtoById(30L, 7L)).thenReturn(dto(30L, null, false));
        when(courseCommentImageMapper.findByCommentIds(List.of(30L)))
                .thenReturn(List.of(image(30L, "/uploads/course-comments/new.jpg", 1)));

        CourseCommentDto created = service.create(10L, 7L, "새 댓글", null, null);

        assertThat(created.getImageUrls()).containsExactly("/uploads/course-comments/new.jpg");
    }

    private CourseCommentDto dto(Long id, Long parentCommentId, boolean deleted) {
        CourseCommentDto dto = new CourseCommentDto();
        dto.setId(id);
        dto.setCourseId(10L);
        dto.setParentCommentId(parentCommentId);
        dto.setDeleted(deleted);
        if (!deleted) {
            dto.setContent("내용 " + id);
            dto.setWriterNickname("작성자");
        }
        return dto;
    }

    private CourseCommentImage image(long commentId, String url, int order) {
        CourseCommentImage image = new CourseCommentImage();
        image.setCommentId(commentId);
        image.setImageUrl(url);
        image.setDisplayOrder(order);
        return image;
    }
}
