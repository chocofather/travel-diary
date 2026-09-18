package com.example.travlediary.service.comment;

import com.example.travlediary.model.DestinationComment;
import com.example.travlediary.model.DestinationCommentImage;
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
import org.junit.jupiter.api.io.TempDir;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** STEP C: 신규 댓글 사진은 destination_comment_images 에만 저장된다. */
@ExtendWith(MockitoExtension.class)
class DestinationCommentImageWriteTest {

    @Mock
    private DestinationMapper destinationMapper;
    @Mock
    private DestinationCommentMapper commentMapper;
    @Mock
    private DestinationCommentImageMapper commentImageMapper;
    @Mock
    private UserMapper userMapper;

    @TempDir
    Path uploadDir;

    private DestinationCommentService service;

    @BeforeEach
    void setUp() {
        service = new DestinationCommentService(
                destinationMapper, commentMapper, commentImageMapper, userMapper,
                new FileUploadService(uploadDir.toString()), new LocalContentLanguageDetector(),
                TestWithdrawnMemberName.real());
        ReflectionTestUtils.setField(service, "uploadPath", uploadDir.toString());
    }

    @Test
    void imagesAreStoredInOrderInTheirOwnTable() {
        givenCommentIsInserted(50L);
        givenWriterExists();
        when(commentImageMapper.insert(any(DestinationCommentImage.class))).thenReturn(1);

        service.create(10L, 7L, "사진 후기",
                List.of(image("a.jpg"), image("b.jpg"), image("c.jpg")), null);

        ArgumentCaptor<DestinationCommentImage> captor =
                ArgumentCaptor.forClass(DestinationCommentImage.class);
        verify(commentImageMapper, org.mockito.Mockito.times(3)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(DestinationCommentImage::getCommentId)
                .containsExactly(50L, 50L, 50L);
        assertThat(captor.getAllValues())
                .extracting(DestinationCommentImage::getDisplayOrder)
                .containsExactly(1, 2, 3);
        assertThat(captor.getAllValues())
                .allSatisfy(image -> assertThat(image.getImageUrl()).startsWith("/uploads/comments/"));

        // 댓글 자체는 한 번만 저장된다 (사진은 별도 테이블)
        verify(commentMapper).insert(any(DestinationComment.class));
        ArgumentCaptor<DestinationComment> commentCaptor = ArgumentCaptor.forClass(DestinationComment.class);
        verify(commentMapper).insert(commentCaptor.capture());
        assertThat(commentCaptor.getValue().getSourceLanguage()).isEqualTo("ko");
    }

    @Test
    void emptyFilesAreIgnoredWhenCountingAttachments() {
        givenCommentIsInserted(50L);
        givenWriterExists();

        service.create(10L, 7L, "사진 없음",
                Arrays.asList(emptyImage(), null, emptyImage()), null);

        verify(commentImageMapper, never()).insert(any());
    }

    @Test
    void moreThanThreeImagesAreRejectedBeforeAnythingIsStored() {
        assertThatThrownBy(() -> service.create(10L, 7L, "사진 4장",
                List.of(image("a.jpg"), image("b.jpg"), image("c.jpg"), image("d.jpg")), null))
                .isInstanceOf(CommentImageLimitException.class)
                .hasMessage("사진은 최대 3장까지 첨부할 수 있습니다.");

        verify(commentMapper, never()).insert(any());
        verify(commentImageMapper, never()).insert(any());
    }

    @Test
    void storedFilesAreRemovedWhenImageRowInsertFails() throws Exception {
        givenCommentIsInserted(50L);
        when(commentImageMapper.insert(any(DestinationCommentImage.class))).thenReturn(0);

        assertThatThrownBy(() -> service.create(10L, 7L, "실패", List.of(image("a.jpg")), null))
                .isInstanceOf(IllegalStateException.class);

        // 업로드된 실제 파일이 남지 않아야 한다
        try (var files = Files.list(uploadDir.resolve("comments"))) {
            assertThat(files).isEmpty();
        }
    }

    /**
     * 여행지 댓글 사진도 공통 이미지 검증을 거친다.
     * 원본 파일명은 저장 이름에 쓰지 않고, 확장자는 판별한 형식으로 정한다.
     */
    @Test
    void storedImageNamesIgnoreTheOriginalFileName() {
        givenCommentIsInserted(50L);
        givenWriterExists();
        when(commentImageMapper.insert(any(DestinationCommentImage.class))).thenReturn(1);

        service.create(10L, 7L, "사진 후기", List.of(image("../evil name.html")), null);

        ArgumentCaptor<DestinationCommentImage> captor =
                ArgumentCaptor.forClass(DestinationCommentImage.class);
        verify(commentImageMapper).insert(captor.capture());
        assertThat(captor.getValue().getImageUrl())
                .matches("^/uploads/comments/[0-9a-f-]{36}\\.jpg$");
    }

    /**
     * HTML·스크립트 SVG·사진으로 위장한 파일은 받지 않는다.
     * 앞서 받은 정상 사진까지 지우고, 댓글도 저장하지 않으며, 폴더에 아무것도 남기지 않는다.
     */
    @Test
    void nonImageFilesAreRejectedAndLeaveNothingBehind() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] svg = ("<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (MultipartFile rejected : List.of(
                new MockMultipartFile("images", "x.html", "text/html", html),
                new MockMultipartFile("images", "x.svg", "image/svg+xml", svg),
                new MockMultipartFile("images", "x.jpg", "image/jpeg", html))) {
            assertThatThrownBy(() -> service.create(10L, 7L, "위장 파일",
                    List.of(image("ok.jpg"), rejected), null))
                    .isInstanceOf(com.example.travlediary.service.file.UnsupportedImageFormatException.class);
        }

        verify(commentMapper, never()).insert(any());
        verify(commentImageMapper, never()).insert(any());
        Path comments = uploadDir.resolve("comments");
        if (Files.exists(comments)) {
            try (var files = Files.list(comments)) {
                assertThat(files).isEmpty();
            }
        }
    }

    @Test
    void createStillRunsInOneTransaction() throws NoSuchMethodException {
        Transactional transactional = DestinationCommentService.class
                .getMethod("create", Long.class, Long.class, String.class, List.class, Long.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
    }

    @Test
    void editRedetectsAndPersistsSourceLanguage() {
        DestinationComment comment = new DestinationComment();
        comment.setId(50L);
        comment.setUserId(7L);
        comment.setDeleted(false);
        when(commentMapper.findById(50L)).thenReturn(comment);

        assertThat(service.updateComment(
                50L, 7L, "This updated comment is written clearly in English.")).isTrue();

        ArgumentCaptor<DestinationComment> captor = ArgumentCaptor.forClass(DestinationComment.class);
        verify(commentMapper).updateContent(captor.capture());
        assertThat(captor.getValue().getSourceLanguage()).isEqualTo("en");
    }

    /** insert 는 void 이므로 doAnswer 로 PK 채움만 흉내 낸다. */
    private void givenCommentIsInserted(long commentId) {
        doAnswer(invocation -> {
            invocation.getArgument(0, DestinationComment.class).setId(commentId);
            return null;
        }).when(commentMapper).insert(any(DestinationComment.class));
    }

    private void givenWriterExists() {
        User user = new User();
        user.setId(7L);
        user.setNickname("여행자");
        user.setUserRole(UserRole.USER);
        when(userMapper.findById(7L)).thenReturn(user);
    }

    /** 실제로 펼쳐지는 작은 JPEG. 댓글 사진도 공통 이미지 검증을 거치므로 진짜 사진이어야 한다. */
    private MultipartFile image(String name) {
        try {
            java.awt.image.BufferedImage drawn =
                    new java.awt.image.BufferedImage(8, 6, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(drawn, "jpg", bytes);
            return new MockMultipartFile("images", name, "image/jpeg", bytes.toByteArray());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private MultipartFile emptyImage() {
        return new MockMultipartFile("images", "", "image/jpeg", new byte[0]);
    }
}
