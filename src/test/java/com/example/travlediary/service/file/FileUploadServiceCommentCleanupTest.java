package com.example.travlediary.service.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUploadServiceCommentCleanupTest {

    @TempDir
    Path uploadRoot;

    @Test
    void deletesManagedCommentFile() throws Exception {
        Path image = commentImage("11111111-2222-3333-4444-555555555555_photo.jpg");
        FileUploadService service = service();

        assertThat(service.deleteCommentFile(
                "/uploads/comments/11111111-2222-3333-4444-555555555555_photo.jpg")).isTrue();
        assertThat(image).doesNotExist();
    }

    @Test
    void missingCommentFileIsNotAnError() {
        assertThat(service().deleteCommentFile("/uploads/comments/gone.jpg")).isFalse();
    }

    @Test
    void refusesToDeleteOutsideManagedCommentDirectory() throws Exception {
        Path outside = Files.write(uploadRoot.resolve("outside.jpg"), new byte[]{1, 2, 3});
        commentImage("keep.jpg");
        FileUploadService service = service();

        assertThatThrownBy(() -> service.deleteCommentFile("/uploads/comments/../outside.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(outside).exists();
    }

    @Test
    void refusesDestinationAndExternalAndAbsolutePaths() throws Exception {
        Path destinationImage = Files.write(
                Files.createDirectories(uploadRoot.resolve("destinations")).resolve("keep.jpg"),
                new byte[]{1, 2, 3});
        FileUploadService service = service();

        for (String notAComment : new String[]{
                "/uploads/destinations/keep.jpg",
                "https://cdn.example.com/comments/remote.jpg",
                "/var/tmp/keep.jpg",
                "/uploads/comments/nested/deep.jpg",
                null}) {
            assertThatThrownBy(() -> service.deleteCommentFile(notAComment))
                    .as("경로 %s", notAComment)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(destinationImage).exists();
    }

    private FileUploadService service() {
        return new FileUploadService(uploadRoot.toString());
    }

    private Path commentImage(String fileName) throws Exception {
        Path comments = Files.createDirectories(uploadRoot.resolve("comments"));
        return Files.write(comments.resolve(fileName), new byte[]{1, 2, 3});
    }
}
