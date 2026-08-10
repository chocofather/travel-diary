package com.example.travlediary.service.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class FileUploadService {

    private static final long TRAVEL_INFO_THUMBNAIL_MAX_SIZE = 5L * 1024 * 1024;
    private static final String TRAVEL_INFO_THUMBNAIL_DIRECTORY = "travel-info/thumbnails";
    private static final String TRAVEL_INFO_THUMBNAIL_URL_PREFIX =
            "/uploads/" + TRAVEL_INFO_THUMBNAIL_DIRECTORY + "/";
    private static final Pattern MANAGED_THUMBNAIL_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.(?:jpg|png|webp)$",
            Pattern.CASE_INSENSITIVE);

    private final String uploadDir;

    public FileUploadService(@Value("${custom.upload-path}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    /**
     * 기본 저장 - 상위 upload 폴더에 저장
     */
    public String saveFile(MultipartFile file) {
        return saveFile(file, "");
    }

    /**
     * 하위 디렉토리 포함 저장
     * @param file MultipartFile
     * @param subDir "events", "destinations" 등 하위 폴더 이름
     * @return 저장된 파일의 웹 URL 경로 (ex: /uploads/events/uuid.jpg)
     */
    public String saveFile(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) return null;

        // 하위 폴더 지정
        File dir = subDir.isEmpty()
                ? new File(uploadDir)
                : new File(uploadDir + File.separator + subDir);

        if (!dir.exists()) dir.mkdirs();

        String original = file.getOriginalFilename();
        String ext = "";

        int dotIdx = original.lastIndexOf('.');
        if (dotIdx != -1) {
            ext = original.substring(dotIdx);
        }

        String savedName = UUID.randomUUID().toString() + ext;
        File dest = new File(dir, savedName);

        try {
            file.transferTo(dest);
            System.out.println("✅ 저장 완료: " + dest.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("❌ 저장 실패: " + e.getMessage());
            throw new RuntimeException("파일 저장 실패", e);
        }

        // 웹 경로 반환
        return subDir.isEmpty()
                ? "/uploads/" + savedName
                : "/uploads/" + subDir + "/" + savedName;
    }

    public String saveTravelInfoThumbnail(MultipartFile file) {
        ThumbnailFormat format = validateTravelInfoThumbnail(file);
        Path thumbnailDirectory = resolveThumbnailDirectory(true);
        String savedName = UUID.randomUUID() + "." + format.extension;
        Path destination = thumbnailDirectory.resolve(savedName).normalize();
        ensureContained(thumbnailDirectory, destination);

        try (InputStream input = file.getInputStream()) {
            Files.copy(input, destination);
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException ignored) {
                // 원래 저장 실패를 우선 전달한다.
            }
            throw new RuntimeException("썸네일 파일 저장에 실패했습니다.", exception);
        }
        return TRAVEL_INFO_THUMBNAIL_URL_PREFIX + savedName;
    }

    public boolean deleteTravelInfoThumbnail(String imageUrl) {
        String fileName = managedThumbnailFileName(imageUrl);
        Path thumbnailDirectory = resolveThumbnailDirectory(false);
        if (thumbnailDirectory == null) {
            return false;
        }

        Path target = thumbnailDirectory.resolve(fileName).normalize();
        ensureContained(thumbnailDirectory, target);
        try {
            return Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new RuntimeException("썸네일 파일 삭제에 실패했습니다.", exception);
        }
    }

    private ThumbnailFormat validateTravelInfoThumbnail(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("썸네일 이미지를 선택해 주세요.");
        }
        if (file.getSize() > TRAVEL_INFO_THUMBNAIL_MAX_SIZE) {
            throw new IllegalArgumentException("썸네일 이미지는 5MB 이하만 업로드할 수 있습니다.");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()
                || originalName.contains("/") || originalName.contains("\\")
                || originalName.contains("..")) {
            throw new IllegalArgumentException("올바르지 않은 썸네일 파일명입니다.");
        }

        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == originalName.length() - 1) {
            throw new IllegalArgumentException("JPG, PNG, WebP 이미지만 업로드할 수 있습니다.");
        }
        String requestedExtension = originalName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        String contentType = file.getContentType();
        ThumbnailFormat detected = detectThumbnailFormat(file);

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

    private ThumbnailFormat detectThumbnailFormat(MultipartFile file) {
        byte[] header = new byte[12];
        int length;
        try (InputStream input = file.getInputStream()) {
            length = input.read(header);
        } catch (IOException exception) {
            throw new RuntimeException("썸네일 파일을 확인할 수 없습니다.", exception);
        }

        if (length >= 3
                && unsigned(header[0]) == 0xff
                && unsigned(header[1]) == 0xd8
                && unsigned(header[2]) == 0xff) {
            return ThumbnailFormat.JPEG;
        }
        if (length >= 8
                && unsigned(header[0]) == 0x89
                && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                && unsigned(header[4]) == 0x0d && unsigned(header[5]) == 0x0a
                && unsigned(header[6]) == 0x1a && unsigned(header[7]) == 0x0a) {
            return ThumbnailFormat.PNG;
        }
        if (length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return ThumbnailFormat.WEBP;
        }
        throw new IllegalArgumentException("실제 JPG, PNG 또는 WebP 이미지 파일만 업로드할 수 있습니다.");
    }

    private Path resolveThumbnailDirectory(boolean create) {
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            if (create) {
                Files.createDirectories(uploadRoot);
            } else if (Files.notExists(uploadRoot)) {
                return null;
            }

            Path realUploadRoot = uploadRoot.toRealPath();
            Path thumbnailDirectory = realUploadRoot.resolve(TRAVEL_INFO_THUMBNAIL_DIRECTORY).normalize();
            ensureContained(realUploadRoot, thumbnailDirectory);
            if (create) {
                Files.createDirectories(thumbnailDirectory);
            } else if (Files.notExists(thumbnailDirectory)) {
                return null;
            }

            Path realThumbnailDirectory = thumbnailDirectory.toRealPath();
            ensureContained(realUploadRoot, realThumbnailDirectory);
            return realThumbnailDirectory;
        } catch (IOException exception) {
            throw new RuntimeException("썸네일 저장 경로를 준비할 수 없습니다.", exception);
        }
    }

    private String managedThumbnailFileName(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(TRAVEL_INFO_THUMBNAIL_URL_PREFIX)) {
            throw new IllegalArgumentException("관리 대상이 아닌 썸네일 경로입니다.");
        }
        String fileName = imageUrl.substring(TRAVEL_INFO_THUMBNAIL_URL_PREFIX.length());
        if (!MANAGED_THUMBNAIL_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("올바르지 않은 썸네일 경로입니다.");
        }
        return fileName;
    }

    private void ensureContained(Path root, Path target) {
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("허용된 업로드 경로를 벗어날 수 없습니다.");
        }
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    private enum ThumbnailFormat {
        JPEG("jpg"),
        PNG("png"),
        WEBP("webp");

        private final String extension;

        ThumbnailFormat(String extension) {
            this.extension = extension;
        }
    }
}
