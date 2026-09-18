package com.example.travlediary.service.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 체험 여행일기에서 가져온 사진 한 장을 받는 자리.
 *
 * <p>이 사진은 브라우저의 IndexedDB 에서 올라온다. 거기에 무엇이 들어 있었는지는
 * 사용자가 정할 수 있으므로, "브라우저에서 이미 걸렀다" 를 근거로 삼지 않는다.
 * 확장자나 클라이언트가 말한 MIME 이 아니라 실제로 펼쳐지는지를 본다.
 *
 * <p>저장 자리는 회원 사진과 같은 private 저장소다. 검증 자체는 예전과 같은 한 벌
 * ({@link FileUploadService}) 을 그대로 쓰므로 여기 기대값도 그대로다.
 */
class DiaryPrivatePhotoStorageImportedPhotoTest {

    @TempDir
    Path temporaryDirectory;

    /** 진짜 사진은 받는다. 저장 키는 예전 형식 그대로고 실제 파일만 private 루트에 생긴다. */
    @Test
    void realRasterPhotosAreAccepted() throws IOException {
        DiaryPrivatePhotoStorage storage = storage();

        String jpeg = storage.saveImportedPhoto(
                photo("photo0", "a.jpg", "image/jpeg", image("jpg")), "diary-pages");
        String png = storage.saveImportedPhoto(
                photo("photo1", "b.png", "image/png", image("png")), "diary-covers");
        String gif = storage.saveImportedPhoto(
                photo("photo2", "c.gif", "image/gif", image("gif")), "diary-pages");

        assertThat(jpeg).startsWith("/uploads/diary-pages/");
        assertThat(png).startsWith("/uploads/diary-covers/");
        assertThat(gif).startsWith("/uploads/diary-pages/");
        // 실제 파일은 공개 업로드 루트가 아니라 private 루트에 있다.
        assertThat(privateRoot().resolve(jpeg.substring("/uploads/".length()))).isRegularFile();
        assertThat(publicRoot().resolve(jpeg.substring("/uploads/".length()))).doesNotExist();
    }

    /** 빈 파일은 사진이 아니다. */
    @Test
    void anEmptyFileIsRejected() {
        DiaryPrivatePhotoStorage storage = storage();

        assertThatThrownBy(() -> storage.saveImportedPhoto(
                photo("photo0", "a.jpg", "image/jpeg", new byte[0]), "diary-pages"))
                .isInstanceOf(UnsupportedImageFormatException.class);
        assertThatThrownBy(() -> storage.saveImportedPhoto(null, "diary-pages"))
                .isInstanceOf(UnsupportedImageFormatException.class);
    }

    /**
     * 이름과 MIME 만 그럴듯한 파일은 받지 않는다.
     *
     * <p>실제로 열어 보기 때문에 확장자를 바꿔 붙여도 통과하지 못한다.
     */
    @Test
    void aFileThatOnlyLooksLikeAPhotoIsRejected() {
        DiaryPrivatePhotoStorage storage = storage();

        for (byte[] content : new byte[][]{
                "<?php system($_GET['c']); ?>".getBytes(StandardCharsets.UTF_8),
                "GIF89a<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8),
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0x00, 0x10}}) {
            assertThatThrownBy(() -> storage.saveImportedPhoto(
                    photo("photo0", "a.jpg", "image/jpeg", content), "diary-pages"))
                    .isInstanceOf(UnsupportedImageFormatException.class)
                    .hasMessage(FileUploadService.UNSUPPORTED_DIARY_PHOTO_MESSAGE);
        }
    }

    /** SVG 는 글을 품을 수 있어 사진으로 받지 않는다. (읽을 reader 자체가 없다) */
    @Test
    void anSvgIsRejectedEvenWhenItCallsItselfAnImage() {
        DiaryPrivatePhotoStorage storage = storage();
        byte[] svg = ("<svg xmlns=\"http://www.w3.org/2000/svg\">"
                + "<script>alert(1)</script></svg>").getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> storage.saveImportedPhoto(
                photo("photo0", "a.svg", "image/svg+xml", svg), "diary-pages"))
                .isInstanceOf(UnsupportedImageFormatException.class);
        // 확장자만 사진으로 바꿔도 마찬가지다.
        assertThatThrownBy(() -> storage.saveImportedPhoto(
                photo("photo0", "a.png", "image/png", svg), "diary-pages"))
                .isInstanceOf(UnsupportedImageFormatException.class);
    }

    /** 너무 큰 사진은 펼쳐 보기 전에 끊는다. */
    @Test
    void anOversizedPhotoIsRejectedBeforeItIsDecoded() {
        DiaryPrivatePhotoStorage storage = storage();
        byte[] huge = new byte[10 * 1024 * 1024 + 1];

        assertThatThrownBy(() -> storage.saveImportedPhoto(
                photo("photo0", "a.jpg", "image/jpeg", huge), "diary-pages"))
                .isInstanceOf(UnsupportedImageFormatException.class)
                .hasMessage(FileUploadService.OVERSIZED_DIARY_PHOTO_MESSAGE);
    }

    /** 받지 않은 사진은 파일로 남지 않는다. */
    @Test
    void aRejectedPhotoLeavesNothingBehind() throws IOException {
        DiaryPrivatePhotoStorage storage = storage();

        assertThatThrownBy(() -> storage.saveImportedPhoto(
                photo("photo0", "a.jpg", "image/jpeg",
                        "not an image".getBytes(StandardCharsets.UTF_8)), "diary-pages"))
                .isInstanceOf(UnsupportedImageFormatException.class);

        Path directory = privateRoot().resolve("diary-pages");
        assertThat(Files.notExists(directory)
                || Files.list(directory).findAny().isEmpty()).isTrue();
    }

    /* ===== 도우미 ===== */

    private Path publicRoot() {
        return temporaryDirectory.resolve("public-uploads");
    }

    private Path privateRoot() {
        return temporaryDirectory.resolve("private-diary");
    }

    private DiaryPrivatePhotoStorage storage() {
        return new DiaryPrivatePhotoStorage(
                new FileUploadService(publicRoot().toString()),
                publicRoot().toString(), privateRoot().toString());
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
