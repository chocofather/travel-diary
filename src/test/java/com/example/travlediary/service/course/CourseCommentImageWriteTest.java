package com.example.travlediary.service.course;

import com.example.travlediary.dto.CourseCommentDto;
import com.example.travlediary.model.CourseComment;
import com.example.travlediary.model.CourseCommentImage;
import com.example.travlediary.repository.course.CourseCommentImageMapper;
import com.example.travlediary.repository.course.CourseCommentMapper;
import com.example.travlediary.service.comment.CommentImageLimitException;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 신규 코스 댓글 사진은 course_comment_images 에만 저장된다. */
@ExtendWith(MockitoExtension.class)
class CourseCommentImageWriteTest {

    @Mock
    private CourseCommentMapper courseCommentMapper;
    @Mock
    private CourseCommentImageMapper courseCommentImageMapper;
    @Mock
    private FileUploadService fileUploadService;

    @TempDir
    Path uploadDir;

    private CourseCommentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CourseCommentServiceImpl(
                courseCommentMapper, courseCommentImageMapper, fileUploadService);
        ReflectionTestUtils.setField(service, "uploadPath", uploadDir.toString());
    }

    @Test
    void commentWithoutImagesStoresNoImageRows() {
        givenCourseAndInsertedComment(30L);
        when(courseCommentMapper.findDtoById(30L, 7L)).thenReturn(dto(30L));

        service.create(10L, 7L, "사진 없는 댓글", null, null);

        verify(courseCommentImageMapper, never()).insert(any());
        verify(fileUploadService, never()).saveFile(any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void imagesAreStoredInDisplayOrderInTheirOwnTable(int count) {
        givenCourseAndInsertedComment(30L);
        givenFilesAreSaved();
        when(courseCommentImageMapper.insert(any(CourseCommentImage.class))).thenReturn(1);
        when(courseCommentMapper.findDtoById(30L, 7L)).thenReturn(dto(30L));

        service.create(10L, 7L, "사진 댓글", null, images(count));

        ArgumentCaptor<CourseCommentImage> captor = ArgumentCaptor.forClass(CourseCommentImage.class);
        verify(courseCommentImageMapper, times(count)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(CourseCommentImage::getCommentId)
                .containsOnly(30L);
        assertThat(captor.getAllValues())
                .extracting(CourseCommentImage::getDisplayOrder)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, count).boxed().toList());
        assertThat(captor.getAllValues())
                .allSatisfy(image -> assertThat(image.getImageUrl())
                        .startsWith("/uploads/course-comments/"));
        // 댓글 자체는 한 번만 저장된다 (사진은 별도 테이블)
        verify(courseCommentMapper).insert(any(CourseComment.class));
    }

    @Test
    void moreThanThreeImagesAreRejectedBeforeAnythingIsStored() {
        when(courseCommentMapper.existsActiveCourse(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(10L, 7L, "사진 4장", null, images(4)))
                .isInstanceOf(CommentImageLimitException.class)
                .hasMessage("사진은 최대 3장까지 첨부할 수 있습니다.");

        verify(courseCommentMapper, never()).insert(any());
        verify(courseCommentImageMapper, never()).insert(any());
        verify(fileUploadService, never()).saveFile(any(), anyString());
    }

    @Test
    void emptyFilesAreIgnoredWhenCountingAttachments() {
        givenCourseAndInsertedComment(30L);
        when(courseCommentMapper.findDtoById(30L, 7L)).thenReturn(dto(30L));

        // 빈 파일 4개는 첨부 0장으로 본다 (개수 제한에도 걸리지 않는다)
        service.create(10L, 7L, "빈 파일", null,
                Arrays.asList(emptyImage(), null, emptyImage(), emptyImage()));

        verify(courseCommentImageMapper, never()).insert(any());
        verify(fileUploadService, never()).saveFile(any(), anyString());
    }

    @Test
    void storedFilesAreRemovedWhenImageRowInsertFails() throws Exception {
        givenCourseAndInsertedComment(30L);
        givenFilesAreSaved();
        when(courseCommentImageMapper.insert(any(CourseCommentImage.class))).thenReturn(0);

        assertThatThrownBy(() -> service.create(10L, 7L, "실패", null, images(2)))
                .isInstanceOf(IllegalStateException.class);

        // 이번 요청에서 업로드된 실제 파일이 남지 않아야 한다
        assertThat(storedFileCount()).isZero();
    }

    @Test
    void alreadyStoredFilesAreRemovedWhenALaterFileFails() throws Exception {
        givenCourseAndInsertedComment(30L);
        when(fileUploadService.saveFile(any(MultipartFile.class), eq("course-comments")))
                .thenAnswer(invocation -> storeFile(invocation.getArgument(0)))
                .thenThrow(new RuntimeException("파일 저장 실패"));

        assertThatThrownBy(() -> service.create(10L, 7L, "저장 실패", null, images(2)))
                .isInstanceOf(RuntimeException.class);

        // 먼저 저장된 1장도 함께 정리된다 (사진 행은 애초에 만들지 않는다)
        assertThat(storedFileCount()).isZero();
        verify(courseCommentImageMapper, never()).insert(any());
    }

    @Test
    void otherRequestsFilesAreNotTouchedWhenThisRequestFails() throws Exception {
        Path directory = Files.createDirectories(uploadDir.resolve("course-comments"));
        Path otherRequestFile = Files.writeString(directory.resolve("keep-me.jpg"), "other");
        givenCourseAndInsertedComment(30L);
        givenFilesAreSaved();
        when(courseCommentImageMapper.insert(any(CourseCommentImage.class))).thenReturn(0);

        assertThatThrownBy(() -> service.create(10L, 7L, "실패", null, images(1)))
                .isInstanceOf(IllegalStateException.class);

        // 이번 요청 목록에 없는 기존 파일은 그대로 남는다
        assertThat(Files.exists(otherRequestFile)).isTrue();
        assertThat(storedFileCount()).isEqualTo(1);
    }

    @Test
    void createdDtoCarriesTheStoredImageUrls() {
        givenCourseAndInsertedComment(30L);
        givenFilesAreSaved();
        when(courseCommentImageMapper.insert(any(CourseCommentImage.class))).thenReturn(1);
        when(courseCommentMapper.findDtoById(30L, 7L)).thenReturn(dto(30L));
        when(courseCommentImageMapper.findByCommentIds(List.of(30L))).thenReturn(List.of(
                image(30L, "/uploads/course-comments/a.jpg", 1),
                image(30L, "/uploads/course-comments/b.jpg", 2)));

        CourseCommentDto created = service.create(10L, 7L, "사진 댓글", null, images(2));

        assertThat(created.getImageUrls()).containsExactly(
                "/uploads/course-comments/a.jpg", "/uploads/course-comments/b.jpg");
    }

    @Test
    void replyKeepsItsRootGroupAndAlsoStoresImages() {
        when(courseCommentMapper.existsActiveCourse(10L)).thenReturn(true);
        CourseComment root = new CourseComment();
        root.setId(20L);
        root.setCourseId(10L);
        when(courseCommentMapper.findActiveCommentForUpdate(20L)).thenReturn(root);
        when(courseCommentMapper.insert(any(CourseComment.class))).thenAnswer(invocation -> {
            CourseComment reply = invocation.getArgument(0, CourseComment.class);
            assertThat(reply.getParentCommentId()).isEqualTo(20L);
            assertThat(reply.getReplyToCommentId()).isEqualTo(20L);
            reply.setId(31L);
            return 1;
        });
        givenFilesAreSaved();
        when(courseCommentImageMapper.insert(any(CourseCommentImage.class))).thenReturn(1);
        when(courseCommentMapper.findDtoById(31L, 7L)).thenReturn(dto(31L));

        service.create(10L, 7L, "답글", 20L, images(1));

        ArgumentCaptor<CourseCommentImage> captor = ArgumentCaptor.forClass(CourseCommentImage.class);
        verify(courseCommentImageMapper).insert(captor.capture());
        assertThat(captor.getValue().getCommentId()).isEqualTo(31L);
        assertThat(captor.getValue().getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void createStillRunsInOneTransaction() throws NoSuchMethodException {
        Transactional transactional = CourseCommentServiceImpl.class
                .getMethod("create", Long.class, Long.class, String.class, Long.class, List.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
    }

    private void givenCourseAndInsertedComment(long commentId) {
        when(courseCommentMapper.existsActiveCourse(10L)).thenReturn(true);
        when(courseCommentMapper.insert(any(CourseComment.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, CourseComment.class).setId(commentId);
            return 1;
        });
    }

    /** FileUploadService 를 흉내 내 실제 파일을 만든다. (정리 여부를 파일로 확인하기 위함) */
    private void givenFilesAreSaved() {
        when(fileUploadService.saveFile(any(MultipartFile.class), eq("course-comments")))
                .thenAnswer(invocation -> storeFile(invocation.getArgument(0)));
    }

    private String storeFile(MultipartFile file) throws Exception {
        Path directory = Files.createDirectories(uploadDir.resolve("course-comments"));
        Path saved = Files.createTempFile(directory, "img", ".jpg");
        Files.write(saved, file.getBytes());
        return "/uploads/course-comments/" + saved.getFileName();
    }

    private long storedFileCount() throws Exception {
        Path directory = uploadDir.resolve("course-comments");
        if (Files.notExists(directory)) return 0;
        try (var files = Files.list(directory)) {
            return files.count();
        }
    }

    private List<MultipartFile> images(int count) {
        return IntStream.range(0, count)
                .<MultipartFile>mapToObj(index -> new MockMultipartFile(
                        "images", "image-" + index + ".jpg", "image/jpeg", new byte[]{1, 2, 3}))
                .toList();
    }

    private MultipartFile emptyImage() {
        return new MockMultipartFile("images", "", "image/jpeg", new byte[0]);
    }

    private CourseCommentDto dto(Long id) {
        CourseCommentDto dto = new CourseCommentDto();
        dto.setId(id);
        dto.setCourseId(10L);
        dto.setContent("내용 " + id);
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
