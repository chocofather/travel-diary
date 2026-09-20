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

    /**
     * 프로필 사진을 저장한다.
     *
     * <p>이 사이트에는 프로필 원본을 크게 보여 주는 화면이 없다. 가장 큰 자리가 공개 프로필의
     * 82×82 다. 그래서 원본을 따로 남기지 않고 화면에 필요한 크기까지 줄인 것 하나만 둔다.
     *
     * <p>줄이는 일은 파일을 만들기 <b>전에</b> 메모리에서 끝낸다. 그래야 줄이다가 잘못되어도
     * 반쯤 쓰다 만 파일이 남지 않는다. 줄이지 못하는 형식이거나 줄이다 실패하면 원본을
     * 그대로 저장한다 — 사진을 줄이는 일 때문에 되던 업로드가 막히면 안 된다.
     */
    public String saveProfileImage(MultipartFile file) {
        ImageFormat format = validate(file);

        byte[] content = readAll(file);
        if (format.imageIoName != null && ProfileImageResizer.canResize(format.imageIoName)) {
            content = ProfileImageResizer.optimize(content, format.imageIoName, format.keepsAlpha);
        }

        Path profileDirectory = resolveProfileDirectory(true);
        String savedName = UUID.randomUUID() + "." + format.extension;
        Path destination = profileDirectory.resolve(savedName).normalize();
        ensureContained(profileDirectory, destination);

        boolean created = false;
        try (OutputStream output = Files.newOutputStream(
                destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            created = true;
            output.write(content);
        } catch (IOException exception) {
            if (created) {
                deletePathQuietly(destination);
            }
            throw new IllegalStateException("프로필 이미지를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.", exception);
        }
        return PROFILE_URL_PREFIX + savedName;
    }

    /** 5MB 상한을 이미 확인한 뒤라 통째로 읽어도 된다. */
    private byte[] readAll(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "프로필 이미지를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.", exception);
        }
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

    /**
     * 저장 확장자와, 이 런타임에서 다시 구울 수 있는지.
     *
     * <p>WEBP 는 표준 ImageIO 가 읽지도 쓰지도 못해 {@code imageIoName} 이 없다.
     * 줄이지 못하므로 올라온 그대로 저장한다. 다른 형식으로 구워 {@code .webp} 이름을
     * 붙이는 일은 하지 않는다 — 이름과 속이 다른 파일이 된다.
     */
    private enum ImageFormat {
        JPEG("jpg", "jpeg", false),
        PNG("png", "png", true),
        WEBP("webp", null, true);

        private final String extension;
        /** ImageIO 형식 이름. 다시 구울 수 없으면 null. */
        private final String imageIoName;
        /** 투명도를 지닐 수 있는 형식인지. */
        private final boolean keepsAlpha;

        ImageFormat(String extension, String imageIoName, boolean keepsAlpha) {
            this.extension = extension;
            this.imageIoName = imageIoName;
            this.keepsAlpha = keepsAlpha;
        }
    }
}
