package com.example.travlediary.service.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 업로드 루트 밖으로 나가는 삭제가 불가능한지, 이미 없는 파일이 성공인지의 계약. */
class ManagedUploadFileDeleterTest {

    @TempDir
    Path root;

    private Path uploadRoot;
    private ManagedUploadFileDeleter deleter;

    @BeforeEach
    void setUp() throws IOException {
        uploadRoot = Files.createDirectory(root.resolve("uploads"));
        for (String directory : new String[]{
                "profiles", "diary-covers", "diary-pages", "diary-cover-designs", "posts"}) {
            Files.createDirectory(uploadRoot.resolve(directory));
        }
        deleter = new ManagedUploadFileDeleter(uploadRoot.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "profiles", "diary-covers", "diary-pages", "diary-cover-designs"})
    void aManagedFileIsDeletedFromDisk(String directory) throws IOException {
        Path file = Files.createFile(uploadRoot.resolve(directory).resolve("a.jpg"));

        assertThat(deleter.delete("/uploads/" + directory + "/a.jpg"))
                .isEqualTo(ManagedUploadFileDeleter.DeletionOutcome.DELETED);
        assertThat(file).doesNotExist();
    }

    /** 이미 없으면 목표 상태가 이뤄진 것이라 재시도할 이유가 없다. */
    @Test
    void anAlreadyMissingFileIsNotAnError() throws IOException {
        assertThat(deleter.delete("/uploads/diary-pages/missing.jpg"))
                .isEqualTo(ManagedUploadFileDeleter.DeletionOutcome.ALREADY_ABSENT);
    }

    @Test
    void traversalCannotReachOutsideTheUploadRoot() throws IOException {
        Path outside = Files.createFile(root.resolve("secret.txt"));

        assertThat(deleter.delete("/uploads/diary-pages/../../secret.txt"))
                .isEqualTo(ManagedUploadFileDeleter.DeletionOutcome.REJECTED);
        assertThat(outside).exists();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/images/diary/stickers/travel/airplane.svg",
            "https://example.test/uploads/diary-pages/a.jpg",
            "/etc/passwd",
            "/uploads/diary-pages\\..\\..\\secret.txt",
            "/uploads/",
            "/uploads",
            "uploads/diary-pages/a.jpg"
    })
    void pathsOutsideTheUploadUrlSpaceAreRejected(String imageUrl) throws IOException {
        assertThat(deleter.delete(imageUrl))
                .isEqualTo(ManagedUploadFileDeleter.DeletionOutcome.REJECTED);
    }

    @Test
    void nullIsRejected() throws IOException {
        assertThat(deleter.delete(null))
                .isEqualTo(ManagedUploadFileDeleter.DeletionOutcome.REJECTED);
    }

    /**
     * 업로드 루트 밖을 가리키는 심볼릭 링크가 폴더로 들어와 있어도 대상 파일까지 지우지 않는다.
     * 링크 자체만 지워진다.
     */
    @Test
    void aSymlinkPointingOutsideDoesNotLeakTheDeletion() throws IOException {
        Path outside = Files.createFile(root.resolve("outside.jpg"));
        Path link = uploadRoot.resolve("diary-pages").resolve("link.jpg");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException unsupported) {
            return; // 심볼릭 링크를 못 만드는 환경에서는 검증을 건너뛴다
        }

        assertThat(deleter.delete("/uploads/diary-pages/link.jpg"))
                .isEqualTo(ManagedUploadFileDeleter.DeletionOutcome.DELETED);
        assertThat(link).doesNotExist();
        assertThat(outside).exists();
    }

    /** 담긴 폴더가 루트 밖을 가리키는 링크면 그 아래 파일은 손대지 않는다. */
    @Test
    void aSymlinkedDirectoryOutsideTheRootIsRejected() throws IOException {
        Path outsideDirectory = Files.createDirectory(root.resolve("outside-dir"));
        Path victim = Files.createFile(outsideDirectory.resolve("victim.jpg"));
        Path linkedDirectory = uploadRoot.resolve("diary-pages").resolve("linked");
        try {
            Files.createSymbolicLink(linkedDirectory, outsideDirectory);
        } catch (UnsupportedOperationException | IOException unsupported) {
            return;
        }

        assertThat(deleter.delete("/uploads/diary-pages/linked/victim.jpg"))
                .isEqualTo(ManagedUploadFileDeleter.DeletionOutcome.REJECTED);
        assertThat(victim).exists();
    }
}
