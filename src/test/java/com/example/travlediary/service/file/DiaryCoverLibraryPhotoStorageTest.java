package com.example.travlediary.service.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiaryCoverLibraryPhotoStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void copiesAPublicUploadIntoTheSeparatePrivateRoot() throws Exception {
        Path publicRoot = temporaryDirectory.resolve("public-uploads");
        Path privateRoot = temporaryDirectory.resolve("private-cover-library");
        Path source = publicRoot.resolve("diary-cover-designs/original.jpg");
        Files.createDirectories(source.getParent());
        Files.write(source, jpeg());
        DiaryCoverLibraryPhotoStorage storage =
                new DiaryCoverLibraryPhotoStorage(publicRoot.toString(), privateRoot.toString());

        DiaryCoverLibraryPhotoStorage.StoredPhoto stored =
                storage.copyFromPublicUpload("/uploads/diary-cover-designs/original.jpg");

        assertThat(stored.storageKey()).startsWith("photos/").endsWith(".jpg");
        assertThat(stored.storageKey()).doesNotContain("/uploads/");
        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        assertThat(stored.fileSize()).isEqualTo(jpeg().length);
        assertThat(privateRoot.resolve(stored.storageKey())).hasBinaryContent(jpeg());
        assertThat(privateRoot.resolve(stored.storageKey()).normalize())
                .isNotEqualTo(source.normalize());

        assertThat(storage.delete(stored.storageKey())).isTrue();
        assertThat(privateRoot.resolve(stored.storageKey())).doesNotExist();
        assertThat(source).isRegularFile();
    }

    @Test
    void rejectsMissingExternalAndTraversalSources() {
        Path publicRoot = temporaryDirectory.resolve("public-uploads");
        Path privateRoot = temporaryDirectory.resolve("private-cover-library");
        DiaryCoverLibraryPhotoStorage storage =
                new DiaryCoverLibraryPhotoStorage(publicRoot.toString(), privateRoot.toString());

        assertThatThrownBy(() -> storage.copyFromPublicUpload("/images/static.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.copyFromPublicUpload("/uploads/../secret.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.copyFromPublicUpload("/uploads/missing.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void privateReadsRejectTraversalAndUnmanagedStorageKeys() {
        Path publicRoot = temporaryDirectory.resolve("public-uploads");
        Path privateRoot = temporaryDirectory.resolve("private-cover-library");
        DiaryCoverLibraryPhotoStorage storage =
                new DiaryCoverLibraryPhotoStorage(publicRoot.toString(), privateRoot.toString());

        assertThatThrownBy(() -> storage.resolveForRead("../secret.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.resolveForRead("photos/../../secret.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.resolveForRead("photos/not-managed.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsConfigurationWhosePrivatePhotoDirectoryIsPubliclyExposed() {
        Path privateRoot = temporaryDirectory.resolve("storage");
        Path publicRoot = privateRoot.resolve("photos");

        assertThatThrownBy(() ->
                new DiaryCoverLibraryPhotoStorage(publicRoot.toString(), privateRoot.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("private 저장 경로");
    }

    @Test
    void privateReadsRejectNonImageContent(@TempDir Path directory) throws Exception {
        Path publicRoot = directory.resolve("public-uploads");
        Path privateRoot = directory.resolve("private-cover-library");
        String key = "photos/123e4567-e89b-12d3-a456-426614174000.jpg";
        Path invalid = privateRoot.resolve(key);
        Files.createDirectories(invalid.getParent());
        Files.writeString(invalid, "not an image");
        DiaryCoverLibraryPhotoStorage storage =
                new DiaryCoverLibraryPhotoStorage(publicRoot.toString(), privateRoot.toString());

        assertThatThrownBy(() -> storage.resolveForRead(key))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void privateReadsRejectSymbolicLinksOutsideTheStorageRoot(@TempDir Path directory)
            throws Exception {
        Path publicRoot = directory.resolve("public-uploads");
        Path privateRoot = directory.resolve("private-cover-library");
        Path outside = directory.resolve("outside.jpg");
        Files.write(outside, jpeg());
        String key = "photos/123e4567-e89b-12d3-a456-426614174000.jpg";
        Path linked = privateRoot.resolve(key);
        Files.createDirectories(linked.getParent());
        Files.createSymbolicLink(linked, outside);
        DiaryCoverLibraryPhotoStorage storage =
                new DiaryCoverLibraryPhotoStorage(publicRoot.toString(), privateRoot.toString());

        assertThatThrownBy(() -> storage.resolveForRead(key))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private byte[] jpeg() {
        return new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0, 16};
    }
}
