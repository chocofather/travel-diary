package com.example.travlediary.service.kto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class KtoFestivalImageDownloadService {

    private static final String FESTIVAL_DIRECTORY = "travel-info/festivals";
    private static final String FESTIVAL_URL_PREFIX = "/uploads/travel-info/festivals/";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final int BUFFER_SIZE = 8192;
    private static final long MAX_IMAGE_PIXELS = 40_000_000L;
    private static final Pattern MANAGED_FILE_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.(?:jpg|png)$",
            Pattern.CASE_INSENSITIVE);

    private final KtoPhotoUrlValidator urlValidator;
    private final KtoPhotoHttpTransport httpTransport;
    private final Path uploadRoot;
    private final long maxFileSize;

    @Autowired
    public KtoFestivalImageDownloadService(
            @Value("${custom.upload-path}") String uploadPath,
            @Value("${spring.servlet.multipart.max-file-size:10MB}") DataSize maxFileSize
    ) {
        this(
                new KtoPhotoUrlValidator(),
                new JdkKtoPhotoHttpTransport(CONNECT_TIMEOUT, READ_TIMEOUT),
                Paths.get(uploadPath),
                maxFileSize.toBytes());
    }

    KtoFestivalImageDownloadService(
            KtoPhotoUrlValidator urlValidator,
            KtoPhotoHttpTransport httpTransport,
            Path uploadRoot,
            long maxFileSize
    ) {
        if (maxFileSize <= 0) {
            throw new IllegalArgumentException("maxFileSize must be positive");
        }
        this.urlValidator = urlValidator;
        this.httpTransport = httpTransport;
        this.uploadRoot = uploadRoot.toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
    }

    public KtoDownloadedFestivalImage download(String sourceImageUrl) {
        URI sourceUri = urlValidator.validate(sourceImageUrl);
        Path temporary = null;
        Path completedFile = null;
        boolean completed = false;

        try (KtoPhotoHttpResponse response = httpTransport.get(sourceUri)) {
            String declaredContentType = validateResponse(response);
            Path festivalDirectory = festivalDirectory();
            temporary = Files.createTempFile(festivalDirectory, ".kto-festival-", ".download");

            long fileSize = copyBounded(response.body(), temporary);
            if (fileSize == 0) {
                throw new KtoPhotoDownloadException();
            }

            ImageFormat imageFormat = detectAndValidateImage(temporary, declaredContentType);
            String generatedName = UUID.randomUUID() + "." + imageFormat.extension;
            completedFile = festivalDirectory.resolve(generatedName).normalize();
            ensureContained(festivalDirectory, completedFile);
            moveCompletedFile(temporary, completedFile);

            completed = true;
            return new KtoDownloadedFestivalImage(
                    FESTIVAL_URL_PREFIX + generatedName,
                    sourceUri.toString(),
                    imageFormat.contentType,
                    fileSize);
        } catch (InvalidKtoPhotoUrlException | KtoPhotoDownloadException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new KtoPhotoDownloadException();
        } finally {
            deleteQuietly(temporary);
            if (!completed) {
                deleteQuietly(completedFile);
            }
        }
    }

    public boolean deleteDownloadedFestivalImage(KtoDownloadedFestivalImage image) {
        return image != null && deleteDownloadedFestivalImage(image.localImageUrl());
    }

    public boolean deleteDownloadedFestivalImage(String localImageUrl) {
        String fileName = managedFileName(localImageUrl);
        if (fileName == null) {
            return false;
        }
        try {
            if (Files.notExists(uploadRoot)) {
                return false;
            }
            Path realUploadRoot = uploadRoot.toRealPath();
            Path festivalDirectory = realUploadRoot.resolve(FESTIVAL_DIRECTORY).normalize();
            ensureContained(realUploadRoot, festivalDirectory);
            if (Files.notExists(festivalDirectory)) {
                return false;
            }
            Path realFestivalDirectory = festivalDirectory.toRealPath();
            ensureContained(realUploadRoot, realFestivalDirectory);
            Path target = realFestivalDirectory.resolve(fileName).normalize();
            ensureContained(realFestivalDirectory, target);
            return Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new KtoPhotoDownloadException();
        }
    }

    private String validateResponse(KtoPhotoHttpResponse response) {
        if (response == null
                || response.statusCode() < 200 || response.statusCode() >= 300
                || response.body() == null
                || response.contentLength() > maxFileSize) {
            throw new KtoPhotoDownloadException();
        }
        String contentType = normalizeContentType(response.contentType());
        if (!("image/jpeg".equals(contentType) || "image/png".equals(contentType))) {
            throw new KtoPhotoDownloadException();
        }
        return contentType;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameters = contentType.indexOf(';');
        String mediaType = parameters >= 0 ? contentType.substring(0, parameters) : contentType;
        String normalized = mediaType.strip().toLowerCase(Locale.ROOT);
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    private long copyBounded(InputStream input, Path temporary) throws IOException {
        long total = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (OutputStream output = Files.newOutputStream(
                temporary, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            while (true) {
                long remainingWithSentinel = maxFileSize - total + 1;
                int requested = (int) Math.min(buffer.length, remainingWithSentinel);
                int read = input.read(buffer, 0, requested);
                if (read < 0) {
                    return total;
                }
                if (read == 0) {
                    continue;
                }
                total += read;
                if (total > maxFileSize) {
                    throw new KtoPhotoDownloadException();
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private ImageFormat detectAndValidateImage(Path file, String declaredContentType) {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(file.toFile())) {
            if (imageInput == null) {
                throw new KtoPhotoDownloadException();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new KtoPhotoDownloadException();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                ImageFormat detected = ImageFormat.from(reader.getFormatName());
                if (detected == null || !detected.contentType.equals(declaredContentType)) {
                    throw new KtoPhotoDownloadException();
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new KtoPhotoDownloadException();
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new KtoPhotoDownloadException();
                }
                return detected;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof KtoPhotoDownloadException downloadException) {
                throw downloadException;
            }
            throw new KtoPhotoDownloadException();
        }
    }

    private Path festivalDirectory() throws IOException {
        Files.createDirectories(uploadRoot);
        Path realUploadRoot = uploadRoot.toRealPath();
        Path festivalDirectory = realUploadRoot.resolve(FESTIVAL_DIRECTORY).normalize();
        ensureContained(realUploadRoot, festivalDirectory);
        Files.createDirectories(festivalDirectory);
        Path realFestivalDirectory = festivalDirectory.toRealPath();
        ensureContained(realUploadRoot, realFestivalDirectory);
        return realFestivalDirectory;
    }

    private String managedFileName(String localImageUrl) {
        if (localImageUrl == null || !localImageUrl.startsWith(FESTIVAL_URL_PREFIX)) {
            return null;
        }
        String fileName = localImageUrl.substring(FESTIVAL_URL_PREFIX.length());
        return MANAGED_FILE_NAME.matcher(fileName).matches() ? fileName : null;
    }

    private void moveCompletedFile(Path temporary, Path completedFile) throws IOException {
        try {
            Files.move(temporary, completedFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, completedFile);
        }
    }

    private void ensureContained(Path root, Path target) {
        if (!target.startsWith(root)) {
            throw new KtoPhotoDownloadException();
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 원래 다운로드 실패를 우선 전달한다.
        }
    }

    private enum ImageFormat {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png");

        private final String extension;
        private final String contentType;

        ImageFormat(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        private static ImageFormat from(String formatName) {
            if (formatName == null) {
                return null;
            }
            return switch (formatName.toLowerCase(Locale.ROOT)) {
                case "jpg", "jpeg" -> JPEG;
                case "png" -> PNG;
                default -> null;
            };
        }
    }
}
