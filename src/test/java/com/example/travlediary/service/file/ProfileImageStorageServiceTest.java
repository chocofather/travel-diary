package com.example.travlediary.service.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileImageStorageServiceTest {

    private static final byte[] JPEG = bytes(0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10);
    private static final byte[] PNG = bytes(
            0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00, 0x00, 0x00);
    private static final byte[] WEBP = bytes(
            0x52, 0x49, 0x46, 0x46, 0x04, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50);

    @TempDir
    Path uploadRoot;

    @Test
    void savesJpgJpegPngAndWebpInDedicatedDirectoryWithDetectedExtensions() {
        ProfileImageStorageService service = service();

        String jpgUrl = service.saveProfileImage(file("avatar.jpg", "image/jpeg", JPEG));
        String jpegUrl = service.saveProfileImage(file("avatar.jpeg", "image/jpeg", JPEG));
        String pngUrl = service.saveProfileImage(file("avatar.png", "image/png", PNG));
        String webpUrl = service.saveProfileImage(file("avatar.webp", "image/webp", WEBP));

        assertThat(jpgUrl).startsWith("/uploads/profiles/").endsWith(".jpg");
        assertThat(jpegUrl).startsWith("/uploads/profiles/").endsWith(".jpg");
        assertThat(pngUrl).startsWith("/uploads/profiles/").endsWith(".png");
        assertThat(webpUrl).startsWith("/uploads/profiles/").endsWith(".webp");
        assertThat(storedPath(jpgUrl)).isRegularFile();
        assertThat(storedPath(jpegUrl)).isRegularFile();
        assertThat(storedPath(pngUrl)).isRegularFile();
        assertThat(storedPath(webpUrl)).isRegularFile();
    }

    @Test
    void rejectsEmptyOversizedUnsupportedMismatchedAndNonImageFiles() {
        ProfileImageStorageService service = service();

        assertThatThrownBy(() -> service.saveProfileImage(file("empty.jpg", "image/jpeg", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("선택");
        assertThatThrownBy(() -> service.saveProfileImage(
                file("large.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");
        assertThatThrownBy(() -> service.saveProfileImage(
                file("avatar.gif", "image/gif", bytes(0x47, 0x49, 0x46))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.saveProfileImage(file("avatar.png", "image/png", JPEG)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("일치하지 않습니다");
        assertThatThrownBy(() -> service.saveProfileImage(
                file("fake.jpg", "image/jpeg", "not an image".getBytes())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("실제 JPG");
    }

    @Test
    void rejectsTraversalAndDeletesOnlyManagedProfileFiles() throws Exception {
        ProfileImageStorageService service = service();

        assertThatThrownBy(() -> service.saveProfileImage(file("../escape.jpg", "image/jpeg", JPEG)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("파일명");

        String managedUrl = service.saveProfileImage(file("safe.jpg", "image/jpeg", JPEG));
        Path managedFile = storedPath(managedUrl);
        Path outside = uploadRoot.resolve("outside.jpg");
        Files.write(outside, JPEG);

        assertThat(service.deleteManagedProfileImage("/images/default.png")).isFalse();
        assertThat(service.deleteManagedProfileImage("uploads/default.png")).isFalse();
        assertThat(service.deleteManagedProfileImage("/uploads/profiles/../../outside.jpg")).isFalse();
        assertThat(service.deleteManagedProfileImage("/uploads/other/outside.jpg")).isFalse();
        assertThat(outside).isRegularFile();

        assertThat(service.deleteManagedProfileImage(managedUrl)).isTrue();
        assertThat(managedFile).doesNotExist();
    }

    private ProfileImageStorageService service() {
        return new ProfileImageStorageService(uploadRoot.toString());
    }

    private MockMultipartFile file(String originalName, String contentType, byte[] content) {
        return new MockMultipartFile("profileImageFile", originalName, contentType, content);
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
