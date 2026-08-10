package com.example.travlediary.service.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUploadServiceTest {

    private static final byte[] JPEG = bytes(0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10);
    private static final byte[] PNG = bytes(
            0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00, 0x00, 0x00);
    private static final byte[] WEBP = bytes(
            0x52, 0x49, 0x46, 0x46, 0x04, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50);

    @TempDir
    Path uploadRoot;

    @Test
    void savesJpegPngAndWebpWithDetectedExtensions() {
        FileUploadService service = service();

        String jpegUrl = service.saveTravelInfoThumbnail(file("thumbnail", "photo.jpeg", "image/jpeg", JPEG));
        String pngUrl = service.saveTravelInfoThumbnail(file("thumbnail", "photo.png", "image/png", PNG));
        String webpUrl = service.saveTravelInfoThumbnail(file("thumbnail", "photo.webp", "image/webp", WEBP));

        assertThat(jpegUrl).startsWith("/uploads/travel-info/thumbnails/").endsWith(".jpg");
        assertThat(pngUrl).startsWith("/uploads/travel-info/thumbnails/").endsWith(".png");
        assertThat(webpUrl).startsWith("/uploads/travel-info/thumbnails/").endsWith(".webp");
        assertThat(storedPath(jpegUrl)).isRegularFile();
        assertThat(storedPath(pngUrl)).isRegularFile();
        assertThat(storedPath(webpUrl)).isRegularFile();
    }

    @Test
    void rejectsEmptyOversizedSvgMismatchedAndNonImageFiles() {
        FileUploadService service = service();

        assertThatThrownBy(() -> service.saveTravelInfoThumbnail(
                file("thumbnail", "empty.jpg", "image/jpeg", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.saveTravelInfoThumbnail(
                file("thumbnail", "large.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");

        assertThatThrownBy(() -> service.saveTravelInfoThumbnail(
                file("thumbnail", "vector.svg", "image/svg+xml", "<svg></svg>".getBytes())))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.saveTravelInfoThumbnail(
                file("thumbnail", "disguised.png", "image/png", JPEG)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("일치하지 않습니다");

        assertThatThrownBy(() -> service.saveTravelInfoThumbnail(
                file("thumbnail", "malware.jpg", "image/jpeg", "not an image".getBytes())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("실제 JPG");
    }

    @Test
    void rejectsTraversalAndDeletesOnlyManagedThumbnailFiles() {
        FileUploadService service = service();

        assertThatThrownBy(() -> service.saveTravelInfoThumbnail(
                file("thumbnail", "../escape.jpg", "image/jpeg", JPEG)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("파일명");

        String url = service.saveTravelInfoThumbnail(file("thumbnail", "safe.jpg", "image/jpeg", JPEG));
        Path stored = storedPath(url);

        assertThat(service.deleteTravelInfoThumbnail(url)).isTrue();
        assertThat(stored).doesNotExist();
        assertThatThrownBy(() -> service.deleteTravelInfoThumbnail("/uploads/editor/other.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.deleteTravelInfoThumbnail(
                "/uploads/travel-info/thumbnails/../../outside.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private FileUploadService service() {
        return new FileUploadService(uploadRoot.toString());
    }

    private MockMultipartFile file(String name, String originalName, String contentType, byte[] content) {
        return new MockMultipartFile(name, originalName, contentType, content);
    }

    private Path storedPath(String url) {
        return uploadRoot.resolve(url.substring("/uploads/".length()));
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = (byte) values[index];
        }
        return result;
    }
}
