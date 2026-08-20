package com.example.travlediary.service.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUploadServiceDestinationCleanupTest {

    @TempDir
    Path uploadRoot;

    @Test
    void deletesManagedDestinationFile() throws Exception {
        Path destinations = Files.createDirectories(uploadRoot.resolve("destinations"));
        Path image = Files.write(destinations.resolve("new-direct.jpg"), new byte[]{1, 2, 3});
        FileUploadService service = new FileUploadService(uploadRoot.toString());

        assertThat(service.deleteDestinationFile(
                "/uploads/destinations/new-direct.jpg")).isTrue();
        assertThat(image).doesNotExist();
    }

    @Test
    void refusesToDeleteOutsideManagedDestinationDirectory() throws Exception {
        Path outside = Files.write(uploadRoot.resolve("outside.jpg"), new byte[]{1, 2, 3});
        FileUploadService service = new FileUploadService(uploadRoot.toString());

        assertThatThrownBy(() -> service.deleteDestinationFile(
                "/uploads/destinations/../outside.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(outside).exists();
    }
}
