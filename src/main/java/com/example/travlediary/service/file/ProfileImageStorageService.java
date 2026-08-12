package com.example.travlediary.service.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ProfileImageStorageService {

    private static final long MAX_PROFILE_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final String PROFILE_DIRECTORY = "profiles";
    private static final String PROFILE_URL_PREFIX = "/uploads/profiles/";
    private static final Pattern MANAGED_PROFILE_IMAGE_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.(?:jpg|png|webp)$",
            Pattern.CASE_INSENSITIVE);

    private final String uploadDir;

    public ProfileImageStorageService(@Value("${custom.upload-path}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String saveProfileImage(MultipartFile file) {
        ImageFormat format = validate(file);
        Path profileDirectory = resolveProfileDirectory(true);
        String savedName = UUID.randomUUID() + "." + format.extension;
        Path destination = profileDirectory.resolve(savedName).normalize();
        ensureContained(profileDirectory, destination);

        boolean created = false;
        try (InputStream input = file.getInputStream();
             OutputStream output = Files.newOutputStream(
                     destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            created = true;
            input.transferTo(output);
        } catch (IOException exception) {
            if (created) {
                deletePathQuietly(destination);
            }
            throw new IllegalStateException("프로필 이미지를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.", exception);
        }
        return PROFILE_URL_PREFIX + savedName;
    }

    public boolean deleteManagedProfileImage(String imageUrl) {
        String fileName = managedFileName(imageUrl);
        if (fileName == null) {
            return false;
        }

        Path profileDirectory = resolveProfileDirectory(false);
        if (profileDirectory == null) {
            return false;
        }
        Path target = profileDirectory.resolve(fileName).normalize();
        ensureContained(profileDirectory, target);
        try {
            return Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new IllegalStateException("프로필 이미지 파일을 삭제하지 못했습니다.", exception);
        }
    }

    private ImageFormat validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("프로필 이미지 파일을 선택해 주세요.");
        }
        if (file.getSize() > MAX_PROFILE_IMAGE_SIZE) {
            throw new IllegalArgumentException("프로필 이미지는 5MB 이하만 업로드할 수 있습니다.");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()
                || originalName.contains("/") || originalName.contains("\\")
                || originalName.contains("..")) {
            throw new IllegalArgumentException("올바르지 않은 프로필 이미지 파일명입니다.");
        }

        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == originalName.length() - 1) {
            throw unsupportedFormat();
        }
        String requestedExtension = originalName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        String contentType = file.getContentType();
        ImageFormat detected = detectFormat(file);

        boolean matches = switch (detected) {
            case JPEG -> ("jpg".equals(requestedExtension) || "jpeg".equals(requestedExtension))
                    && "image/jpeg".equalsIgnoreCase(contentType);
            case PNG -> "png".equals(requestedExtension)
                    && "image/png".equalsIgnoreCase(contentType);
            case WEBP -> "webp".equals(requestedExtension)
                    && "image/webp".equalsIgnoreCase(contentType);
        };
        if (!matches) {
            throw new IllegalArgumentException("파일 확장자, MIME 형식과 실제 이미지 형식이 일치하지 않습니다.");
        }
        return detected;
    }

    private ImageFormat detectFormat(MultipartFile file) {
        byte[] header = new byte[12];
        int length;
        try (InputStream input = file.getInputStream()) {
            length = input.read(header);
        } catch (IOException exception) {
            throw new IllegalArgumentException("프로필 이미지 파일을 확인할 수 없습니다.", exception);
        }

        if (length >= 3
                && unsigned(header[0]) == 0xff
                && unsigned(header[1]) == 0xd8
                && unsigned(header[2]) == 0xff) {
            return ImageFormat.JPEG;
        }
        if (length >= 8
                && unsigned(header[0]) == 0x89
                && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                && unsigned(header[4]) == 0x0d && unsigned(header[5]) == 0x0a
                && unsigned(header[6]) == 0x1a && unsigned(header[7]) == 0x0a) {
            return ImageFormat.PNG;
        }
        if (length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return ImageFormat.WEBP;
        }
        throw new IllegalArgumentException("실제 JPG, PNG 또는 WEBP 이미지 파일만 업로드할 수 있습니다.");
    }

    private Path resolveProfileDirectory(boolean create) {
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            if (create) {
                Files.createDirectories(uploadRoot);
            } else if (Files.notExists(uploadRoot)) {
                return null;
            }

            Path realUploadRoot = uploadRoot.toRealPath();
            Path profileDirectory = realUploadRoot.resolve(PROFILE_DIRECTORY).normalize();
            ensureContained(realUploadRoot, profileDirectory);
            if (create) {
                Files.createDirectories(profileDirectory);
            } else if (Files.notExists(profileDirectory)) {
                return null;
            }

            Path realProfileDirectory = profileDirectory.toRealPath();
            ensureContained(realUploadRoot, realProfileDirectory);
            return realProfileDirectory;
        } catch (IOException exception) {
            throw new IllegalStateException("프로필 이미지 저장 경로를 준비하지 못했습니다.", exception);
        }
    }

    private String managedFileName(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(PROFILE_URL_PREFIX)) {
            return null;
        }
        String fileName = imageUrl.substring(PROFILE_URL_PREFIX.length());
        return MANAGED_PROFILE_IMAGE_NAME.matcher(fileName).matches() ? fileName : null;
    }

    private void ensureContained(Path root, Path target) {
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("허용된 프로필 이미지 경로를 벗어날 수 없습니다.");
        }
    }

    private void deletePathQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 원래 저장 실패를 우선 전달한다.
        }
    }

    private IllegalArgumentException unsupportedFormat() {
        return new IllegalArgumentException("프로필 이미지는 JPG, PNG, WEBP 형식만 사용할 수 있습니다.");
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    private enum ImageFormat {
        JPEG("jpg"),
        PNG("png"),
        WEBP("webp");

        private final String extension;

        ImageFormat(String extension) {
            this.extension = extension;
        }
    }
}
