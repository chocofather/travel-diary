package com.tripbora.service.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * saveFile(file, subDir) 가 만든 파일만 지운다. (지역 아이콘 삭제가 쓴다)
 * 예전 이름·다른 폴더·바깥 경로는 건드리지 않는다.
 */
class FileUploadServiceSavedFileCleanupTest {

    private static final String NAME = "0f8c7a3e-2b1d-4c5e-9a6f-1234567890ab.png";

    @TempDir
    Path uploadRoot;

    @Test
    void deletesServerNamedFileInTheGivenFolder() throws Exception {
        Path icon = write("icons/" + NAME);

        assertThat(service().deleteSavedFile("/uploads/icons/" + NAME, "icons")).isTrue();
        assertThat(icon).doesNotExist();
    }

    @Test
    void leavesLegacyNamesOtherFoldersAndOutsidePathsAlone() throws Exception {
        Path legacy = write("icons/japan.png");
        Path amenity = write("icons/amenities/" + NAME);
        Path outside = write(NAME);
        FileUploadService service = service();

        assertThat(service.deleteSavedFile("/uploads/icons/japan.png", "icons")).isFalse();
        assertThat(service.deleteSavedFile("/uploads/icons/amenities/" + NAME, "icons")).isFalse();
        assertThat(service.deleteSavedFile("/uploads/icons/../" + NAME, "icons")).isFalse();
        assertThat(service.deleteSavedFile("/images/" + NAME, "icons")).isFalse();
        assertThat(service.deleteSavedFile(null, "icons")).isFalse();

        assertThat(legacy).exists();
        assertThat(amenity).exists();
        assertThat(outside).exists();
    }

    @Test
    void missingFileIsNotAnError() {
        assertThat(service().deleteSavedFile("/uploads/icons/" + NAME, "icons")).isFalse();
    }

    private Path write(String relative) throws Exception {
        Path target = uploadRoot.resolve(relative);
        Files.createDirectories(target.getParent());
        return Files.write(target, new byte[]{1, 2, 3});
    }

    private FileUploadService service() {
        return new FileUploadService(uploadRoot.toString());
    }
}
