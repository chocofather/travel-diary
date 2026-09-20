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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 프로필 사진은 어디에서도 원본 크기로 보이지 않는다. 가장 큰 자리가 공개 프로필의 82×82 이고
 * 헤더와 댓글에서는 그보다 작다. 그런데 5MB 짜리가 그대로 저장되고 있었다.
 *
 * <p>그래서 저장하는 파일 자체를 줄인다. 여기서 고정하는 것은 <b>화면에 보이는 품질은 그대로</b>
 * 라는 것과, <b>줄이지 못하는 경우에도 업로드가 실패하지 않는다</b>는 것이다.
 * 뒤엣것이 무너지면 지금까지 되던 업로드가 갑자기 막히므로 형식별로 하나씩 확인한다.
 */
class ProfileImageResizeTest {

    /** 저장하는 사진의 긴 변 상한. 82px 자리를 3배 해상도로 채우고도 남는다. */
    private static final int MAX_EDGE = 256;

    @TempDir
    Path uploadRoot;

    // ---------- 큰 사진은 줄여서 저장한다 ----------

    @Test
    void aLargeJpegIsStoredWithinTheLongEdgeLimit() throws IOException {
        byte[] original = jpeg(1600, 1200);

        BufferedImage stored = storedImage(save("avatar.jpg", "image/jpeg", original));

        assertThat(Math.max(stored.getWidth(), stored.getHeight())).isEqualTo(MAX_EDGE);
        assertThat(stored.getWidth()).isEqualTo(256);
        assertThat(stored.getHeight()).isEqualTo(192);
    }

    @Test
    void aLargePngIsStoredWithinTheLongEdgeLimit() throws IOException {
        byte[] original = png(900, 1800, false);

        BufferedImage stored = storedImage(save("avatar.png", "image/png", original));

        assertThat(Math.max(stored.getWidth(), stored.getHeight())).isEqualTo(MAX_EDGE);
        assertThat(stored.getWidth()).isEqualTo(128);
        assertThat(stored.getHeight()).isEqualTo(256);
    }

    @Test
    void theStoredFileIsMuchSmallerThanTheOriginal() throws IOException {
        byte[] original = jpeg(1600, 1200);

        Path stored = storedPath(save("avatar.jpg", "image/jpeg", original));

        assertThat(Files.size(stored)).isLessThan(original.length / 2L);
    }

    /** 세로로 긴 사진도 가로로 긴 사진도 원래 비율 그대로여야 한다. */
    @Test
    void theAspectRatioIsKept() throws IOException {
        BufferedImage wide = storedImage(save("wide.jpg", "image/jpeg", jpeg(1000, 400)));
        BufferedImage tall = storedImage(save("tall.jpg", "image/jpeg", jpeg(400, 1000)));

        assertThat((double) wide.getWidth() / wide.getHeight())
                .isCloseTo(1000.0 / 400.0, org.assertj.core.data.Offset.offset(0.02));
        assertThat((double) tall.getWidth() / tall.getHeight())
                .isCloseTo(400.0 / 1000.0, org.assertj.core.data.Offset.offset(0.02));
    }

    // ---------- 원본 전체를 메모리에 올리지 않는다 ----------

    /**
     * 5MB 상한은 파일 크기일 뿐 픽셀 수를 막지 못한다. 잘 압축된 거대한 그림 한 장이
     * 수 GB 를 집어삼킬 수 있으므로, 펼치는 단계부터 건너뛰며 읽어 크기를 묶어 둔다.
     */
    @Test
    void theDecodeStepStaysWithinItsBudget() {
        assertThat(ProfileImageResizer.subsamplingStep(800)).isEqualTo(1);
        assertThat(ProfileImageResizer.subsamplingStep(1024)).isEqualTo(1);
        assertThat(ProfileImageResizer.subsamplingStep(2048)).isEqualTo(2);
        assertThat(ProfileImageResizer.subsamplingStep(12000)).isEqualTo(12);
        // 12000px 를 12칸씩 건너뛰면 1000px. 상한 안에 든다.
        assertThat(12000 / ProfileImageResizer.subsamplingStep(12000))
                .isLessThanOrEqualTo(ProfileImageResizer.DECODE_EDGE);
    }

    @Test
    void aLargeJpegIsNeverDecodedAtItsFullResolution() throws IOException {
        BufferedImage decoded =
                ProfileImageResizer.decodeWithinBudget(jpeg(4000, 3000), "jpeg");

        assertThat(decoded).isNotNull();
        assertThat(Math.max(decoded.getWidth(), decoded.getHeight()))
                .isLessThanOrEqualTo(ProfileImageResizer.DECODE_EDGE);
        assertThat(decoded.getWidth()).isLessThan(4000);
    }

    @Test
    void aLargePngIsNeverDecodedAtItsFullResolution() throws IOException {
        BufferedImage decoded =
                ProfileImageResizer.decodeWithinBudget(png(3000, 3000, true), "png");

        assertThat(decoded).isNotNull();
        assertThat(Math.max(decoded.getWidth(), decoded.getHeight()))
                .isLessThanOrEqualTo(ProfileImageResizer.DECODE_EDGE);
        // 건너뛰며 읽어도 투명도는 그대로 살아 있어야 한다.
        assertThat(decoded.getColorModel().hasAlpha()).isTrue();
    }

    /** 치수는 머리말만 보고 알아낸다. 여기서 픽셀을 펼치면 방어가 무너진다. */
    @Test
    void theOriginalSizeIsReadFromTheHeaderAlone() throws IOException {
        assertThat(ProfileImageResizer.readDimensions(jpeg(4000, 3000), "jpeg"))
                .containsExactly(4000, 3000);
        assertThat(ProfileImageResizer.readDimensions(png(640, 480, false), "png"))
                .containsExactly(640, 480);
    }

    /**
     * 머리말조차 읽히지 않는 파일은 치수를 알려 주지 못한다.
     * {@code null} 을 주든 IOException 을 올리든, 부르는 쪽은 원본 저장으로 되돌아간다.
     * (그 결과는 {@code anUndecodableImageFallsBackToTheOriginalBytes} 가 확인한다)
     */
    @Test
    void anUnreadableFileNeverReportsDimensions() {
        byte[] broken = new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0x00, 0x10};

        assertThatThrownBy(() -> ProfileImageResizer.readDimensions(broken, "jpeg"))
                .isInstanceOf(IOException.class);
    }

    // ---------- 작은 사진은 건드리지 않는다 ----------

    @Test
    void anImageAlreadyWithinTheLimitIsNeverEnlarged() throws IOException {
        byte[] original = jpeg(120, 80);

        BufferedImage stored = storedImage(save("small.jpg", "image/jpeg", original));

        assertThat(stored.getWidth()).isEqualTo(120);
        assertThat(stored.getHeight()).isEqualTo(80);
    }

    /** 이미 작은 사진은 다시 굽지 않는다. 다시 구우면 품질만 깎이고 얻는 것이 없다. */
    @Test
    void anImageAlreadyWithinTheLimitIsStoredByteForByte() throws IOException {
        byte[] original = jpeg(200, 200);

        Path stored = storedPath(save("small.jpg", "image/jpeg", original));

        assertThat(Files.readAllBytes(stored)).isEqualTo(original);
    }

    // ---------- 투명도 ----------

    @Test
    void transparencySurvivesTheResize() throws IOException {
        byte[] original = png(800, 800, true);

        BufferedImage stored = storedImage(save("avatar.png", "image/png", original));

        assertThat(stored.getColorModel().hasAlpha()).isTrue();
        // 왼쪽 위는 완전히 비워 두었다. 줄인 뒤에도 비어 있어야 한다.
        assertThat(new Color(stored.getRGB(2, 2), true).getAlpha()).isZero();
        // 오른쪽 아래는 채워 두었다.
        int filledX = stored.getWidth() - 3;
        int filledY = stored.getHeight() - 3;
        assertThat(new Color(stored.getRGB(filledX, filledY), true).getAlpha()).isEqualTo(255);
    }

    // ---------- 줄이지 못하는 경우 ----------

    /**
     * WebP 는 이 런타임의 ImageIO 가 읽지도 쓰지도 못한다.
     * 줄이지 못한다고 업로드를 막지 않고, 원본을 그대로 저장한다.
     */
    @Test
    void aWebpIsStoredUntouchedBecauseTheRuntimeCannotReEncodeIt() throws IOException {
        byte[] original = webpHeaderOnly();

        Path stored = storedPath(save("avatar.webp", "image/webp", original));

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
     * 그렇게 하지 않으면 지금까지 똑바로 보이던 사진이 눕는다.
     */
    @Test
    void aRotatedPhotoIsUprightedBeforeItIsStored() throws IOException {
        // 가로로 누운 1200x600 에 "왼쪽으로 90도 돌려서 보라"(orientation 6) 를 붙인다.
        byte[] original = jpegWithOrientation(1200, 600, 6);

        BufferedImage stored = storedImage(save("photo.jpg", "image/jpeg", original));

        // 돌려 놓았으므로 세로가 길어진다.
        assertThat(stored.getWidth()).isEqualTo(128);
        assertThat(stored.getHeight()).isEqualTo(256);
    }

    @Test
    void anUnrotatedPhotoIsLeftAsItIs() throws IOException {
        byte[] original = jpegWithOrientation(1200, 600, 1);

        BufferedImage stored = storedImage(save("photo.jpg", "image/jpeg", original));

        assertThat(stored.getWidth()).isEqualTo(256);
        assertThat(stored.getHeight()).isEqualTo(128);
    }

    // ---------- 기존 계약 ----------

    @Test
    void theStoredUrlAndExtensionContractIsUnchanged() throws IOException {
        assertThat(save("avatar.jpeg", "image/jpeg", jpeg(600, 600)))
                .startsWith("/uploads/profiles/").endsWith(".jpg");
        assertThat(save("avatar.png", "image/png", png(600, 600, false)))
                .startsWith("/uploads/profiles/").endsWith(".png");
    }

    /** 줄이다가 잘못되더라도 반쯤 쓰다 만 파일이 남으면 안 된다. */
    @Test
    void noPartialFileIsLeftBehind() throws IOException {
        save("avatar.jpg", "image/jpeg", jpeg(1600, 1200));

        Path directory = uploadRoot.resolve("profiles");
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
        return new ProfileImageStorageService(uploadRoot.toString())
                .saveProfileImage(new MockMultipartFile("profileImageFile", name, contentType, content));
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
