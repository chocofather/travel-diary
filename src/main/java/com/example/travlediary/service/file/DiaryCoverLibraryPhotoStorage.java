package com.example.travlediary.service.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DiaryCoverLibraryPhotoStorage {

    private static final String PUBLIC_URL_PREFIX = "/uploads/";
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

    public StoredPhoto copyFromPublicUpload(String sourceUrl) {
        Path source = resolvePublicSource(sourceUrl);
        PhotoFormat format = detectFormat(source);
        long fileSize;
        try {
            fileSize = Files.size(source);
        } catch (IOException exception) {
            throw new RuntimeException("공유 사진 파일 정보를 읽지 못했습니다.", exception);
        }
        if (fileSize <= 0) {
            throw new IllegalArgumentException("공유할 사진 파일이 비어 있습니다.");
        }

        String storageKey = PHOTO_DIRECTORY + "/" + UUID.randomUUID() + "." + format.extension;
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
        return new StoredPhoto(storageKey, format.contentType, fileSize);
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

        PhotoFormat format = detectFormat(target);
        if (!storageKey.toLowerCase(Locale.ROOT).endsWith("." + format.extension)) {
            throw new IllegalArgumentException("공유 사진 파일 형식이 올바르지 않습니다.");
        }
        return target;
    }

    private Path resolvePublicSource(String sourceUrl) {
        if (sourceUrl == null || !sourceUrl.startsWith(PUBLIC_URL_PREFIX)) {
            throw new IllegalArgumentException("공개 업로드 사진만 라이브러리에 포함할 수 있습니다.");
        }
        String relative = sourceUrl.substring(PUBLIC_URL_PREFIX.length());
        if (relative.isBlank()) {
            throw new IllegalArgumentException("공유할 사진 파일을 찾을 수 없습니다.");
        }
        Path source = publicUploadRoot.resolve(relative).normalize();
        ensureContained(publicUploadRoot, source);
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("공유할 사진 파일을 찾을 수 없습니다.");
        }
        return source;
    }

    private Path resolveManagedPath(String storageKey) {
        if (storageKey == null || !MANAGED_STORAGE_KEY.matcher(storageKey).matches()) {
            throw new IllegalArgumentException("올바르지 않은 공유 사진 저장 키입니다.");
        }
        Path target = privateStorageRoot.resolve(storageKey).normalize();
        ensureContained(privateStorageRoot.resolve(PHOTO_DIRECTORY).normalize(), target);
        return target;
    }

    private PhotoFormat detectFormat(Path source) {
        byte[] header = new byte[12];
        int length;
        try (InputStream input = Files.newInputStream(source)) {
            length = input.read(header);
        } catch (IOException exception) {
            throw new RuntimeException("공유 사진 파일을 읽지 못했습니다.", exception);
        }

        if (length >= 3 && unsigned(header[0]) == 0xff
                && unsigned(header[1]) == 0xd8 && unsigned(header[2]) == 0xff) {
            return PhotoFormat.JPEG;
        }
        if (length >= 8 && unsigned(header[0]) == 0x89 && header[1] == 'P'
                && header[2] == 'N' && header[3] == 'G'
                && unsigned(header[4]) == 0x0d && unsigned(header[5]) == 0x0a
                && unsigned(header[6]) == 0x1a && unsigned(header[7]) == 0x0a) {
            return PhotoFormat.PNG;
        }
        if (length >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                && header[3] == '8' && (header[4] == '7' || header[4] == '9')
                && header[5] == 'a') {
            return PhotoFormat.GIF;
        }
        if (length >= 12 && header[0] == 'R' && header[1] == 'I'
                && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E'
                && header[10] == 'B' && header[11] == 'P') {
            return PhotoFormat.WEBP;
        }
        throw new IllegalArgumentException("JPG, PNG, GIF 또는 WebP 사진만 공유할 수 있습니다.");
    }

    private int unsigned(byte value) {
        return value & 0xff;
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

    private enum PhotoFormat {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        GIF("gif", "image/gif"),
        WEBP("webp", "image/webp");

        private final String extension;
        private final String contentType;

        PhotoFormat(String extension, String contentType) {
            this.extension = extension.toLowerCase(Locale.ROOT);
            this.contentType = contentType;
        }
    }
}
