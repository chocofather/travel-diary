package com.example.travlediary.service.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DiaryCoverLibraryPhotoStorage {

    private static final String PHOTO_DIRECTORY = "photos";
    private static final Pattern MANAGED_STORAGE_KEY = Pattern.compile(
            "^photos/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.(?:jpg|png|gif|webp)$",
            Pattern.CASE_INSENSITIVE);

    private final Path publicUploadRoot;
    private final Path privateStorageRoot;

    public DiaryCoverLibraryPhotoStorage(
            @Value("${custom.upload-path}") String publicUploadPath,
            @Value("${custom.cover-library-private-path}") String privateStoragePath) {
        this.publicUploadRoot = Paths.get(publicUploadPath).toAbsolutePath().normalize();
        this.privateStorageRoot = Paths.get(privateStoragePath).toAbsolutePath().normalize();
        Path privatePhotoDirectory = privateStorageRoot.resolve(PHOTO_DIRECTORY).normalize();
        if (privateStorageRoot.startsWith(publicUploadRoot)
                || privatePhotoDirectory.startsWith(publicUploadRoot)) {
            throw new IllegalStateException(
                    "표지 라이브러리 private 저장 경로는 공개 upload 경로 밖이어야 합니다.");
        }
    }

    /**
     * 개인 다이어리 저장소의 검증된 사진을 라이브러리 private 저장소로 복사한다.
     *
     * <p>내 표지 디자인 사진이 공개 업로드 폴더를 떠난 뒤의 공유 등록 경로다.
     * 부르는 쪽이 임의 경로를 넘길 수 없도록, 원본 경로는 사용자 입력이 아니라
     * {@link DiaryPrivatePhotoStorage#resolveManagedSource(String)} 가 관리 저장 키를
     * 확인해 내준 실제 경로만 받는다.
     */
    public StoredPhoto copyFromDiaryPrivateStorage(Path validatedSource) {
        if (validatedSource == null || !Files.isRegularFile(validatedSource)) {
            throw new IllegalArgumentException("공유할 사진 파일을 찾을 수 없습니다.");
        }
        return copyValidatedSource(validatedSource);
    }

    private StoredPhoto copyValidatedSource(Path source) {
        StoredImageFormat format = detectFormat(source);
        long fileSize;
        try {
            fileSize = Files.size(source);
        } catch (IOException exception) {
            throw new RuntimeException("공유 사진 파일 정보를 읽지 못했습니다.", exception);
        }
        if (fileSize <= 0) {
            throw new IllegalArgumentException("공유할 사진 파일이 비어 있습니다.");
        }

        String storageKey = PHOTO_DIRECTORY + "/" + UUID.randomUUID() + "." + format.extension();
        Path photoDirectory = privateStorageRoot.resolve(PHOTO_DIRECTORY).normalize();
        Path target = privateStorageRoot.resolve(storageKey).normalize();
        ensureContained(privateStorageRoot, photoDirectory);
        ensureContained(photoDirectory, target);

        try {
            Files.createDirectories(photoDirectory);
            Files.copy(source, target);
        } catch (IOException exception) {
            deletePathQuietly(target);
            throw new RuntimeException("공유 사진 파일을 private 저장소에 복사하지 못했습니다.", exception);
        }
        return new StoredPhoto(storageKey, format.contentType(), fileSize);
    }

    public boolean delete(String storageKey) {
        Path target = resolveManagedPath(storageKey);
        try {
            return Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new RuntimeException("공유 사진 파일을 정리하지 못했습니다.", exception);
        }
    }

    /** 다음 단계의 통제된 asset 응답에서 ACTIVE 상태 확인 후 사용할 실제 파일 경로다. */
    public Path resolveForRead(String storageKey) {
        Path target = resolveManagedPath(storageKey);
        if (Files.isSymbolicLink(target) || !Files.isRegularFile(target)) {
            throw new IllegalArgumentException("공유 사진 파일을 찾을 수 없습니다.");
        }
        try {
            ensureContained(privateStorageRoot.toRealPath(), target.toRealPath());
        } catch (IOException exception) {
            throw new IllegalArgumentException("공유 사진 파일을 찾을 수 없습니다.", exception);
        }

        StoredImageFormat format = detectFormat(target);
        if (!format.matchesStorageKey(storageKey)) {
            throw new IllegalArgumentException("공유 사진 파일 형식이 올바르지 않습니다.");
        }
        return target;
    }

    private Path resolveManagedPath(String storageKey) {
        if (storageKey == null || !MANAGED_STORAGE_KEY.matcher(storageKey).matches()) {
            throw new IllegalArgumentException("올바르지 않은 공유 사진 저장 키입니다.");
        }
        Path target = privateStorageRoot.resolve(storageKey).normalize();
        ensureContained(privateStorageRoot.resolve(PHOTO_DIRECTORY).normalize(), target);
        return target;
    }

    /** 형식 판별은 개인 다이어리 사진과 한 벌을 쓴다. 오류 문구만 이 자리의 것이다. */
    private StoredImageFormat detectFormat(Path source) {
        Optional<StoredImageFormat> format;
        try {
            format = StoredImageFormat.detect(source);
        } catch (IOException exception) {
            throw new RuntimeException("공유 사진 파일을 읽지 못했습니다.", exception);
        }
        return format.orElseThrow(() ->
                new IllegalArgumentException("JPG, PNG, GIF 또는 WebP 사진만 공유할 수 있습니다."));
    }

    private void ensureContained(Path root, Path candidate) {
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("허용된 저장 경로를 벗어날 수 없습니다.");
        }
    }

    private void deletePathQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 원래 복사 실패를 우선 전달한다.
        }
    }

    public record StoredPhoto(String storageKey, String contentType, long fileSize) {
    }
}
