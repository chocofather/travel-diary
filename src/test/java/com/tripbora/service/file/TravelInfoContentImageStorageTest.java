package com.tripbora.service.file;

import com.tripbora.service.travelinfo.structured.StructuredImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 구조화 콘텐츠(STRUCTURED) 본문 이미지 저장·삭제.
 *
 * <p>고정하는 계약
 * <ul>
 *   <li>실제 JPEG/PNG/WEBP 만 받고, 저장 이름은 구조화 model 이 받는 소문자 UUID + 판별한 확장자다.</li>
 *   <li>JPEG/PNG 는 긴 변 2000px 를 넘으면 비율을 지켜 줄이고, 작으면 원본 그대로(키우지 않음)다.</li>
 *   <li>WEBP 는 줄일 수 없어 2000px 를 넘으면 받지 않는다.</li>
 *   <li>응답 크기는 저장한 그림의 실제(화면 기준) 크기다.</li>
 *   <li>삭제는 전용 폴더 안의 서버가 만든 이름만 지운다.</li>
 * </ul>
 */
class TravelInfoContentImageStorageTest {

    private static final int MAX_EDGE = FileUploadService.TRAVEL_INFO_CONTENT_IMAGE_MAX_EDGE;
    private static final String NAME_PATTERN =
            "^/uploads/travel-info/content/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                    + "\\.(?:jpg|png|webp)$";
    /** 1x1 WEBP (VP8). 일반 이미지 업로드 테스트와 같은 표본이다. */
    private static final byte[] TINY_WEBP = Base64.getDecoder().decode(
            "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v89");

    @TempDir
    Path uploadRoot;

    @Test
    void storagePathMatchesWhatTheStructuredModelAccepts() {
        assertThat(FileUploadService.TRAVEL_INFO_CONTENT_IMAGE_URL_PREFIX)
                .isEqualTo(StructuredImage.URL_PREFIX);
        assertThat(MAX_EDGE).isEqualTo(2000);
    }

    // ---- 저장 ------------------------------------------------------------------------------

    @Test
    void jpegPngAndWebpAreStoredWithUuidNamesAndTheirRealSize() throws IOException {
        FileUploadService service = service();

        FileUploadService.StoredContentImage jpeg = service.saveTravelInfoContentImage(
                file("a.JPG", "image/jpeg", raster("jpg", 800, 600)));
        FileUploadService.StoredContentImage png = service.saveTravelInfoContentImage(
                file("b.png", "image/png", raster("png", 640, 960)));
        FileUploadService.StoredContentImage webp = service.saveTravelInfoContentImage(
                file("c.webp", "image/webp", TINY_WEBP));

        assertThat(jpeg.url()).matches(NAME_PATTERN).endsWith(".jpg");
        assertThat(png.url()).matches(NAME_PATTERN).endsWith(".png");
        assertThat(webp.url()).matches(NAME_PATTERN).endsWith(".webp");
        assertThat(List.of(jpeg.width(), jpeg.height())).containsExactly(800, 600);
        assertThat(List.of(png.width(), png.height())).containsExactly(640, 960);
        assertThat(List.of(webp.width(), webp.height())).containsExactly(1, 1);
        assertThat(Files.readAllBytes(stored(webp.url()))).isEqualTo(TINY_WEBP);
    }

    @Test
    void largeJpegAndPngAreShrunkToTheLongEdgeKeepingTheirRatio() throws IOException {
        FileUploadService service = service();

        FileUploadService.StoredContentImage jpeg = service.saveTravelInfoContentImage(
                file("wide.jpg", "image/jpeg", raster("jpg", 3000, 2000)));
        FileUploadService.StoredContentImage png = service.saveTravelInfoContentImage(
                file("tall.png", "image/png", raster("png", 1500, 4500)));

        assertThat(List.of(jpeg.width(), jpeg.height())).containsExactly(2000, 1333);
        assertThat(List.of(png.width(), png.height())).containsExactly(667, 2000);
        // 응답 크기는 실제로 저장한 그림의 크기다.
        BufferedImage storedJpeg = ImageIO.read(stored(jpeg.url()).toFile());
        BufferedImage storedPng = ImageIO.read(stored(png.url()).toFile());
        assertThat(List.of(storedJpeg.getWidth(), storedJpeg.getHeight())).containsExactly(2000, 1333);
        assertThat(List.of(storedPng.getWidth(), storedPng.getHeight())).containsExactly(667, 2000);
    }

    @Test
    void imagesWithinTheLimitAreNotRecompressedOrEnlarged() throws IOException {
        byte[] exactLimit = raster("jpg", MAX_EDGE, 1000);
        byte[] small = raster("png", 120, 90);

        FileUploadService.StoredContentImage atLimit = service().saveTravelInfoContentImage(
                file("limit.jpg", "image/jpeg", exactLimit));
        FileUploadService.StoredContentImage tiny = service().saveTravelInfoContentImage(
                file("small.png", "image/png", small));

        assertThat(Files.readAllBytes(stored(atLimit.url()))).isEqualTo(exactLimit);
        assertThat(Files.readAllBytes(stored(tiny.url()))).isEqualTo(small);
        assertThat(List.of(tiny.width(), tiny.height())).containsExactly(120, 90);
    }

    @Test
    void sizeFollowsExifRotationLikeTheBrowserShowsIt() throws IOException {
        // 누운 픽셀 1200x600 + "돌려서 보라"(6). 줄이지 않으므로 표시가 남고, 화면에는 600x1200 으로 보인다.
        FileUploadService.StoredContentImage small = service().saveTravelInfoContentImage(
                file("portrait.jpg", "image/jpeg", jpegWithOrientation(1200, 600, 6)));
        // 줄이면 픽셀을 바로 세워 굽는다. 3000x1500 → 바로 선 1500x3000 → 1000x2000
        FileUploadService.StoredContentImage large = service().saveTravelInfoContentImage(
                file("portrait-large.jpg", "image/jpeg", jpegWithOrientation(3000, 1500, 6)));

        assertThat(List.of(small.width(), small.height())).containsExactly(600, 1200);
        assertThat(List.of(large.width(), large.height())).containsExactly(1000, 2000);
        BufferedImage storedLarge = ImageIO.read(stored(large.url()).toFile());
        assertThat(List.of(storedLarge.getWidth(), storedLarge.getHeight())).containsExactly(1000, 2000);
    }

    @Test
    void webpOverTheLimitIsRejectedBecauseItCannotBeShrunk() throws IOException {
        FileUploadService service = service();

        assertRejected(service, file("big.webp", "image/webp", vp8lWebp(2001, 800)),
                FileUploadService.OVERSIZED_WEBP_CONTENT_IMAGE_MESSAGE);
        FileUploadService.StoredContentImage atLimit = service.saveTravelInfoContentImage(
                file("ok.webp", "image/webp", vp8lWebp(2000, 1500)));
        assertThat(List.of(atLimit.width(), atLimit.height())).containsExactly(2000, 1500);
    }

    @Test
    void rejectsOversizedFakeAndUnsupportedImagesWithoutLeavingFiles() throws IOException {
        FileUploadService service = service();
        byte[] png = raster("png", 10, 10);
        byte[] oversized = new byte[(int) (10L * 1024 * 1024) + 1];
        System.arraycopy(png, 0, oversized, 0, png.length);

        assertRejected(service, file("big.png", "image/png", oversized), FileUploadService.OVERSIZED_IMAGE_MESSAGE);
        assertRejected(service, file("a.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)),
                FileUploadService.UNSUPPORTED_IMAGE_MESSAGE);
        assertRejected(service, file("a.gif", "image/gif", raster("gif", 10, 10)),
                FileUploadService.UNSUPPORTED_IMAGE_MESSAGE);
        assertRejected(service, file("a.bmp", "image/bmp", raster("bmp", 10, 10)),
                FileUploadService.UNSUPPORTED_IMAGE_MESSAGE);
        // 이름·MIME 만 이미지인 파일, 머리말만 JPEG 인 깨진 파일
        assertRejected(service, file("fake.jpg", "image/jpeg", "not an image".getBytes(StandardCharsets.UTF_8)),
                FileUploadService.UNSUPPORTED_IMAGE_MESSAGE);
        assertRejected(service, file("broken.jpg", "image/jpeg",
                        new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00, 0x01, 0x02}),
                FileUploadService.UNSUPPORTED_IMAGE_MESSAGE);
        assertRejected(service, file("empty.png", "image/png", new byte[0]), FileUploadService.EMPTY_IMAGE_MESSAGE);
        assertThatThrownBy(() -> service.saveTravelInfoContentImage(null))
                .hasMessage(FileUploadService.EMPTY_IMAGE_MESSAGE);

        Path directory = uploadRoot.resolve("travel-info/content");
        assertThat(Files.notExists(directory) || isEmpty(directory)).isTrue();
    }

    // ---- 삭제 ------------------------------------------------------------------------------

    @Test
    void deletesOnlyItsOwnManagedFile() throws IOException {
        FileUploadService service = service();
        FileUploadService.StoredContentImage image = service.saveTravelInfoContentImage(
                file("a.png", "image/png", raster("png", 20, 20)));

        assertThat(service.deleteTravelInfoContentImage(image.url())).isTrue();
        assertThat(Files.exists(stored(image.url()))).isFalse();
        // 이미 없는 파일은 목표가 이뤄진 상태다.
        assertThat(service.deleteTravelInfoContentImage(image.url())).isFalse();
    }

    @Test
    void refusesPathsOutsideTheContentDirectoryOrNotMadeByTheServer() throws IOException {
        FileUploadService service = service();
        String name = UUID.randomUUID() + ".jpg";
        Path thumbnails = Files.createDirectories(uploadRoot.resolve("travel-info/thumbnails"));
        Path victim = Files.write(thumbnails.resolve(name), new byte[]{1});
        Files.createDirectories(uploadRoot.resolve("travel-info/content"));

        for (String url : List.of(
                "/uploads/travel-info/content/../thumbnails/" + name,
                "/uploads/travel-info/content/%2e%2e/thumbnails/" + name,
                "/uploads/travel-info/thumbnails/" + name,
                "/uploads/travel-info/content/" + name.toUpperCase(),
                "/uploads/travel-info/content/" + name + "/../x.jpg",
                "/uploads/travel-info/content/photo.jpg",
                "/uploads/travel-info/content/" + UUID.randomUUID() + ".svg",
                "https://example.com/uploads/travel-info/content/" + name,
                "")) {
            assertThatThrownBy(() -> service.deleteTravelInfoContentImage(url))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service.deleteTravelInfoContentImage(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.exists(victim)).isTrue();
    }

    // ---- helpers ---------------------------------------------------------------------------

    private void assertRejected(FileUploadService service, MockMultipartFile file, String message) {
        assertThatThrownBy(() -> service.saveTravelInfoContentImage(file))
                .isInstanceOf(UnsupportedImageFormatException.class)
                .hasMessage(message);
    }

    private FileUploadService service() {
        return new FileUploadService(uploadRoot.toString());
    }

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("image", name, contentType, content);
    }

    private Path stored(String url) {
        return uploadRoot.resolve(url.substring("/uploads/".length()));
    }

    private boolean isEmpty(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.findAny().isEmpty();
        }
    }

    /** 실제로 펼쳐지는 그림. 줄였을 때 압축이 잘 되는 단순한 무늬를 그린다. */
    private byte[] raster(String format, int width, int height) throws IOException {
        int type = "jpg".equals(format) || "bmp".equals(format)
                ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        if ("gif".equals(format)) {
            type = BufferedImage.TYPE_BYTE_INDEXED;
        }
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(40, 120, 200));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(width / 4, height / 4, width / 2, height / 2);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, bytes)).isTrue();
        return bytes.toByteArray();
    }

    /** 원하는 크기를 머리말에 적은 최소 VP8L WEBP. 이 런타임은 펼치지 못하므로 구조 검사만 지나면 된다. */
    private byte[] vp8lWebp(int width, int height) {
        int bits = (width - 1) | ((height - 1) << 14);
        byte[] payload = {0x2f, (byte) bits, (byte) (bits >> 8), (byte) (bits >> 16), (byte) (bits >>> 24)};
        int chunkSize = payload.length;
        int padded = chunkSize + (chunkSize & 1);
        int riffSize = 4 + 8 + padded;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.writeBytes(new byte[]{'R', 'I', 'F', 'F'});
        data.writeBytes(littleEndian(riffSize));
        data.writeBytes(new byte[]{'W', 'E', 'B', 'P', 'V', 'P', '8', 'L'});
        data.writeBytes(littleEndian(chunkSize));
        data.writeBytes(payload);
        if (padded != chunkSize) {
            data.write(0);
        }
        return data.toByteArray();
    }

    private byte[] littleEndian(int value) {
        return new byte[]{(byte) value, (byte) (value >> 8), (byte) (value >> 16), (byte) (value >>> 24)};
    }

    /** SOI 바로 뒤에 Orientation 만 담은 EXIF(APP1) 를 끼워 넣은 JPEG. (프로필 축소 테스트와 같은 방식) */
    private byte[] jpegWithOrientation(int width, int height, int orientation) throws IOException {
        byte[] base = raster("jpg", width, height);
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.write(new byte[]{'M', 'M', 0x00, 0x2a, 0x00, 0x00, 0x00, 0x08, 0x00, 0x01, 0x01, 0x12,
                0x00, 0x03, 0x00, 0x00, 0x00, 0x01, (byte) (orientation >> 8), (byte) orientation, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00});
        byte[] payload = tiff.toByteArray();
        int length = 2 + 6 + payload.length;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(base, 0, 2);
        output.write(new byte[]{(byte) 0xff, (byte) 0xe1, (byte) (length >> 8), (byte) length,
                'E', 'x', 'i', 'f', 0x00, 0x00});
        output.write(payload);
        output.write(base, 2, base.length - 2);
        // 실제로 펼쳐지는 JPEG 인지 확인해 둔다.
        assertThat(ImageIO.read(new ByteArrayInputStream(output.toByteArray()))).isNotNull();
        return output.toByteArray();
    }
}
