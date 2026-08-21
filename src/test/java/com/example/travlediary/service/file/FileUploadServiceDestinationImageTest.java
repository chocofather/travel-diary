package com.example.travlediary.service.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUploadServiceDestinationImageTest {

    private static final String UPLOADED_NAME =
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:jpg|png)$";

    @TempDir
    Path uploadRoot;

    @Test
    void storesRealJpegUnderTheDestinationDirectory() throws Exception {
        String url = service().saveDestinationImage(
                file("photo.jpg", "image/jpeg", imageBytes("jpg")));

        assertThat(url).startsWith("/uploads/destinations/").endsWith(".jpg");
        assertThat(storedFile(url)).isRegularFile();
        assertThat(storedFile(url).getFileName().toString()).matches(UPLOADED_NAME);
    }

    @Test
    void storesRealPngUnderTheDestinationDirectory() throws Exception {
        String url = service().saveDestinationImage(
                file("photo.png", "image/png", imageBytes("png")));

        assertThat(url).startsWith("/uploads/destinations/").endsWith(".png");
        assertThat(storedFile(url)).isRegularFile();
    }

    @Test
    void refusesATextFileRenamedAsJpeg() throws Exception {
        assertThatThrownBy(() -> service().saveDestinationImage(
                file("fake.jpg", "application/octet-stream", "hello".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JPEG 또는 PNG");
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void refusesAForgedImageContentType() throws Exception {
        assertThatThrownBy(() -> service().saveDestinationImage(
                file("photo.jpg", "image/jpeg", "hello".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void storesByTheDetectedFormatInsteadOfTheClientExtension() throws Exception {
        // 이름은 .jpg 지만 실제 내용은 PNG → 감지된 포맷 기준으로 저장한다
        String url = service().saveDestinationImage(
                file("photo.jpg", "image/jpeg", imageBytes("png")));

        assertThat(url).endsWith(".png");
        assertThat(storedFile(url)).isRegularFile();
    }

    @Test
    void refusesNullAndEmptyUploads() throws Exception {
        assertThatThrownBy(() -> service().saveDestinationImage(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().saveDestinationImage(
                file("empty.jpg", "image/jpeg", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void refusesImageFormatsOutsideJpegAndPng() throws Exception {
        assertThatThrownBy(() -> service().saveDestinationImage(
                file("animation.gif", "image/gif", imageBytes("gif"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().saveDestinationImage(
                file("vector.svg", "image/svg+xml",
                        "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void ignoresMaliciousOriginalFileNames() throws Exception {
        String url = service().saveDestinationImage(
                file("../../evil.jpg", "image/jpeg", imageBytes("jpg")));

        assertThat(url).startsWith("/uploads/destinations/").endsWith(".jpg");
        assertThat(storedFile(url).getFileName().toString()).matches(UPLOADED_NAME);
        assertThat(storedFile(url).toRealPath().getParent())
                .isEqualTo(uploadRoot.resolve("destinations").toRealPath());
        assertThat(uploadRoot.resolve("evil.jpg")).doesNotExist();
        assertThat(uploadRoot.getParent().resolve("evil.jpg")).doesNotExist();
    }

    private FileUploadService service() {
        return new FileUploadService(uploadRoot.toString());
    }

    private MultipartFile file(String originalName, String contentType, byte[] content) {
        return new MockMultipartFile("files", originalName, contentType, content);
    }

    private byte[] imageBytes(String format) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, bytes)).as("%s writer", format).isTrue();
        return bytes.toByteArray();
    }

    private Path storedFile(String url) {
        return uploadRoot.resolve(url.replaceFirst("^/uploads/", ""));
    }

    private List<Path> storedFiles() throws IOException {
        Path destinations = uploadRoot.resolve("destinations");
        if (Files.notExists(destinations)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(destinations)) {
            return files.toList();
        }
    }
}
