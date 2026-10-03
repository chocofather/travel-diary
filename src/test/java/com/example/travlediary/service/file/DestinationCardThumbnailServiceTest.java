package com.example.travlediary.service.file;

import com.example.travlediary.service.destination.DestinationImageService;
import com.example.travlediary.service.file.DestinationCardThumbnailService.CardImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 여행지 카드(메인·목록) 썸네일. 원본은 그대로 두고, 카드 상자를 빈틈없이 채우는 크기까지만 줄여 캐시한다.
 */
class DestinationCardThumbnailServiceTest {

    @TempDir
    Path uploadRoot;

    private final Set<String> noDerivativeUrls = ConcurrentHashMap.newKeySet();
    private final AtomicInteger licenseLookups = new AtomicInteger();
    private volatile boolean licenseLookupFails;
    private final DestinationImageService licenses = licenses();

    /** 공공누리 제3유형(변경금지)은 요청이 와도 줄이거나 잘라 만든 파일을 두지 않고 원본을 보낸다. */
    @Test
    void noDerivativesPhotosNeverGetAThumbnailFileAndServeTheOriginal() throws IOException {
        byte[] type3 = original("kogl3.jpg", 3000, 2000);
        original("kogl1.jpg", 3000, 2000);
        noDerivativeUrls.add("/uploads/destinations/kogl3.jpg");
        DestinationCardThumbnailService service = service();

        for (int width : new int[]{480, 960}) {
            Path served = service.resolve("v2", width, "kogl3.jpg").orElseThrow();
            assertThat(served).isEqualTo(uploadRoot.resolve("destinations/kogl3.jpg"));
            assertThat(service.isThumbnailFile(served)).isFalse();
            assertThat(uploadRoot.resolve("thumbnail-cache/destinations/v2/" + width + "/kogl3.jpg")).doesNotExist();
        }
        assertThat(Files.readAllBytes(uploadRoot.resolve("destinations/kogl3.jpg"))).isEqualTo(type3);
        // 1유형은 기존처럼 썸네일을 만든다.
        assertThat(dimensions(service.resolve("v2", 480, "kogl1.jpg").orElseThrow())).containsExactly(540, 360);
        assertThat(service.generatedCount()).isEqualTo(1);
    }

    /** 관리 화면·업로드 뒤 미리 만들기와 서버 시작 미리 만들기도 3유형은 건너뛴다. 라이선스는 한꺼번에 묻는다. */
    @Test
    void prewarmAndStartupPrewarmSkipNoDerivativesPhotos() throws Exception {
        original("kogl1.jpg", 3000, 2000);
        original("kogl3.jpg", 3000, 2000);
        original("startup1.jpg", 2400, 1600);
        original("startup3.jpg", 2400, 1600);
        noDerivativeUrls.addAll(List.of("/uploads/destinations/kogl3.jpg", "/uploads/destinations/startup3.jpg"));
        DestinationCardThumbnailService service = service();

        service.prewarm(List.of("/uploads/destinations/kogl1.jpg", "/uploads/destinations/kogl3.jpg"));
        awaitFile(uploadRoot.resolve("thumbnail-cache/destinations/v2/480/kogl1.jpg"));

        org.springframework.test.util.ReflectionTestUtils.setField(service, "prewarmOnStartup", true);
        service.prewarmExistingImages();
        awaitFile(uploadRoot.resolve("thumbnail-cache/destinations/v2/480/startup1.jpg"));
        awaitCondition(() -> service.generatedCount() == 2);
        Thread.sleep(200);

        assertThat(uploadRoot.resolve("thumbnail-cache/destinations/v2/480/kogl3.jpg")).doesNotExist();
        assertThat(uploadRoot.resolve("thumbnail-cache/destinations/v2/480/startup3.jpg")).doesNotExist();
        assertThat(service.isSmallThumbnailReady("/uploads/destinations/kogl3.jpg")).isFalse();
        assertThat(service.generatedCount()).isEqualTo(2);
    }

    /** 원본이 바뀌어 다시 만들 차례가 와도 3유형은 만들지 않는다. 예전에 만든 파일은 고쳐 쓰지 않는다. */
    @Test
    void regenerationSkipsNoDerivativesPhotosAndLeavesAnOldFileUntouched() throws IOException {
        original("kogl3.jpg", 3000, 2000);
        Path legacy = uploadRoot.resolve("thumbnail-cache/destinations/v2/480/kogl3.jpg");
        Files.createDirectories(legacy.getParent());
        Files.write(legacy, jpeg(540, 360));
        FileTime old = FileTime.from(Instant.parse("2026-01-01T00:00:00Z"));
        Files.setLastModifiedTime(legacy, old);
        noDerivativeUrls.add("/uploads/destinations/kogl3.jpg");
        DestinationCardThumbnailService service = service();

        assertThat(service.resolve("v2", 480, "kogl3.jpg")).contains(uploadRoot.resolve("destinations/kogl3.jpg"));
        assertThat(Files.getLastModifiedTime(legacy)).isEqualTo(old);
        assertThat(service.generatedCount()).isZero();
    }

    /** 라이선스를 확인하지 못하면 이번에는 만들지 않고 원본을 보낸다(추정하지 않는다). */
    @Test
    void unknownLicenseStateCreatesNothingThisTime() throws Exception {
        original("photo.jpg", 3000, 2000);
        licenseLookupFails = true;
        DestinationCardThumbnailService service = service();

        assertThat(service.resolve("v2", 480, "photo.jpg")).contains(uploadRoot.resolve("destinations/photo.jpg"));
        service.prewarm(List.of("/uploads/destinations/photo.jpg"));
        Thread.sleep(200);
        assertThat(uploadRoot.resolve("thumbnail-cache/destinations/v2/480/photo.jpg")).doesNotExist();

        licenseLookupFails = false;
        assertThat(service.isThumbnailFile(service.resolve("v2", 480, "photo.jpg").orElseThrow())).isTrue();
    }

    /** 이미 썸네일이 있는 사진은 라이선스를 다시 묻지 않는다(목록 요청마다 DB 를 묻지 않게). */
    @Test
    void existingThumbnailsAreServedWithoutAskingForTheLicenseAgain() throws IOException {
        original("photo.jpg", 3000, 2000);
        DestinationCardThumbnailService service = service();
        Path thumbnail = service.resolve("v2", 480, "photo.jpg").orElseThrow();
        int lookups = licenseLookups.get();

        assertThat(service.resolve("v2", 480, "photo.jpg")).contains(thumbnail);
        service.prewarm(List.of("/uploads/destinations/photo.jpg"));
        assertThat(licenseLookups.get()).isEqualTo(lookups);
    }

    /**
     * srcset 의 폭(w)은 실제로 보내는 파일의 가로 픽셀과 같아야 한다.
     * 가로·세로 사진, 파노라마, 상자보다 작은 사진 모두 확인한다.
     */
    @Test
    void srcsetWidthsMatchThePixelsOfTheFilesActuallyServed() throws IOException {
        original("landscape.jpg", 3000, 2000);
        original("portrait.jpg", 3456, 5184);
        original("panorama.jpg", 1920, 662);
        original("kto.jpg", 940, 627);
        original("tiny.jpg", 400, 300);
        DestinationCardThumbnailService service = service();

        assertThat(srcset(service, "landscape.jpg")).isEqualTo(
                "/thumbnails/destinations/v2/480/landscape.jpg 540w, /thumbnails/destinations/v2/960/landscape.jpg 1080w");
        assertThat(srcset(service, "portrait.jpg")).endsWith("/480/portrait.jpg 480w, /thumbnails/destinations/v2/960/portrait.jpg 960w");
        // 파노라마는 높이를 채우느라 폭이 넓고, 큰 쪽은 원본(1920)이 그대로 나간다.
        assertThat(srcset(service, "panorama.jpg")).endsWith("/480/panorama.jpg 1044w, /thumbnails/destinations/v2/960/panorama.jpg 1920w");
        assertThat(srcset(service, "kto.jpg")).endsWith("/480/kto.jpg 540w, /thumbnails/destinations/v2/960/kto.jpg 940w");
        // 두 크기가 같은 파일(원본)이면 후보를 하나만 둔다.
        assertThat(srcset(service, "tiny.jpg")).isEqualTo("/thumbnails/destinations/v2/480/tiny.jpg 400w");

        for (String name : new String[]{"landscape.jpg", "portrait.jpg", "panorama.jpg", "kto.jpg", "tiny.jpg"}) {
            for (String candidate : srcset(service, name).split(", ")) {
                String[] parts = candidate.split(" ");
                int variant = Integer.parseInt(parts[0].split("/")[4]);
                int declared = Integer.parseInt(parts[1].replace("w", ""));
                Path served = service.resolve("v2", variant, name).orElseThrow();
                assertThat(ImageIO.read(served.toFile()).getWidth()).as(candidate).isEqualTo(declared);
            }
        }
        assertThat(service.cardImage("/uploads/destinations/landscape.jpg")).get()
                .extracting(CardImage::src).isEqualTo("/thumbnails/destinations/v2/480/landscape.jpg");
    }

    /** 4:3 칸을 cover 로 채울 때 사진이 칸 폭의 몇 배로 그려지는지. 목록 카드 sizes 에 곱한다. */
    @Test
    void coverScaleGrowsOnlyForPhotosWiderThanTheBox() throws IOException {
        original("panorama.jpg", 1920, 662);
        original("landscape.jpg", 3000, 2000);
        original("portrait.jpg", 3456, 5184);
        DestinationCardThumbnailService service = service();

        assertThat(service.cardImage("/uploads/destinations/panorama.jpg").orElseThrow().coverScale(4, 3)).isEqualTo(2.18);
        assertThat(service.cardImage("/uploads/destinations/landscape.jpg").orElseThrow().coverScale(4, 3)).isEqualTo(1.13);
        assertThat(service.cardImage("/uploads/destinations/portrait.jpg").orElseThrow().coverScale(4, 3)).isEqualTo(1.0);
    }

    @Test
    void onlyDestinationUploadsThatExistBecomeCardThumbnails() throws IOException {
        original("photo.jpg", 3000, 2000);
        Files.createDirectories(uploadRoot.resolve("events"));
        Files.write(uploadRoot.resolve("events/photo.jpg"), jpeg(3000, 2000));
        DestinationCardThumbnailService service = service();

        // 외부 주소·기본 이미지·다른 폴더·없는 파일은 화면이 원래 주소를 그대로 쓴다.
        for (String other : new String[]{null, "", "/images/default.png", "https://example.com/a.jpg",
                "/uploads/events/photo.jpg", "/uploads/destinations/../events/photo.jpg",
                "/uploads/destinations/a.webp", "/uploads/destinations/missing.jpg"}) {
            assertThat(service.cardImage(other)).as(other).isEmpty();
        }
    }

    /** 가로·세로가 모두 상자 이상이 되게 줄이고, 원본 파일은 바뀌지 않는다. */
    @Test
    void largePhotosAreReducedToCoverTheCardBoxAndTheOriginalIsUntouched() throws IOException {
        byte[] landscape = original("landscape.jpg", 3000, 2000);
        DestinationCardThumbnailService service = service();

        assertThat(dimensions(service.resolve("v2", 480, "landscape.jpg").orElseThrow())).containsExactly(540, 360);
        assertThat(dimensions(service.resolve("v2", 960, "landscape.jpg").orElseThrow())).containsExactly(1080, 720);

        Path thumbnail = service.resolve("v2", 480, "landscape.jpg").orElseThrow();
        assertThat(thumbnail).startsWith(uploadRoot.resolve("thumbnail-cache"));
        assertThat(Files.size(thumbnail)).isLessThan(landscape.length);
        assertThat(Files.readAllBytes(uploadRoot.resolve("destinations/landscape.jpg"))).isEqualTo(landscape);
    }

    @Test
    void theThumbnailIsMadeOnceAndRemadeOnlyWhenTheOriginalChanges() throws IOException {
        original("photo.jpg", 3000, 2000);
        DestinationCardThumbnailService service = service();
        Path thumbnail = service.resolve("v2", 480, "photo.jpg").orElseThrow();
        FileTime made = FileTime.from(Instant.parse("2026-01-01T00:00:00Z"));
        Files.setLastModifiedTime(uploadRoot.resolve("destinations/photo.jpg"), made);
        Files.setLastModifiedTime(thumbnail, made);

        assertThat(service.resolve("v2", 480, "photo.jpg")).contains(thumbnail);
        assertThat(Files.getLastModifiedTime(thumbnail)).isEqualTo(made);

        // 원본이 바뀌면(썸네일보다 새 파일) 다시 만든다.
        Files.setLastModifiedTime(uploadRoot.resolve("destinations/photo.jpg"),
                FileTime.from(Instant.parse("2026-02-01T00:00:00Z")));
        service.resolve("v2", 480, "photo.jpg");
        assertThat(Files.getLastModifiedTime(thumbnail)).isGreaterThan(made);
    }

    /** 이미 상자보다 작은 사진은 키우거나 다시 굽지 않는다. */
    @Test
    void smallPhotosKeepTheirOriginalBytes() throws IOException {
        byte[] small = original("small.jpg", 940, 627);

        assertThat(Files.readAllBytes(service().resolve("v2", 960, "small.jpg").orElseThrow())).isEqualTo(small);
    }

    @Test
    void addressesOutsideTheRulesFindNothing() throws IOException {
        original("photo.jpg", 3000, 2000);
        Files.createDirectories(uploadRoot.resolve("profiles"));
        Files.write(uploadRoot.resolve("profiles/secret.jpg"), jpeg(10, 10));
        DestinationCardThumbnailService service = service();

        // 이전 규칙(v1) 주소는 더 이상 받지 않는다.
        assertThat(service.resolve("v1", 480, "photo.jpg")).isEmpty();
        assertThat(service.resolve("v2", 700, "photo.jpg")).isEmpty();
        assertThat(service.resolve("v2", 480, "missing.jpg")).isEmpty();
        assertThat(service.resolve("v2", 480, "../profiles/secret.jpg")).isEmpty();
        assertThat(service.resolve("v2", 480, "..")).isEmpty();
        assertThat(Files.exists(uploadRoot.resolve("thumbnail-cache/destinations/v2/480/secret.jpg"))).isFalse();
    }

    /** 같은 썸네일을 여러 요청이 동시에 기다려도 한 번만 만든다. */
    @Test
    void concurrentRequestsForTheSameThumbnailGenerateItOnce() throws Exception {
        original("photo.jpg", 3000, 2000);
        DestinationCardThumbnailService service = service();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        try {
            var results = pool.invokeAll(java.util.Collections.nCopies(6,
                    () -> service.resolve("v2", 480, "photo.jpg").orElseThrow()));
            for (var result : results) {
                assertThat(result.get()).isEqualTo(uploadRoot.resolve("thumbnail-cache/destinations/v2/480/photo.jpg"));
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(service.generatedCount()).isEqualTo(1);
    }

    /** 미리 만들기는 기다리지 않고 돌아오며, 같은 사진을 여러 번 넣어도 한 번만 만든다. 이미 있으면 다시 만들지 않는다. */
    @Test
    void prewarmReturnsAtOnceAndPreparesEachThumbnailOnce() throws Exception {
        original("a.jpg", 3000, 2000);
        original("b.jpg", 2400, 1600);
        DestinationCardThumbnailService service = service();
        List<String> urls = List.of("/uploads/destinations/a.jpg", "/uploads/destinations/b.jpg",
                "/uploads/destinations/a.jpg", "https://example.com/external.jpg");

        // 관리 화면 카드는 준비 전이면 원본을 쓴다.
        assertThat(service.isSmallThumbnailReady("/uploads/destinations/a.jpg")).isFalse();
        long started = System.nanoTime();
        service.prewarm(urls);
        service.prewarm(urls);
        assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofMillis(200));

        awaitFile(uploadRoot.resolve("thumbnail-cache/destinations/v2/480/a.jpg"));
        awaitFile(uploadRoot.resolve("thumbnail-cache/destinations/v2/480/b.jpg"));
        awaitCondition(() -> service.generatedCount() == 2);
        assertThat(service.isSmallThumbnailReady("/uploads/destinations/a.jpg")).isTrue();
        assertThat(service.isSmallThumbnailReady("https://example.com/external.jpg")).isFalse();
        service.prewarm(urls);
        Thread.sleep(200);
        assertThat(service.generatedCount()).isEqualTo(2);
        // 요청은 미리 만든 파일을 바로 쓴다.
        assertThat(service.resolve("v2", 480, "a.jpg")).contains(uploadRoot.resolve("thumbnail-cache/destinations/v2/480/a.jpg"));
        assertThat(service.generatedCount()).isEqualTo(2);
    }

    /** 한 장을 만들지 못해도 그 사진만 원본으로 보내고, 다른 사진은 그대로 썸네일을 받는다. */
    @Test
    void oneBrokenPhotoDoesNotBlockTheOthers() throws IOException {
        original("blocked.jpg", 3000, 2000);
        original("good.jpg", 3000, 2000);
        // 이 사진의 썸네일 자리에 비어 있지 않은 폴더가 있어 쓰기가 실패한다.
        Files.createDirectories(uploadRoot.resolve("thumbnail-cache/destinations/v2/480/blocked.jpg"));
        Files.write(uploadRoot.resolve("thumbnail-cache/destinations/v2/480/blocked.jpg/keep"), new byte[]{1});
        DestinationCardThumbnailService service = service();

        Path blocked = service.resolve("v2", 480, "blocked.jpg").orElseThrow();
        assertThat(blocked).isEqualTo(uploadRoot.resolve("destinations/blocked.jpg"));
        assertThat(service.isThumbnailFile(blocked)).isFalse();
        Path good = service.resolve("v2", 480, "good.jpg").orElseThrow();
        assertThat(dimensions(good)).containsExactly(540, 360);
        assertThat(service.isThumbnailFile(good)).isTrue();
    }

    /** 미리 만들기 줄이 길어도 브라우저 요청은 그 줄을 다 기다리지 않는다. */
    @Test
    void aRequestDoesNotWaitForTheWholePrewarmQueue() throws IOException {
        List<String> queued = new java.util.ArrayList<>();
        for (int index = 0; index < 8; index++) {
            original("queued" + index + ".jpg", 3200, 2400);
            queued.add("/uploads/destinations/queued" + index + ".jpg");
        }
        original("wanted.jpg", 3000, 2000);
        DestinationCardThumbnailService service = service();

        service.prewarm(queued);
        assertThat(service.resolve("v2", 480, "wanted.jpg")).contains(
                uploadRoot.resolve("thumbnail-cache/destinations/v2/480/wanted.jpg"));
        long preparedBeforeTheRequestFinished = queued.stream()
                .filter(url -> Files.exists(uploadRoot.resolve("thumbnail-cache/destinations/v2/480/"
                        + url.substring(url.lastIndexOf('/') + 1))))
                .count();
        assertThat(preparedBeforeTheRequestFinished).isLessThan(queued.size());
    }

    private static void awaitFile(Path file) throws InterruptedException {
        awaitCondition(() -> Files.exists(file));
    }

    private static void awaitCondition(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(20).toNanos();
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("기다리는 조건").isLessThan(deadline);
            Thread.sleep(20);
        }
    }

    private DestinationCardThumbnailService service() {
        return new DestinationCardThumbnailService(uploadRoot.toString(), licenses);
    }

    /** 공통 라이선스 판정 대신 쓰는 자리. {@link #noDerivativeUrls} 에 든 원본만 공공누리 제3유형으로 답한다. */
    @SuppressWarnings("unchecked")
    private DestinationImageService licenses() {
        DestinationImageService service = mock(DestinationImageService.class);
        when(service.noDerivativeImageUrls(any())).thenAnswer(invocation -> {
            if (licenseLookupFails) {
                throw new IllegalStateException("db down");
            }
            licenseLookups.incrementAndGet();
            return ((Collection<String>) invocation.getArgument(0)).stream()
                    .filter(noDerivativeUrls::contains)
                    .collect(Collectors.toSet());
        });
        return service;
    }

    private static String srcset(DestinationCardThumbnailService service, String name) {
        return service.cardImage("/uploads/destinations/" + name).orElseThrow().srcset();
    }

    private byte[] original(String name, int width, int height) throws IOException {
        byte[] bytes = jpeg(width, height);
        Files.createDirectories(uploadRoot.resolve("destinations"));
        Files.write(uploadRoot.resolve("destinations").resolve(name), bytes);
        return bytes;
    }

    /** 무늬가 있는 사진. 한 가지 색이면 줄여도 크기 차이가 나지 않는다. */
    private static byte[] jpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            for (int x = 0; x < width; x += 8) {
                graphics.setColor(new Color((x * 7) % 256, (x * 3) % 256, 180));
                graphics.fillRect(x, 0, 4, height);
            }
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", bytes);
        return bytes.toByteArray();
    }

    private static Integer[] dimensions(Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        return new Integer[]{image.getWidth(), image.getHeight()};
    }
}
