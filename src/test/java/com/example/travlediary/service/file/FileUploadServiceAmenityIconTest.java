package com.example.travlediary.service.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 편의시설 아이콘은 상세 페이지가 code 로 경로를 만들기 때문에
 * 파일명이 code 로 고정되고, 기존 아이콘을 덮어쓰지 않는다.
 */
class FileUploadServiceAmenityIconTest {

    @TempDir
    Path uploadRoot;

    @Test
    void storesRealPngUnderTheAmenityIconDirectoryNamedAfterTheCode() throws Exception {
        String url = service().saveAmenityIcon("FREE_WIFI", pngFile("icon.png"));

        assertThat(url).isEqualTo("/uploads/icons/amenities/free_wifi.png");
        Path stored = uploadRoot.resolve("icons/amenities/free_wifi.png");
        assertThat(stored).isRegularFile();
        assertThat(detectedFormatOf(stored)).isEqualTo("png");
        assertThat(storedFiles()).containsExactly(stored);
    }

    @Test
    void keepsAnUploadedJpegAsJpegWithoutConverting() throws Exception {
        byte[] original = imageBytes("jpg");
        String url = service().saveAmenityIcon("PARKING", file("photo.jpg", "image/jpeg", original));

        assertThat(url).isEqualTo("/uploads/icons/amenities/parking.jpg");
        Path stored = uploadRoot.resolve("icons/amenities/parking.jpg");
        assertThat(stored).isRegularFile();
        // 원본 bytes 그대로 저장되고 PNG 로 변환되지 않는다
        assertThat(Files.readAllBytes(stored)).isEqualTo(original);
        assertThat(detectedFormatOf(stored)).isEqualTo("jpeg");
        assertThat(uploadRoot.resolve("icons/amenities/parking.png")).doesNotExist();
    }

    @Test
    void keepsTheJpegExtensionSpellingAsUploaded() throws Exception {
        String url = service().saveAmenityIcon(
                "PARKING", file("photo.jpeg", "image/jpeg", imageBytes("jpg")));

        assertThat(url).isEqualTo("/uploads/icons/amenities/parking.jpeg");
        assertThat(uploadRoot.resolve("icons/amenities/parking.jpeg")).isRegularFile();
    }

    @Test
    void storesASafeSvgAsIs() throws Exception {
        byte[] original = svgBytes("<path d=\"M2 2 L20 20\" fill=\"none\" stroke=\"#333\"/>");
        String url = service().saveAmenityIcon("FREE_WIFI", svgFile(original));

        assertThat(url).isEqualTo("/uploads/icons/amenities/free_wifi.svg");
        Path stored = uploadRoot.resolve("icons/amenities/free_wifi.svg");
        assertThat(stored).isRegularFile();
        assertThat(Files.readAllBytes(stored)).isEqualTo(original);
    }

    @Test
    void allowsOrdinaryIconShapesInsideSvg() throws Exception {
        service().saveAmenityIcon("PARKING", svgFile(svgBytes("""
                <title>주차</title><desc>parking</desc>
                <defs><linearGradient id="g"><stop offset="0" stop-color="#fff"/></linearGradient></defs>
                <g><rect x="1" y="1" width="4" height="4"/><circle cx="8" cy="8" r="3"/>
                <line x1="0" y1="0" x2="9" y2="9"/><polygon points="1,1 4,1 4,4"/>
                <polyline points="0,0 2,2"/><path d="M0 0 L5 5"/></g>
                """)));

        assertThat(uploadRoot.resolve("icons/amenities/parking.svg")).isRegularFile();
    }

    @Test
    void refusesFilesWhoseExtensionOrContentTypeDoesNotMatchTheRealFormat() throws Exception {
        // 이름과 Content-Type 만 png 로 바꾼 실제 JPEG
        assertThatThrownBy(() -> service().saveAmenityIcon(
                "PARKING", file("icon.png", "image/png", imageBytes("jpg"))))
                .isInstanceOf(UnsupportedImageFormatException.class)
                .hasMessageContaining("PNG, JPG 또는 SVG");
        // 이름만 jpg 로 바꾼 실제 PNG
        assertThatThrownBy(() -> service().saveAmenityIcon(
                "PARKING", file("icon.jpg", "image/jpeg", imageBytes("png"))))
                .isInstanceOf(UnsupportedImageFormatException.class);
        // 실제 PNG 를 .svg 로 위장
        assertThatThrownBy(() -> service().saveAmenityIcon(
                "PARKING", file("icon.svg", "image/svg+xml", imageBytes("png"))))
                .isInstanceOf(UnsupportedImageFormatException.class);
        // SVG 내용을 .png 로 위장
        assertThatThrownBy(() -> service().saveAmenityIcon(
                "PARKING", file("icon.png", "image/png", svgBytes("<path d=\"M0 0\"/>"))))
                .isInstanceOf(UnsupportedImageFormatException.class);
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void refusesImageFormatsOutsidePngJpegAndSvg() throws Exception {
        assertThatThrownBy(() -> service().saveAmenityIcon(
                "PARKING", file("icon.gif", "image/gif", imageBytes("gif"))))
                .isInstanceOf(UnsupportedImageFormatException.class);
        assertThatThrownBy(() -> service().saveAmenityIcon(
                "PARKING", file("icon.webp", "image/webp", imageBytes("png"))))
                .isInstanceOf(UnsupportedImageFormatException.class);
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void refusesSvgCarryingScriptsEventHandlersOrExternalReferences() throws Exception {
        String[] unsafeBodies = {
                "<script>alert(1)</script>",
                "<rect width=\"4\" height=\"4\" onload=\"alert(1)\"/>",
                "<rect width=\"4\" height=\"4\" onclick=\"alert(1)\"/>",
                "<rect width=\"4\" height=\"4\" onerror=\"alert(1)\"/>",
                "<rect width=\"4\" height=\"4\" onmouseover=\"alert(1)\"/>",
                "<a href=\"javascript:alert(1)\"><rect width=\"4\" height=\"4\"/></a>",
                "<a href=\"JAVASCRIPT: alert(1)\"><rect width=\"4\" height=\"4\"/></a>",
                "<image href=\"http://evil.example/x.png\"/>",
                "<image href=\"https://evil.example/x.png\"/>",
                "<image href=\"file:///etc/passwd\"/>",
                "<iframe/>",
                "<object data=\"x\"/>",
                "<embed/>",
                "<foreignObject><div/></foreignObject>"
        };

        for (String body : unsafeBodies) {
            assertThatThrownBy(() -> service().saveAmenityIcon("PARKING", svgFile(svgBytes(body))))
                    .as("svg body=%s", body)
                    .isInstanceOf(UnsupportedImageFormatException.class);
        }
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void refusesSvgWithADoctypeOrExternalEntity() throws Exception {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE svg [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <svg xmlns="http://www.w3.org/2000/svg"><desc>&xxe;</desc></svg>
                """;
        assertThatThrownBy(() -> service().saveAmenityIcon(
                "PARKING", svgFile(xxe.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(UnsupportedImageFormatException.class);

        // XML 로 파싱조차 되지 않는 파일
        assertThatThrownBy(() -> service().saveAmenityIcon(
                "PARKING", svgFile("not xml at all".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(UnsupportedImageFormatException.class);
        // root 가 svg 가 아닌 XML
        assertThatThrownBy(() -> service().saveAmenityIcon(
                "PARKING", svgFile("<html><body/></html>".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(UnsupportedImageFormatException.class);
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void refusesATextFileRenamedAsPng() throws Exception {
        assertThatThrownBy(() -> service().saveAmenityIcon(
                "PARKING", file("icon.png", "image/png", "hello".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(UnsupportedImageFormatException.class);
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void refusesAContentTypeOtherThanImagePng() throws Exception {
        assertThatThrownBy(() -> service().saveAmenityIcon(
                "PARKING", file("icon.png", "application/octet-stream", imageBytes("png"))))
                .isInstanceOf(UnsupportedImageFormatException.class);
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void refusesIconsLargerThan512Kilobytes() throws Exception {
        byte[] oversized = new byte[512 * 1024 + 1];
        assertThatThrownBy(() -> service().saveAmenityIcon(
                "PARKING", file("icon.png", "image/png", oversized)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("512KB");
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void refusesNullAndEmptyUploads() throws Exception {
        assertThatThrownBy(() -> service().saveAmenityIcon("PARKING", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().saveAmenityIcon(
                "PARKING", file("icon.png", "image/png", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void neverOverwritesAnIconThatAlreadyExists() throws Exception {
        service().saveAmenityIcon("FREE_WIFI", pngFile("icon.png"));
        Path stored = uploadRoot.resolve("icons/amenities/free_wifi.png");
        byte[] original = Files.readAllBytes(stored);

        MultipartFile replacement = file("other.png", "image/png", imageBytes("png", 16));
        assertThat(replacement.getBytes()).isNotEqualTo(original);
        assertThatThrownBy(() -> service().saveAmenityIcon("FREE_WIFI", replacement))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 있습니다");
        // 기존 운영 아이콘이 손상되지 않는다
        assertThat(Files.readAllBytes(stored)).isEqualTo(original);
        assertThat(storedFiles()).containsExactly(stored);
    }

    @Test
    void refusesANewIconWhenTheSameCodeAlreadyHasADifferentExtension() throws Exception {
        service().saveAmenityIcon("FREE_WIFI", pngFile("icon.png"));

        // free_wifi.png 가 이미 있으면 free_wifi.svg / .jpg 신규 저장도 막는다
        assertThatThrownBy(() -> service().saveAmenityIcon(
                "FREE_WIFI", svgFile(svgBytes("<path d=\"M0 0\"/>"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 있습니다");
        assertThatThrownBy(() -> service().saveAmenityIcon(
                "FREE_WIFI", file("icon.jpg", "image/jpeg", imageBytes("jpg"))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(storedFiles())
                .containsExactly(uploadRoot.resolve("icons/amenities/free_wifi.png"));
    }

    @Test
    void rejectsCodesThatCouldEscapeTheIconDirectory() throws Exception {
        for (String code : new String[]{
                "../evil", "icons/../../evil", "free wifi", "free-wifi",
                "free_wifi", "A", null, "", "AB/CD", "AB\\CD"}) {
            assertThatThrownBy(() -> service().saveAmenityIcon(code, pngFile("icon.png")))
                    .as("code=%s", code)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(storedFiles()).isEmpty();
        assertThat(uploadRoot.getParent().resolve("evil.png")).doesNotExist();
    }

    @Test
    void deleteRemovesOnlyTheIconNamedAfterTheCodeWhateverItsExtension() throws Exception {
        service().saveAmenityIcon("FREE_WIFI", svgFile(svgBytes("<path d=\"M0 0\"/>")));
        service().saveAmenityIcon("PARKING", pngFile("icon.png"));

        // 롤백 정리는 확장자를 모르므로 code 기준으로 지운다
        assertThat(service().deleteAmenityIcon("FREE_WIFI")).isTrue();
        assertThat(uploadRoot.resolve("icons/amenities/free_wifi.svg")).doesNotExist();
        assertThat(uploadRoot.resolve("icons/amenities/parking.png")).isRegularFile();
        // 없는 파일 삭제는 조용히 false
        assertThat(service().deleteAmenityIcon("FREE_WIFI")).isFalse();
    }

    @Test
    void validateDoesNotWriteAnyFile() throws Exception {
        service().validateAmenityIcon(pngFile("icon.png"));

        assertThat(uploadRoot.resolve("icons/amenities")).doesNotExist();
    }

    private FileUploadService service() {
        return new FileUploadService(uploadRoot.toString());
    }

    private MultipartFile pngFile(String originalName) throws IOException {
        return file(originalName, "image/png", imageBytes("png"));
    }

    private MultipartFile svgFile(byte[] content) {
        return file("icon.svg", "image/svg+xml", content);
    }

    private byte[] svgBytes(String body) {
        return ("<svg xmlns=\"http://www.w3.org/2000/svg\" "
                + "xmlns:xlink=\"http://www.w3.org/1999/xlink\" viewBox=\"0 0 24 24\">"
                + body + "</svg>").getBytes(StandardCharsets.UTF_8);
    }

    private MultipartFile file(String originalName, String contentType, byte[] content) {
        return new MockMultipartFile("icon", originalName, contentType, content);
    }

    private byte[] imageBytes(String format) throws IOException {
        return imageBytes(format, 4);
    }

    private byte[] imageBytes(String format, int size) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, bytes)).as("%s writer", format).isTrue();
        return bytes.toByteArray();
    }

    /** 저장된 파일을 다시 읽어 실제 포맷을 확인한다. */
    private String detectedFormatOf(Path stored) throws IOException {
        try (javax.imageio.stream.ImageInputStream input =
                     ImageIO.createImageInputStream(Files.newInputStream(stored))) {
            assertThat(input).isNotNull();
            var readers = ImageIO.getImageReaders(input);
            assertThat(readers.hasNext()).as("readable image").isTrue();
            var reader = readers.next();
            try {
                return reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
            } finally {
                reader.dispose();
            }
        }
    }

    private List<Path> storedFiles() throws IOException {
        Path icons = uploadRoot.resolve("icons/amenities");
        if (Files.notExists(icons)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(icons)) {
            return files.toList();
        }
    }
}
