package com.example.travlediary.service.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class FileUploadService {

    private static final String DESTINATION_DIRECTORY = "destinations";
    private static final String DESTINATION_URL_PREFIX = "/uploads/destinations/";
    public static final String UNSUPPORTED_DESTINATION_IMAGE_MESSAGE =
            "JPEG 또는 PNG 이미지 파일만 업로드할 수 있습니다.";
    private static final String COMMENT_DIRECTORY = "comments";
    private static final String COMMENT_URL_PREFIX = "/uploads/comments/";
    private static final long TRAVEL_INFO_THUMBNAIL_MAX_SIZE = 5L * 1024 * 1024;
    private static final String TRAVEL_INFO_THUMBNAIL_DIRECTORY = "travel-info/thumbnails";
    private static final String TRAVEL_INFO_THUMBNAIL_URL_PREFIX =
            "/uploads/" + TRAVEL_INFO_THUMBNAIL_DIRECTORY + "/";
    private static final Pattern MANAGED_THUMBNAIL_NAME = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.(?:jpg|png|webp)$",
            Pattern.CASE_INSENSITIVE);
    private static final String AMENITY_ICON_DIRECTORY = "icons/amenities";
    private static final String AMENITY_ICON_URL_PREFIX = "/uploads/" + AMENITY_ICON_DIRECTORY + "/";
    private static final long AMENITY_ICON_MAX_SIZE = 512L * 1024;
    /** 아이콘 파일명은 code 에서 만들어지므로 code 형식을 저장 직전에 한 번 더 확인한다. */
    private static final Pattern AMENITY_CODE = Pattern.compile("^[A-Z0-9_]{2,50}$");
    public static final String UNSUPPORTED_AMENITY_ICON_MESSAGE =
            "PNG, JPG 또는 SVG 이미지 파일만 업로드할 수 있습니다.";
    public static final String UNSAFE_AMENITY_SVG_MESSAGE =
            "안전하지 않은 SVG 파일입니다. 스크립트나 외부 참조가 없는 아이콘만 업로드할 수 있습니다.";
    /** 저장 파일명 후보. 같은 code 로 확장자만 다른 아이콘이 중복 생기지 않게 한다. */
    private static final List<String> AMENITY_ICON_EXTENSIONS =
            List.of("png", "jpg", "jpeg", "svg");
    private static final Set<String> FORBIDDEN_SVG_ELEMENTS = Set.of(
            "script", "iframe", "object", "embed", "foreignobject");
    /** 속성 값에서 금지하는 URI. 공백을 제거한 소문자 값으로 비교한다. */
    private static final List<String> FORBIDDEN_SVG_URI_SCHEMES =
            List.of("javascript:", "http://", "https://", "file:");

    /** 업로드한 파일을 가리키는 웹 경로의 앞머리. 이 밖의 경로는 다루지 않는다. */
    private static final String UPLOAD_URL_PREFIX = "/uploads/";

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

    /**
     * 이미 올라와 있는 파일을 다른 업로드 폴더로 복사한다.
     *
     * <p>표지 디자인을 여행일기에 적용할 때처럼, 원본과 적용본이 같은 파일을 나눠 쓰면
     * 한쪽을 지웠을 때 다른 쪽이 깨진다. 그래서 값만 옮기지 않고 파일도 새로 만든다.
     * 원본은 건드리지 않는다.
     *
     * @param sourceUrl 업로드 폴더 안의 웹 경로 (/uploads/... 로 시작해야 한다)
     * @param subDir    복사해 둘 하위 폴더
     * @return 복사본의 웹 경로. 원본이 없거나 업로드 폴더 밖을 가리키면 null.
     */
    public String copyStoredFile(String sourceUrl, String subDir) {
        if (sourceUrl == null || !sourceUrl.startsWith(UPLOAD_URL_PREFIX)) {
            // 업로드한 파일이 아니면 복사할 것이 없다. (공용 asset 은 경로만 나눠 쓴다)
            return null;
        }

        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path source = uploadRoot.resolve(sourceUrl.substring(UPLOAD_URL_PREFIX.length()))
                .normalize();
        // '..' 같은 조각으로 업로드 폴더 밖을 가리키지 못하게 한다.
        if (!source.startsWith(uploadRoot) || !Files.isRegularFile(source)) {
            return null;
        }

        String name = source.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        String ext = dotIndex == -1 ? "" : name.substring(dotIndex);
        String savedName = UUID.randomUUID() + ext;

        try {
            Path targetDir = uploadRoot.resolve(subDir).normalize();
            if (!targetDir.startsWith(uploadRoot)) {
                return null;
            }
            Files.createDirectories(targetDir);
            Files.copy(source, targetDir.resolve(savedName));
        } catch (IOException exception) {
            throw new RuntimeException("파일 복사 실패", exception);
        }
        return UPLOAD_URL_PREFIX + subDir + "/" + savedName;
    }

    /**
     * 관리자 여행지 직접 업로드 전용 저장.
     * 클라이언트가 보낸 파일명/Content-Type 은 신뢰하지 않고,
     * 실제 bytes 를 decode 해 JPEG/PNG 인 경우에만 저장한다.
     */
    public String saveDestinationImage(MultipartFile file) {
        DestinationImageFormat format = validateDestinationImage(file);
        Path destinationDirectory = resolveDestinationDirectory(true);
        String savedName = UUID.randomUUID() + "." + format.extension;
        Path destination = destinationDirectory.resolve(savedName).normalize();
        ensureContained(destinationDirectory, destination);

        try (InputStream input = file.getInputStream()) {
            Files.copy(input, destination);
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException ignored) {
                // 원래 저장 실패를 우선 전달한다.
            }
            throw new RuntimeException("여행지 이미지 파일 저장에 실패했습니다.", exception);
        }
        return DESTINATION_URL_PREFIX + savedName;
    }

    public boolean deleteDestinationFile(String imageUrl) {
        String fileName = managedDestinationFileName(imageUrl);
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            if (Files.notExists(uploadRoot)) {
                return false;
            }
            Path realUploadRoot = uploadRoot.toRealPath();
            Path destinationDirectory = realUploadRoot.resolve(DESTINATION_DIRECTORY).normalize();
            ensureContained(realUploadRoot, destinationDirectory);
            if (Files.notExists(destinationDirectory)) {
                return false;
            }

            Path realDestinationDirectory = destinationDirectory.toRealPath();
            ensureContained(realUploadRoot, realDestinationDirectory);
            Path target = realDestinationDirectory.resolve(fileName).normalize();
            ensureContained(realDestinationDirectory, target);
            return Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new RuntimeException("여행지 이미지 파일 삭제에 실패했습니다.", exception);
        }
    }

    public boolean deleteCommentFile(String imageUrl) {
        String fileName = managedCommentFileName(imageUrl);
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            if (Files.notExists(uploadRoot)) {
                return false;
            }
            Path realUploadRoot = uploadRoot.toRealPath();
            Path commentDirectory = realUploadRoot.resolve(COMMENT_DIRECTORY).normalize();
            ensureContained(realUploadRoot, commentDirectory);
            if (Files.notExists(commentDirectory)) {
                return false;
            }

            Path realCommentDirectory = commentDirectory.toRealPath();
            ensureContained(realUploadRoot, realCommentDirectory);
            Path target = realCommentDirectory.resolve(fileName).normalize();
            ensureContained(realCommentDirectory, target);
            return Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new RuntimeException("댓글 이미지 파일 삭제에 실패했습니다.", exception);
        }
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

    private String managedDestinationFileName(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(DESTINATION_URL_PREFIX)) {
            throw new IllegalArgumentException("관리 대상이 아닌 여행지 이미지 경로입니다.");
        }
        String fileName = imageUrl.substring(DESTINATION_URL_PREFIX.length());
        if (fileName.isBlank()
                || fileName.contains("/")
                || fileName.contains("\\")
                || fileName.contains("..")) {
            throw new IllegalArgumentException("올바르지 않은 여행지 이미지 경로입니다.");
        }
        return fileName;
    }

    private DestinationImageFormat validateDestinationImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new UnsupportedImageFormatException("이미지 파일을 선택해 주세요.");
        }

        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw unsupportedDestinationImage();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw unsupportedDestinationImage();
            }

            ImageReader reader = readers.next();
            try {
                DestinationImageFormat format = DestinationImageFormat.of(reader.getFormatName());
                reader.setInput(imageInput);
                if (reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0) {
                    throw unsupportedDestinationImage();
                }
                // 실제로 decode 되는 이미지인지까지 확인한다
                reader.read(0);
                return format;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw unsupportedDestinationImage();
        }
    }

    private UnsupportedImageFormatException unsupportedDestinationImage() {
        return new UnsupportedImageFormatException(UNSUPPORTED_DESTINATION_IMAGE_MESSAGE);
    }

    private Path resolveDestinationDirectory(boolean create) {
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            if (create) {
                Files.createDirectories(uploadRoot);
            } else if (Files.notExists(uploadRoot)) {
                return null;
            }

            Path realUploadRoot = uploadRoot.toRealPath();
            Path destinationDirectory = realUploadRoot.resolve(DESTINATION_DIRECTORY).normalize();
            ensureContained(realUploadRoot, destinationDirectory);
            if (create) {
                Files.createDirectories(destinationDirectory);
            } else if (Files.notExists(destinationDirectory)) {
                return null;
            }

            Path realDestinationDirectory = destinationDirectory.toRealPath();
            ensureContained(realUploadRoot, realDestinationDirectory);
            return realDestinationDirectory;
        } catch (IOException exception) {
            throw new RuntimeException("여행지 이미지 저장 경로를 준비할 수 없습니다.", exception);
        }
    }

    private String managedCommentFileName(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(COMMENT_URL_PREFIX)) {
            throw new IllegalArgumentException("관리 대상이 아닌 댓글 이미지 경로입니다.");
        }
        String fileName = imageUrl.substring(COMMENT_URL_PREFIX.length());
        if (fileName.isBlank()
                || fileName.contains("/")
                || fileName.contains("\\")
                || fileName.contains("..")) {
            throw new IllegalArgumentException("올바르지 않은 댓글 이미지 경로입니다.");
        }
        return fileName;
    }

    /**
     * 편의시설 아이콘 파일만 검증한다. 저장은 하지 않으므로 DB INSERT 전에 먼저 부를 수 있다.
     * 클라이언트가 보낸 확장자/Content-Type 을 믿지 않고 실제 bytes 까지 확인한다.
     */
    public void validateAmenityIcon(MultipartFile file) {
        verifyAmenityIcon(file);
    }

    /**
     * 업로드된 아이콘을 검증하고 저장에 쓸 확장자를 돌려준다. 포맷 변환은 하지 않는다.
     * 확장자, Content-Type, 실제 내용이 모두 같은 포맷을 가리킬 때만 통과한다.
     *
     * @return 저장 확장자 ("png" / "jpg" / "jpeg" / "svg")
     */
    private String verifyAmenityIcon(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("아이콘 이미지를 선택해 주세요.");
        }
        if (file.getSize() > AMENITY_ICON_MAX_SIZE) {
            throw new IllegalArgumentException("아이콘 이미지는 512KB 이하만 업로드할 수 있습니다.");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()
                || originalName.contains("/") || originalName.contains("\\")
                || originalName.contains("..")) {
            throw new IllegalArgumentException("올바르지 않은 아이콘 파일명입니다.");
        }

        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == originalName.length() - 1) {
            throw unsupportedAmenityIcon();
        }
        String requestedExtension = originalName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        String contentType = file.getContentType();

        // SVG 는 이미지가 아니라 XML 이므로 ImageIO 가 아닌 별도 경로로 검사한다.
        if ("svg".equals(requestedExtension)) {
            if (!"image/svg+xml".equalsIgnoreCase(contentType)) {
                throw unsupportedAmenityIcon();
            }
            validateSvgIcon(file);
            return "svg";
        }

        AmenityIconFormat detected = detectAmenityIconFormat(file);
        // 확장자만 바꾼 위장 파일을 막기 위해 세 값이 모두 같은 포맷을 가리켜야 한다.
        boolean matches = switch (detected) {
            case PNG -> "png".equals(requestedExtension)
                    && "image/png".equalsIgnoreCase(contentType);
            case JPEG -> ("jpg".equals(requestedExtension) || "jpeg".equals(requestedExtension))
                    && "image/jpeg".equalsIgnoreCase(contentType);
        };
        if (!matches) {
            throw unsupportedAmenityIcon();
        }
        decodeAmenityIcon(file, detected);
        // JPG 와 JPEG 는 올린 그대로의 확장자를 유지한다.
        return requestedExtension;
    }

    private AmenityIconFormat detectAmenityIconFormat(MultipartFile file) {
        byte[] header = new byte[8];
        int length;
        try (InputStream input = file.getInputStream()) {
            length = input.read(header);
        } catch (IOException exception) {
            throw unsupportedAmenityIcon();
        }

        if (length >= 8
                && unsigned(header[0]) == 0x89
                && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                && unsigned(header[4]) == 0x0d && unsigned(header[5]) == 0x0a
                && unsigned(header[6]) == 0x1a && unsigned(header[7]) == 0x0a) {
            return AmenityIconFormat.PNG;
        }
        if (length >= 3
                && unsigned(header[0]) == 0xff
                && unsigned(header[1]) == 0xd8
                && unsigned(header[2]) == 0xff) {
            return AmenityIconFormat.JPEG;
        }
        throw unsupportedAmenityIcon();
    }

    private UnsupportedImageFormatException unsupportedAmenityIcon() {
        return new UnsupportedImageFormatException(UNSUPPORTED_AMENITY_ICON_MESSAGE);
    }

    private UnsupportedImageFormatException unsafeSvgIcon() {
        return new UnsupportedImageFormatException(UNSAFE_AMENITY_SVG_MESSAGE);
    }

    /**
     * SVG 아이콘 검사. 파일로 저장해 &lt;img&gt; 로만 렌더링하지만, 업로드 자체를 보수적으로 막는다.
     * DTD/외부 엔티티(XXE)를 끄고 파싱한 뒤 위험한 요소·속성·URI 를 전부 거부한다.
     */
    private void validateSvgIcon(MultipartFile file) {
        Document document;
        try (InputStream input = file.getInputStream()) {
            document = secureSvgDocumentBuilder().parse(input);
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            // DOCTYPE 선언도 여기서 걸린다(disallow-doctype-decl).
            throw unsafeSvgIcon();
        }

        Element root = document.getDocumentElement();
        if (root == null || !"svg".equalsIgnoreCase(localName(root))) {
            throw unsupportedAmenityIcon();
        }
        checkSvgElement(root);
    }

    private DocumentBuilder secureSvgDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> {
            throw new SAXException("external entity is not allowed");
        });
        return builder;
    }

    private void checkSvgElement(Element element) {
        if (FORBIDDEN_SVG_ELEMENTS.contains(localName(element).toLowerCase(Locale.ROOT))) {
            throw unsafeSvgIcon();
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String name = attribute.getNodeName();
            // 네임스페이스 선언은 정상적으로 http://www.w3.org/... 를 가리키므로 제외한다.
            if ("xmlns".equals(name) || name.startsWith("xmlns:")
                    || XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) {
                continue;
            }
            if (localName(attribute).toLowerCase(Locale.ROOT).startsWith("on")) {
                throw unsafeSvgIcon();
            }
            String value = attribute.getNodeValue() == null
                    ? "" : attribute.getNodeValue().toLowerCase(Locale.ROOT).replace(" ", "");
            for (String forbidden : FORBIDDEN_SVG_URI_SCHEMES) {
                if (value.contains(forbidden)) {
                    throw unsafeSvgIcon();
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                checkSvgElement(childElement);
            }
        }
    }

    private String localName(Node node) {
        return node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
    }

    /**
     * 신규 편의시설 아이콘 저장.
     * 업로드한 포맷을 변환하지 않고 원본 bytes 를 그대로 저장하며,
     * 파일명만 {code 소문자}.{실제확장자} 로 고정한다.
     *
     * @return 웹 URL (예: /uploads/icons/amenities/free_wifi.svg)
     */
    public String saveAmenityIcon(String code, MultipartFile file) {
        String extension = verifyAmenityIcon(file);
        String savedName = amenityIconFileName(code, extension);
        Path iconDirectory = resolveAmenityIconDirectory(true);
        Path destination = iconDirectory.resolve(savedName).normalize();
        ensureContained(iconDirectory, destination);
        // 확장자가 달라도 같은 code 의 아이콘이 이미 있으면 신규 등록으로 보지 않는다.
        ensureNoExistingAmenityIcon(iconDirectory, code);

        boolean created = false;
        try (InputStream input = file.getInputStream();
             OutputStream output = Files.newOutputStream(
                     destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            created = true;
            input.transferTo(output);
        } catch (FileAlreadyExistsException exception) {
            throw amenityIconAlreadyExists(exception);
        } catch (IOException exception) {
            if (created) {
                deleteAmenityIconPathQuietly(destination);
            }
            throw new IllegalStateException("편의시설 아이콘 파일 저장에 실패했습니다.", exception);
        }
        return AMENITY_ICON_URL_PREFIX + savedName;
    }

    /**
     * 기존 편의시설 아이콘 교체 준비.
     * 임시 파일에 먼저 쓰고, 같은 이름의 기존 파일은 백업해 둔 뒤 원자적으로 바꿔치기한다.
     * 호출자는 트랜잭션 결과에 따라 {@link #commitAmenityIconReplacement}
     * 또는 {@link #rollbackAmenityIconReplacement} 를 반드시 불러야 한다.
     */
    public AmenityIconReplacement replaceAmenityIcon(String code, MultipartFile file) {
        String extension = verifyAmenityIcon(file);
        String savedName = amenityIconFileName(code, extension);
        Path iconDirectory = resolveAmenityIconDirectory(true);
        Path destination = iconDirectory.resolve(savedName).normalize();
        ensureContained(iconDirectory, destination);

        // 1) 새 내용을 임시 파일에 먼저 기록한다. 여기서 실패해도 기존 아이콘은 그대로다.
        Path temporary = iconDirectory.resolve(savedName + ".tmp-" + UUID.randomUUID()).normalize();
        ensureContained(iconDirectory, temporary);
        try (InputStream input = file.getInputStream();
             OutputStream output = Files.newOutputStream(
                     temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            input.transferTo(output);
        } catch (IOException exception) {
            deleteAmenityIconPathQuietly(temporary);
            throw new IllegalStateException("편의시설 아이콘 파일 저장에 실패했습니다.", exception);
        }

        // 2) 같은 이름의 기존 파일은 지우지 않고 백업해 둔다(롤백 시 되돌린다).
        Path backup = null;
        try {
            if (Files.exists(destination)) {
                backup = iconDirectory.resolve(savedName + ".bak-" + UUID.randomUUID()).normalize();
                ensureContained(iconDirectory, backup);
                Files.move(destination, backup, StandardCopyOption.ATOMIC_MOVE);
            }
            // 3) 임시 파일을 최종 이름으로 옮긴다.
            moveIntoPlace(temporary, destination);
        } catch (IOException exception) {
            deleteAmenityIconPathQuietly(temporary);
            restoreAmenityIconBackup(backup, destination);
            throw new IllegalStateException("편의시설 아이콘 파일 교체에 실패했습니다.", exception);
        }
        return new AmenityIconReplacement(
                AMENITY_ICON_URL_PREFIX + savedName, code, extension, destination, backup);
    }

    /** 교체 확정. 백업과 이전 확장자로 남아 있던 아이콘을 정리한다. */
    public void commitAmenityIconReplacement(AmenityIconReplacement replacement) {
        if (replacement == null) {
            return;
        }
        deleteAmenityIconPathQuietly(replacement.backup());

        Path iconDirectory = resolveAmenityIconDirectory(false);
        if (iconDirectory == null) {
            return;
        }
        for (String extension : AMENITY_ICON_EXTENSIONS) {
            if (extension.equals(replacement.extension())) {
                continue;
            }
            Path stale = iconDirectory
                    .resolve(amenityIconFileName(replacement.code(), extension)).normalize();
            ensureContained(iconDirectory, stale);
            deleteAmenityIconPathQuietly(stale);
        }
    }

    /** 교체 취소. 새로 쓴 파일을 지우고 백업해 둔 기존 아이콘을 되돌린다. */
    public void rollbackAmenityIconReplacement(AmenityIconReplacement replacement) {
        if (replacement == null) {
            return;
        }
        deleteAmenityIconPathQuietly(replacement.target());
        restoreAmenityIconBackup(replacement.backup(), replacement.target());
    }

    private void moveIntoPlace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void restoreAmenityIconBackup(Path backup, Path destination) {
        if (backup == null) {
            return;
        }
        try {
            if (Files.exists(backup)) {
                moveIntoPlace(backup, destination);
            }
        } catch (IOException ignored) {
            // 되돌리기까지 실패하면 백업 파일은 남겨 둔다. 원본 실패를 우선 전달한다.
        }
    }

    /**
     * 아이콘 교체 진행 상태. 트랜잭션 결과에 따라 commit/rollback 에 그대로 넘긴다.
     *
     * @param iconUrl   새 아이콘 웹 경로
     * @param backup    같은 이름의 기존 파일 백업 (없으면 null)
     */
    public record AmenityIconReplacement(
            String iconUrl, String code, String extension, Path target, Path backup) {
    }

    /** 롤백 정리용. 이번 등록에서 만든 아이콘만 지운다(확장자는 포맷마다 다르다). */
    public boolean deleteAmenityIcon(String code) {
        Path iconDirectory = resolveAmenityIconDirectory(false);
        if (iconDirectory == null) {
            return false;
        }

        boolean deleted = false;
        for (String extension : AMENITY_ICON_EXTENSIONS) {
            Path target = iconDirectory.resolve(amenityIconFileName(code, extension)).normalize();
            ensureContained(iconDirectory, target);
            try {
                deleted |= Files.deleteIfExists(target);
            } catch (IOException exception) {
                throw new IllegalStateException("편의시설 아이콘 파일 삭제에 실패했습니다.", exception);
            }
        }
        return deleted;
    }

    /** DB 에 code 가 없어도 파일만 남아 있을 수 있으므로 확장자 전체를 확인한다. */
    private void ensureNoExistingAmenityIcon(Path iconDirectory, String code) {
        for (String extension : AMENITY_ICON_EXTENSIONS) {
            Path existing = iconDirectory.resolve(amenityIconFileName(code, extension)).normalize();
            ensureContained(iconDirectory, existing);
            if (Files.exists(existing)) {
                throw amenityIconAlreadyExists(null);
            }
        }
    }

    private IllegalStateException amenityIconAlreadyExists(Throwable cause) {
        return new IllegalStateException(
                "같은 코드의 아이콘 파일이 이미 있습니다. 다른 코드를 사용해 주세요.", cause);
    }

    /** code -> 파일명. 경로 문자가 섞일 수 없도록 형식을 먼저 확인한다. */
    private String amenityIconFileName(String code, String extension) {
        if (code == null || !AMENITY_CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("올바르지 않은 편의시설 코드입니다.");
        }
        return code.toLowerCase(Locale.ROOT) + "." + extension;
    }

    /** 실제로 decode 되는 이미지인지 확인하고, 저장에 쓸 이미지를 그대로 돌려준다. */
    private BufferedImage decodeAmenityIcon(MultipartFile file, AmenityIconFormat expected) {
        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw unsupportedAmenityIcon();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw unsupportedAmenityIcon();
            }

            ImageReader reader = readers.next();
            try {
                if (expected != AmenityIconFormat.of(reader.getFormatName())) {
                    throw unsupportedAmenityIcon();
                }
                reader.setInput(imageInput);
                if (reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0) {
                    throw unsupportedAmenityIcon();
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw unsupportedAmenityIcon();
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw unsupportedAmenityIcon();
        }
    }

    private Path resolveAmenityIconDirectory(boolean create) {
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            if (create) {
                Files.createDirectories(uploadRoot);
            } else if (Files.notExists(uploadRoot)) {
                return null;
            }

            Path realUploadRoot = uploadRoot.toRealPath();
            Path iconDirectory = realUploadRoot.resolve(AMENITY_ICON_DIRECTORY).normalize();
            ensureContained(realUploadRoot, iconDirectory);
            if (create) {
                Files.createDirectories(iconDirectory);
            } else if (Files.notExists(iconDirectory)) {
                return null;
            }

            Path realIconDirectory = iconDirectory.toRealPath();
            ensureContained(realUploadRoot, realIconDirectory);
            return realIconDirectory;
        } catch (IOException exception) {
            throw new IllegalStateException("편의시설 아이콘 저장 경로를 준비할 수 없습니다.", exception);
        }
    }

    private void deleteAmenityIconPathQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 원래 저장 실패를 우선 전달한다.
        }
    }

    private void ensureContained(Path root, Path target) {
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("허용된 업로드 경로를 벗어날 수 없습니다.");
        }
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    /** 여행지 직접 업로드 허용 포맷 (KTO 사진과 동일하게 JPEG/PNG 만 허용) */
    private enum DestinationImageFormat {
        JPEG("jpg"),
        PNG("png");

        private final String extension;

        DestinationImageFormat(String extension) {
            this.extension = extension;
        }

        private static DestinationImageFormat of(String formatName) {
            if ("JPEG".equalsIgnoreCase(formatName) || "JPG".equalsIgnoreCase(formatName)) {
                return JPEG;
            }
            if ("PNG".equalsIgnoreCase(formatName)) {
                return PNG;
            }
            throw new UnsupportedImageFormatException(UNSUPPORTED_DESTINATION_IMAGE_MESSAGE);
        }
    }

    /** 편의시설 아이콘 업로드 허용 포맷. 저장은 항상 PNG 로 한다. */
    private enum AmenityIconFormat {
        PNG,
        JPEG;

        private static AmenityIconFormat of(String formatName) {
            if ("PNG".equalsIgnoreCase(formatName)) {
                return PNG;
            }
            if ("JPEG".equalsIgnoreCase(formatName) || "JPG".equalsIgnoreCase(formatName)) {
                return JPEG;
            }
            throw new UnsupportedImageFormatException(UNSUPPORTED_AMENITY_ICON_MESSAGE);
        }
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
