package com.example.travlediary.service.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 일반 여행정보(GENERAL)의 별도 업로드 썸네일은 목록 카드(약 278px), 관리자 미리보기,
 * 공유 카드의 og:image 로만 쓰인다. 상세 본문에는 그려지지 않는다.
 * 그런데 5MB 짜리 원본이 그대로 저장되고 있었다.
 *
 * <p>고정하는 계약은 이렇다. 목적이 내려받는 양을 줄이는 것이므로 "무조건 1200px" 이 아니라
 * "1200px 를 넘으면 줄이되, 줄인 쪽이 실제로 작을 때만 채택" 이다.
 * <ul>
 *   <li>1200px 이하 JPEG/PNG — 원본 바이트 그대로</li>
 *   <li>JPEG — 1200px 를 넘으면 1200px 로 줄인다</li>
 *   <li>PNG — 1200px 를 넘으면 줄이되, 줄인 것이 원본보다 작을 때만 채택한다</li>
 *   <li>WebP — 원본 그대로</li>
 * </ul>
 *
 * <p>여기에 더해 <b>줄이지 못하는 경우에도 업로드가 실패하지 않는다</b>는 것과
 * <b>프로필의 256px 정책이 여기로 새어 들어오지 않는다</b>는 것을 고정한다.
 * 공통 리사이즈 핵심을 프로필과 나눠 쓰므로 마지막 것이 중요하다.
 *
 * <p>축제(FESTIVAL)의 목록 대표 이미지는 이 경로를 지나지 않는다. 그쪽은 파일을 새로 올리지 않고
 * 갤러리 사진 하나를 {@code is_thumbnail} 로 가리키며, 같은 파일이 상세의 확대 보기에 쓰인다.
 * 그 경로는 {@code FestivalAdminServiceTest} 가 그대로 지키고 있다.
 */
class TravelInfoThumbnailResizeTest {

    /** 줄일 때 목표로 삼는 긴 변. 공유 카드 권장 폭에 맞춘 값이다. */
    private static final int MAX_EDGE = FileUploadService.TRAVEL_INFO_THUMBNAIL_MAX_EDGE;

    private static final String URL_PREFIX = "/uploads/travel-info/thumbnails/";

    @TempDir
    Path uploadRoot;

    // ---------- 큰 그림은 줄여서 저장한다 ----------

    @Test
    void aLargeJpegIsStoredWithinTheLongEdgeLimit() throws IOException {
        byte[] original = jpeg(3000, 2000);

        BufferedImage stored = storedImage(save("thumb.jpg", "image/jpeg", original));

        assertThat(Math.max(stored.getWidth(), stored.getHeight())).isEqualTo(MAX_EDGE);
        assertThat(stored.getWidth()).isEqualTo(1200);
        assertThat(stored.getHeight()).isEqualTo(800);
    }

    /** PNG 는 줄인 쪽이 실제로 작을 때만 채택한다. 이 그림은 줄이면 작아지므로 축소본이 남는다. */
    @Test
    void aLargePngIsStoredWithinTheLongEdgeLimitWhenTheResizeActuallyHelps() throws IOException {
        byte[] original = png(1500, 3000, false);

        BufferedImage stored = storedImage(save("thumb.png", "image/png", original));

        assertThat(Math.max(stored.getWidth(), stored.getHeight())).isEqualTo(MAX_EDGE);
        assertThat(stored.getWidth()).isEqualTo(600);
        assertThat(stored.getHeight()).isEqualTo(1200);
    }

    @Test
    void theStoredFileIsMuchSmallerThanTheOriginal() throws IOException {
        byte[] original = jpeg(3000, 2000);

        Path stored = storedPath(save("thumb.jpg", "image/jpeg", original));

        assertThat(Files.size(stored)).isLessThan(original.length / 2L);
    }

    @Test
    void theAspectRatioIsKept() throws IOException {
        BufferedImage wide = storedImage(save("wide.jpg", "image/jpeg", jpeg(2500, 1000)));
        BufferedImage tall = storedImage(save("tall.jpg", "image/jpeg", jpeg(1000, 2500)));

        assertThat((double) wide.getWidth() / wide.getHeight())
                .isCloseTo(2500.0 / 1000.0, org.assertj.core.data.Offset.offset(0.02));
        assertThat((double) tall.getWidth() / tall.getHeight())
                .isCloseTo(1000.0 / 2500.0, org.assertj.core.data.Offset.offset(0.02));
    }

    // ---------- 프로필 정책이 새어 들어오지 않는다 ----------

    /**
     * 프로필과 썸네일은 같은 리사이즈 핵심을 쓰지만 상한은 각자 넘긴다.
     * 여기가 256px 로 저장되면 공유 카드가 뭉개지고, 프로필이 1200px 로 저장되면
     * 82px 자리에 쓰려고 4.5배 큰 파일을 내려받게 된다. 같은 원본으로 한 번에 확인한다.
     */
    @Test
    void eachStorageKeepsItsOwnLongEdgePolicy() throws IOException {
        byte[] original = jpeg(2000, 2000);

        BufferedImage thumbnail = storedImage(save("thumb.jpg", "image/jpeg", original));
        String profileUrl = new ProfileImageStorageService(uploadRoot.toString())
                .saveProfileImage(new MockMultipartFile(
                        "profileImageFile", "avatar.jpg", "image/jpeg", original));
        BufferedImage profile = ImageIO.read(
                uploadRoot.resolve(profileUrl.substring("/uploads/".length())).toFile());

        assertThat(thumbnail.getWidth()).isEqualTo(FileUploadService.TRAVEL_INFO_THUMBNAIL_MAX_EDGE);
        assertThat(profile.getWidth()).isEqualTo(ProfileImageStorageService.PROFILE_MAX_EDGE);
    }

    // ---------- 원본 전체를 메모리에 올리지 않는다 ----------

    /**
     * 5MB 상한은 파일 크기일 뿐 픽셀 수를 막지 못한다. 잘 압축된 거대한 그림 한 장이
     * 수 GB 를 집어삼킬 수 있으므로, 펼치는 단계부터 건너뛰며 읽어 크기를 묶어 둔다.
     */
    @Test
    void theDecodeStepStaysWithinTheThumbnailBudget() {
        int budget = FileUploadService.TRAVEL_INFO_THUMBNAIL_DECODE_EDGE;

        assertThat(RasterImageResizer.subsamplingStep(2400, budget)).isEqualTo(1);
        assertThat(RasterImageResizer.subsamplingStep(4800, budget)).isEqualTo(2);
        assertThat(RasterImageResizer.subsamplingStep(24000, budget)).isEqualTo(10);
        assertThat(24000 / RasterImageResizer.subsamplingStep(24000, budget))
                .isLessThanOrEqualTo(budget);
    }

    @Test
    void aLargeJpegIsNeverDecodedAtItsFullResolution() throws IOException {
        BufferedImage decoded = RasterImageResizer.decodeWithinBudget(
                jpeg(6000, 4000), "jpeg", FileUploadService.TRAVEL_INFO_THUMBNAIL_DECODE_EDGE);

        assertThat(decoded).isNotNull();
        assertThat(Math.max(decoded.getWidth(), decoded.getHeight()))
                .isLessThanOrEqualTo(FileUploadService.TRAVEL_INFO_THUMBNAIL_DECODE_EDGE);
        assertThat(decoded.getWidth()).isLessThan(6000);
    }

    // ---------- 작은 그림은 건드리지 않는다 ----------

    @Test
    void anImageAlreadyWithinTheLimitIsNeverEnlarged() throws IOException {
        BufferedImage stored = storedImage(save("small.jpg", "image/jpeg", jpeg(800, 500)));

        assertThat(stored.getWidth()).isEqualTo(800);
        assertThat(stored.getHeight()).isEqualTo(500);
    }

    /** 이미 상한 안에 드는 그림은 다시 굽지 않는다. 다시 구우면 품질만 깎인다. */
    @Test
    void anImageAlreadyWithinTheLimitIsStoredByteForByte() throws IOException {
        byte[] original = jpeg(1200, 900);

        Path stored = storedPath(save("small.jpg", "image/jpeg", original));

        assertThat(Files.readAllBytes(stored)).isEqualTo(original);
    }

    // ---------- 투명도 ----------

    @Test
    void transparencySurvivesTheResize() throws IOException {
        byte[] original = png(3000, 3000, true);

        BufferedImage stored = storedImage(save("thumb.png", "image/png", original));

        // 이 그림은 줄이면 작아져서 축소본이 채택된다. 그 상태에서 투명도를 본다.
        assertThat(stored.getWidth()).isEqualTo(MAX_EDGE);
        assertThat(stored.getColorModel().hasAlpha()).isTrue();
        // 왼쪽 위는 완전히 비워 두었다. 줄인 뒤에도 비어 있어야 한다.
        assertThat(new Color(stored.getRGB(2, 2), true).getAlpha()).isZero();
        int filledX = stored.getWidth() - 3;
        int filledY = stored.getHeight() - 3;
        assertThat(new Color(stored.getRGB(filledX, filledY), true).getAlpha()).isEqualTo(255);
    }

    /**
     * 줄였는데 파일이 오히려 커지면 원본을 그대로 둔다.
     *
     * <p>PNG 는 넓은 단색 면일수록 잘 줄어드는데, 줄이는 과정의 보간이 그 면을 잘게 번지게 해서
     * 압축이 덜 된다. 내려받는 양을 줄이려고 하는 일이라 커진 쪽을 저장하면 앞뒤가 맞지 않는다.
     */
    @Test
    void aPngThatWouldGrowIsStoredAsUploaded() throws IOException {
        byte[] original = speckledPng(1600, 1200);

        Path stored = storedPath(save("thumb.png", "image/png", original));

        assertThat(Files.size(stored)).isLessThanOrEqualTo((long) original.length);
        assertThat(Files.readAllBytes(stored)).isEqualTo(original);
    }

    // ---------- 줄이지 못하는 경우 ----------

    /** WebP 는 이 런타임의 ImageIO 가 읽지도 쓰지도 못한다. 막지 않고 원본 그대로 저장한다. */
    @Test
    void aWebpIsStoredUntouchedBecauseTheRuntimeCannotReEncodeIt() throws IOException {
        byte[] original = webpHeaderOnly();

        Path stored = storedPath(save("thumb.webp", "image/webp", original));

        assertThat(stored.getFileName().toString()).endsWith(".webp");
        assertThat(Files.readAllBytes(stored)).isEqualTo(original);
    }

    /** 머리말은 맞지만 펼칠 수 없는 파일도 예전처럼 저장된다. 업로드가 새로 막히지 않는다. */
    @Test
    void anUndecodableImageFallsBackToTheOriginalBytes() throws IOException {
        byte[] original = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0x00, 0x10};

        Path stored = storedPath(save("broken.jpg", "image/jpeg", original));

        assertThat(Files.readAllBytes(stored)).isEqualTo(original);
    }

    // ---------- 촬영 방향 ----------

    /**
     * 휴대전화로 세로로 찍은 사진은 픽셀이 누운 채 저장되고 EXIF 가 "돌려서 보라"고 알려 준다.
     * 다시 구우면 그 표시가 사라지므로, 표시대로 픽셀을 돌려 두고 저장한다.
     */
    @Test
    void aRotatedPhotoIsUprightedBeforeItIsStored() throws IOException {
        byte[] original = jpegWithOrientation(2400, 1200, 6);

        BufferedImage stored = storedImage(save("photo.jpg", "image/jpeg", original));

        assertThat(stored.getWidth()).isEqualTo(600);
        assertThat(stored.getHeight()).isEqualTo(1200);
    }

    @Test
    void anUnrotatedPhotoIsLeftAsItIs() throws IOException {
        byte[] original = jpegWithOrientation(2400, 1200, 1);

        BufferedImage stored = storedImage(save("photo.jpg", "image/jpeg", original));

        assertThat(stored.getWidth()).isEqualTo(1200);
        assertThat(stored.getHeight()).isEqualTo(600);
    }

    // ---------- 기존 계약 ----------

    @Test
    void theStoredUrlAndExtensionContractIsUnchanged() throws IOException {
        assertThat(save("thumb.jpeg", "image/jpeg", jpeg(1600, 1600)))
                .startsWith(URL_PREFIX).endsWith(".jpg");
        assertThat(save("thumb.png", "image/png", png(1600, 1600, false)))
                .startsWith(URL_PREFIX).endsWith(".png");
        assertThat(save("thumb.webp", "image/webp", webpHeaderOnly()))
                .startsWith(URL_PREFIX).endsWith(".webp");
    }

    /** 줄이다가 잘못되더라도 반쯤 쓰다 만 파일이 남으면 안 된다. */
    @Test
    void noPartialFileIsLeftBehind() throws IOException {
        save("thumb.jpg", "image/jpeg", jpeg(3000, 2000));

        Path directory = uploadRoot.resolve("travel-info").resolve("thumbnails");
        try (var files = Files.list(directory)) {
            assertThat(files.map(path -> {
                try {
                    return Files.size(path);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            })).allSatisfy(size -> assertThat(size).isPositive());
        }
    }

    // ---------- 도우미 ----------

    private String save(String name, String contentType, byte[] content) {
        return new FileUploadService(uploadRoot.toString())
                .saveTravelInfoThumbnail(
                        new MockMultipartFile("thumbnailFile", name, contentType, content));
    }

    private Path storedPath(String url) {
        return uploadRoot.resolve(url.substring("/uploads/".length()));
    }

    private BufferedImage storedImage(String url) throws IOException {
        BufferedImage image = ImageIO.read(storedPath(url).toFile());
        assertThat(image).as("저장된 파일을 펼치지 못했다: %s", url).isNotNull();
        return image;
    }

    private byte[] jpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        paint(image, false);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        return output.toByteArray();
    }

    private byte[] png(int width, int height, boolean transparent) throws IOException {
        BufferedImage image = new BufferedImage(width, height,
                transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        paint(image, transparent);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    /** 왼쪽 위는 비우고 오른쪽 아래를 채운다. 줄인 뒤에도 그대로인지 보기 위한 무늬다. */
    private void paint(BufferedImage image, boolean transparent) {
        Graphics2D graphics = image.createGraphics();
        if (!transparent) {
            graphics.setColor(new Color(40, 90, 200));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        }
        graphics.setColor(new Color(220, 60, 60));
        graphics.fillRect(image.getWidth() / 2, image.getHeight() / 2,
                image.getWidth() / 2, image.getHeight() / 2);
        graphics.dispose();
    }

    /** 색 얼룩이 잘게 깔린 PNG. 줄이면 보간 때문에 색이 번져 오히려 커진다. */
    private byte[] speckledPng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < 4000; i++) {
            graphics.setColor(new Color(
                    random.nextInt(256), random.nextInt(256), random.nextInt(256)));
            int size = 20 + random.nextInt(120);
            graphics.fillOval(random.nextInt(width), random.nextInt(height), size, size);
        }
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    /** RIFF/WEBP 머리말만 있는 파일. 이 런타임은 어차피 펼치지 못한다. */
    private byte[] webpHeaderOnly() {
        return new byte[]{'R', 'I', 'F', 'F', 0x04, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'};
    }

    /** SOI 바로 뒤에 Orientation 만 담은 EXIF(APP1) 를 끼워 넣은 JPEG. */
    private byte[] jpegWithOrientation(int width, int height, int orientation) throws IOException {
        byte[] base = jpeg(width, height);
        byte[] app1 = exifApp1(orientation);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(base, 0, 2);                       // SOI
        output.write(app1);
        output.write(base, 2, base.length - 2);
        return output.toByteArray();
    }

    /** 빅엔디안 TIFF 헤더 + IFD0 항목 하나(0x0112 Orientation). */
    private byte[] exifApp1(int orientation) throws IOException {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.write(new byte[]{'M', 'M', 0x00, 0x2a});   // 빅엔디안, 매직 42
        tiff.write(new byte[]{0x00, 0x00, 0x00, 0x08}); // IFD0 오프셋
        tiff.write(new byte[]{0x00, 0x01});             // 항목 1개
        tiff.write(new byte[]{0x01, 0x12});             // tag = Orientation
        tiff.write(new byte[]{0x00, 0x03});             // type = SHORT
        tiff.write(new byte[]{0x00, 0x00, 0x00, 0x01}); // count = 1
        tiff.write(new byte[]{(byte) (orientation >> 8), (byte) orientation, 0x00, 0x00});
        tiff.write(new byte[]{0x00, 0x00, 0x00, 0x00}); // 다음 IFD 없음

        byte[] payload = tiff.toByteArray();
        int length = 2 + 6 + payload.length;            // 길이 2 + "Exif\0\0" 6 + TIFF
        ByteArrayOutputStream app1 = new ByteArrayOutputStream();
        app1.write(new byte[]{(byte) 0xff, (byte) 0xe1});
        app1.write(new byte[]{(byte) (length >> 8), (byte) length});
        app1.write(new byte[]{'E', 'x', 'i', 'f', 0x00, 0x00});
        app1.write(payload);
        return app1.toByteArray();
    }
}
