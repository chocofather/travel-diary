package com.example.travlediary.service.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 체험 여행일기에서 가져온 사진 한 장을 받는 자리.
 *
 * <p>이 사진은 브라우저의 IndexedDB 에서 올라온다. 거기에 무엇이 들어 있었는지는
 * 사용자가 정할 수 있으므로, "브라우저에서 이미 걸렀다" 를 근거로 삼지 않는다.
 * 확장자나 클라이언트가 말한 MIME 이 아니라 실제로 펼쳐지는지를 본다.
 */
class FileUploadServiceImportedDiaryPhotoTest {

    @TempDir
    Path uploadRoot;

    /** 진짜 사진은 받는다. 저장 자리는 회원 사진과 같은 폴더다. */
    @Test
    void realRasterPhotosAreAccepted() throws IOException {
        FileUploadService service = service();

        String jpeg = service.saveImportedDiaryPhoto(
                photo("photo0", "a.jpg", "image/jpeg", image("jpg")), "diary-pages");
        String png = service.saveImportedDiaryPhoto(
                photo("photo1", "b.png", "image/png", image("png")), "diary-covers");
        String gif = service.saveImportedDiaryPhoto(
                photo("photo2", "c.gif", "image/gif", image("gif")), "diary-pages");

        assertThat(jpeg).startsWith("/uploads/diary-pages/");
        assertThat(png).startsWith("/uploads/diary-covers/");
        assertThat(gif).startsWith("/uploads/diary-pages/");
        assertThat(uploadRoot.resolve(jpeg.substring("/uploads/".length()))).isRegularFile();
    }

    /** 빈 파일은 사진이 아니다. */
    @Test
    void anEmptyFileIsRejected() {
        FileUploadService service = service();

        assertThatThrownBy(() -> service.saveImportedDiaryPhoto(
                photo("photo0", "a.jpg", "image/jpeg", new byte[0]), "diary-pages"))
                .isInstanceOf(UnsupportedImageFormatException.class);
        assertThatThrownBy(() -> service.saveImportedDiaryPhoto(null, "diary-pages"))
                .isInstanceOf(UnsupportedImageFormatException.class);
    }

    /**
     * 이름과 MIME 만 그럴듯한 파일은 받지 않는다.
     *
     * <p>실제로 열어 보기 때문에 확장자를 바꿔 붙여도 통과하지 못한다.
     */
    @Test
    void aFileThatOnlyLooksLikeAPhotoIsRejected() {
        FileUploadService service = service();

        for (byte[] content : new byte[][]{
                "<?php system($_GET['c']); ?>".getBytes(StandardCharsets.UTF_8),
                "GIF89a<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8),
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0x00, 0x10}}) {
            assertThatThrownBy(() -> service.saveImportedDiaryPhoto(
                    photo("photo0", "a.jpg", "image/jpeg", content), "diary-pages"))
                    .isInstanceOf(UnsupportedImageFormatException.class)
                    .hasMessage(FileUploadService.UNSUPPORTED_DIARY_PHOTO_MESSAGE);
        }
    }

    /** SVG 는 글을 품을 수 있어 사진으로 받지 않는다. (읽을 reader 자체가 없다) */
    @Test
    void anSvgIsRejectedEvenWhenItCallsItselfAnImage() {
        FileUploadService service = service();
        byte[] svg = ("<svg xmlns=\"http://www.w3.org/2000/svg\">"
                + "<script>alert(1)</script></svg>").getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.saveImportedDiaryPhoto(
                photo("photo0", "a.svg", "image/svg+xml", svg), "diary-pages"))
                .isInstanceOf(UnsupportedImageFormatException.class);
        // 확장자만 사진으로 바꿔도 마찬가지다.
        assertThatThrownBy(() -> service.saveImportedDiaryPhoto(
                photo("photo0", "a.png", "image/png", svg), "diary-pages"))
                .isInstanceOf(UnsupportedImageFormatException.class);
    }

    /** 너무 큰 사진은 펼쳐 보기 전에 끊는다. */
    @Test
    void anOversizedPhotoIsRejectedBeforeItIsDecoded() {
        FileUploadService service = service();
        byte[] huge = new byte[10 * 1024 * 1024 + 1];

        assertThatThrownBy(() -> service.saveImportedDiaryPhoto(
                photo("photo0", "a.jpg", "image/jpeg", huge), "diary-pages"))
                .isInstanceOf(UnsupportedImageFormatException.class)
                .hasMessage(FileUploadService.OVERSIZED_DIARY_PHOTO_MESSAGE);
    }

    /** 받지 않은 사진은 파일로 남지 않는다. */
    @Test
    void aRejectedPhotoLeavesNothingBehind() throws IOException {
        FileUploadService service = service();

        assertThatThrownBy(() -> service.saveImportedDiaryPhoto(
                photo("photo0", "a.jpg", "image/jpeg",
                        "not an image".getBytes(StandardCharsets.UTF_8)), "diary-pages"))
                .isInstanceOf(UnsupportedImageFormatException.class);

        Path directory = uploadRoot.resolve("diary-pages");
        assertThat(java.nio.file.Files.notExists(directory)
                || java.nio.file.Files.list(directory).findAny().isEmpty()).isTrue();
    }

    /* ===== 도우미 ===== */

    private FileUploadService service() {
        return new FileUploadService(uploadRoot.toString());
    }

    private MockMultipartFile photo(String name, String fileName, String contentType,
                                    byte[] content) {
        return new MockMultipartFile(name, fileName, contentType, content);
    }

    /** 실제로 펼쳐지는 작은 사진 한 장. */
    private byte[] image(String format) throws IOException {
        BufferedImage drawn = new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertThat(ImageIO.write(drawn, format, bytes)).isTrue();
        return bytes.toByteArray();
    }
}
