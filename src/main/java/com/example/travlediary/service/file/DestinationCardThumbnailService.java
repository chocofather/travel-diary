package com.example.travlediary.service.file;

import com.example.travlediary.service.file.DestinationCardThumbnails.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 메인 여행지 카드 썸네일 파일.
 *
 * <p>처음 요청될 때 원본에서 한 번 만들어 업로드 폴더 안의 캐시 폴더에 두고, 다음부터는 그 파일을 그대로 보낸다.
 * 원본({@code destinations/})과 DB 는 건드리지 않는다. 캐시 폴더는 공개 정적 매핑 목록에 없으며
 * 지워도 다시 만들어진다.
 *
 * <p>만드는 일은 정해진 작업자에게 맡긴다.
 * <ul>
 *   <li>브라우저 요청: 작업자 {@value #REQUEST_THREADS}개가 요청 순서대로 만든다. 예전처럼 서버 전체가 한 장씩
 *       줄을 서지 않으므로, 앞 사진 수십 장이 끝나기를 기다리지 않는다.</li>
 *   <li>미리 만들기(업로드 뒤·관리 화면을 열 때·서버 시작 때): 작업자 1개가 뒤에서 천천히 만든다.
 *       응답을 기다리게 하지 않는다.</li>
 *   <li>큰 사진을 펼치는 동안 메모리를 많이 쓰므로 동시에 펼치는 수는 {@value #DECODE_PERMITS}장으로 묶는다.
 *       미리 만들기는 그중 하나만 쓴다.</li>
 *   <li>같은 썸네일은 한 번만 만든다. 만드는 중에 또 요청되면 그 결과를 함께 기다린다.</li>
 * </ul>
 */
@Service
public class DestinationCardThumbnailService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DestinationCardThumbnailService.class);

    /**
     * 펼칠 때의 긴 변 상한. 업로드 사진(최대 10MB)은 대부분 이 안이라 건너뛰지 않고 전부 펼친 뒤
     * 절반씩 줄인다. 건너뛰며 읽으면 촘촘한 무늬(기와, 창살)가 줄무늬로 남는다.
     */
    static final int DECODE_EDGE = 6000;

    private static final String ORIGINAL_DIRECTORY = "destinations";
    private static final String CACHE_DIRECTORY = "thumbnail-cache/destinations";

    /** 치수를 읽을 때 여는 앞부분. EXIF(최대 64KB)와 치수 표시가 대개 이 안에 있다. */
    private static final int HEADER_BYTES = 256 * 1024;

    /** 브라우저 요청으로 썸네일을 만드는 작업자 수. */
    static final int REQUEST_THREADS = 2;
    /** 동시에 큰 사진을 펼치는 최대 수(요청·미리 만들기 합계). */
    static final int DECODE_PERMITS = 2;
    /** 요청이 자기 썸네일을 기다리는 최대 시간. 넘기면 원본을 보내 화면이 비지 않게 한다. */
    static final Duration REQUEST_WAIT = Duration.ofSeconds(30);

    private final Path uploadRoot;
    /** 파일 이름 → 바로 선 원본 치수. 목록을 그릴 때마다 원본 머리말을 다시 읽지 않는다. */
    private final Map<String, UprightSize> uprightSizes = new ConcurrentHashMap<>();
    /** 만드는 중인 썸네일 → 결과. 같은 썸네일을 두 번 만들지 않게 한다. */
    private final Map<Path, CompletableFuture<Path>> inFlight = new ConcurrentHashMap<>();
    /** 실제로 썸네일을 만든 횟수(같은 사진을 두 번 만들지 않는지 확인용). */
    private final AtomicInteger generatedCount = new AtomicInteger();
    /** 미리 만들기 줄에 이미 들어 있는 썸네일. 같은 사진을 줄에 여러 번 넣지 않는다. */
    private final Set<Path> queuedForPrewarm = ConcurrentHashMap.newKeySet();
    /** 펼치기 자리. 미리 만들기 작업자는 하나뿐이라 이 중 하나만 쓰고, 나머지 하나는 늘 브라우저 요청 몫이다. */
    private final Semaphore decodePermits = new Semaphore(DECODE_PERMITS, true);
    private final ExecutorService requestWorkers = Executors.newFixedThreadPool(REQUEST_THREADS,
            daemonThreads("destination-thumbnail-request-"));
    private final ExecutorService prewarmWorker = Executors.newSingleThreadExecutor(
            daemonThreads("destination-thumbnail-prewarm-"));

    /** 서버가 뜬 뒤 기존 여행지 사진의 관리용 썸네일을 뒤에서 미리 만든다(이미 있으면 확인만 한다). */
    @Value("${custom.thumbnails.prewarm-on-startup:true}")
    private boolean prewarmOnStartup = false;

    public DestinationCardThumbnailService(@Value("${custom.upload-path}") String uploadPath) {
        this.uploadRoot = Paths.get(uploadPath).toAbsolutePath().normalize();
    }

    /**
     * 카드 {@code <img>} 의 src(작은 썸네일)와 srcset.
     *
     * <p>srcset 의 폭 표기(w)는 실제로 보낼 파일의 가로 픽셀이다. 원본 치수로 셈하므로 썸네일을 미리 만들지 않아도
     * 맞는 값이 나온다. 원본이 상자보다 작아 두 크기가 같은 파일이면 후보를 하나만 둔다.
     *
     * @return 여행지 업로드 사진이 아니거나 원본을 읽을 수 없으면 empty — 화면은 원래 주소를 그대로 쓴다
     */
    public Optional<CardImage> cardImage(String imageUrl) {
        Optional<String> fileName = DestinationCardThumbnails.fileName(imageUrl);
        Optional<Path> original = fileName.flatMap(this::original);
        if (original.isEmpty()) {
            return Optional.empty();
        }
        UprightSize size = uprightSize(fileName.get(), original.get());
        if (size == null) {
            return Optional.empty();
        }
        String smallUrl = DestinationCardThumbnails.url(Variant.SMALL, fileName.get());
        int smallWidth = Variant.SMALL.servedWidth(size.width(), size.height());
        int largeWidth = Variant.LARGE.servedWidth(size.width(), size.height());
        String srcset = smallUrl + " " + smallWidth + "w";
        if (largeWidth > smallWidth) {
            srcset += ", " + DestinationCardThumbnails.url(Variant.LARGE, fileName.get()) + " " + largeWidth + "w";
        }
        return Optional.of(new CardImage(smallUrl, srcset, size.width(), size.height()));
    }

    /**
     * 카드 목록에 썸네일 주소를 채운다. 메인 추천 카드와 공개 여행지 목록 카드가 같은 규칙을 쓰게 하는 한 곳이다.
     *
     * @param imageUrl 카드의 원본 대표 이미지 주소를 꺼내는 함수
     * @param apply    썸네일이 있을 때만 불린다. 없으면 카드는 원래 주소를 그대로 쓴다
     */
    public <T> List<T> applyCardImages(List<T> items, Function<T, String> imageUrl,
                                       BiConsumer<T, CardImage> apply) {
        if (items != null) {
            for (T item : items) {
                if (item != null) {
                    cardImage(imageUrl.apply(item)).ifPresent(image -> apply.accept(item, image));
                }
            }
        }
        return items;
    }

    /**
     * 보낼 파일을 찾는다. 썸네일을 만들 수 없으면 원본을 돌려준다(카드가 비지 않게).
     *
     * @return 주소가 규칙에 맞지 않거나 원본이 없으면 empty
     */
    public Optional<Path> resolve(String version, int width, String fileName) {
        Optional<Variant> variant = Variant.ofWidth(width);
        if (!DestinationCardThumbnails.VERSION.equals(version) || variant.isEmpty()
                || !DestinationCardThumbnails.isFileName(fileName)) {
            return Optional.empty();
        }
        Optional<Path> found = original(fileName);
        Optional<Path> cachedPath = cachePath(variant.get(), fileName);
        if (found.isEmpty() || cachedPath.isEmpty()) {
            return Optional.empty();
        }
        Path original = found.get();
        Path cached = cachedPath.get();
        if (isFresh(cached, original)) {
            return Optional.of(cached);
        }
        // 이 사진 것만 기다린다. 다른 사진이 줄을 서 있어도, 같은 썸네일을 이미 만들고 있으면 그 결과를 함께 쓴다.
        CompletableFuture<Path> generation = generation(original, cached, variant.get(), requestWorkers);
        try {
            return Optional.of(generation.get(REQUEST_WAIT.toMillis(), TimeUnit.MILLISECONDS));
        } catch (TimeoutException timeout) {
            log.warn("Destination card thumbnail took too long, the original is served: file={}, width={}",
                    fileName, width);
            return Optional.of(original);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.of(original);
        } catch (ExecutionException failure) {
            // 이 사진 한 장만 원본으로 보낸다. 다른 사진의 썸네일에는 영향이 없다.
            return Optional.of(original);
        }
    }

    /**
     * 작은 썸네일이 이미 만들어져 있어 기다리지 않고 바로 보낼 수 있는지.
     * 관리 화면 카드는 준비된 것만 썸네일로 쓰고, 아직이면 원본을 쓴다(만들기를 기다리지 않게).
     */
    public boolean isSmallThumbnailReady(String imageUrl) {
        Optional<String> fileName = DestinationCardThumbnails.fileName(imageUrl);
        if (fileName.isEmpty()) {
            return false;
        }
        Optional<Path> original = original(fileName.get());
        Optional<Path> cached = cachePath(Variant.SMALL, fileName.get());
        return original.isPresent() && cached.isPresent() && isFresh(cached.get(), original.get());
    }

    /** {@link #resolve} 가 돌려준 파일이 썸네일인지(아니면 대신 보내는 원본). */
    public boolean isThumbnailFile(Path file) {
        return file.toAbsolutePath().normalize().startsWith(uploadRoot.resolve(CACHE_DIRECTORY));
    }

    /**
     * 관리 화면에 쓰는 작은 썸네일을 뒤에서 미리 만든다. 기다리지 않고 바로 돌아온다.
     * 이미 있거나 줄에 들어 있거나 만드는 중인 사진은 건너뛴다. 주어진 순서(화면 순서)대로 만든다.
     *
     * @param imageUrls 여행지 사진 원본 주소({@code /uploads/destinations/...}). 그 밖의 주소는 무시한다.
     */
    public void prewarm(Collection<String> imageUrls) {
        if (imageUrls == null) {
            return;
        }
        imageUrls.forEach(imageUrl -> DestinationCardThumbnails.fileName(imageUrl).ifPresent(this::prewarmFile));
    }

    /** 서버가 뜨면 기존 여행지 사진 폴더를 훑어 없는 관리용 썸네일만 뒤에서 만든다. */
    @EventListener(ApplicationReadyEvent.class)
    public void prewarmExistingImages() {
        if (!prewarmOnStartup) {
            return;
        }
        Path originalDirectory = uploadRoot.resolve(ORIGINAL_DIRECTORY);
        if (!Files.isDirectory(originalDirectory)) {
            return;
        }
        try (Stream<Path> files = Files.list(originalDirectory)) {
            files.map(path -> path.getFileName().toString())
                    .filter(DestinationCardThumbnails::isFileName)
                    .sorted()
                    .forEach(this::prewarmFile);
        } catch (IOException | RuntimeException failure) {
            log.warn("Existing destination images could not be listed for thumbnail prewarm: failureType={}",
                    failure.getClass().getSimpleName());
        }
    }

    private void prewarmFile(String fileName) {
        Optional<Path> found = original(fileName);
        Optional<Path> cachedPath = cachePath(Variant.SMALL, fileName);
        if (found.isEmpty() || cachedPath.isEmpty()) {
            return;
        }
        Path original = found.get();
        Path cached = cachedPath.get();
        if (isFresh(cached, original) || inFlight.containsKey(cached) || !queuedForPrewarm.add(cached)) {
            return;
        }
        try {
            prewarmWorker.execute(() -> {
                try {
                    if (!isFresh(cached, original)) {
                        // 이 작업자 안에서 바로 만든다. 그사이 브라우저 요청이 먼저 만들기 시작했다면 그쪽에 맡기고 넘어간다.
                        generation(original, cached, Variant.SMALL, Runnable::run);
                    }
                } finally {
                    queuedForPrewarm.remove(cached);
                }
            });
        } catch (RejectedExecutionException shutdown) {
            queuedForPrewarm.remove(cached);
        }
    }

    /**
     * 이 썸네일 만들기를 한 번만 시작한다. 이미 만드는 중이면 그 결과를 돌려준다.
     *
     * @param executor 요청 작업자, 또는 미리 만들기 작업자 안에서 바로 실행({@code Runnable::run})
     */
    private CompletableFuture<Path> generation(Path original, Path cached, Variant variant, Executor executor) {
        CompletableFuture<Path> created = new CompletableFuture<>();
        CompletableFuture<Path> running = inFlight.putIfAbsent(cached, created);
        if (running != null) {
            return running;
        }
        try {
            executor.execute(() -> runGeneration(original, cached, variant, created));
        } catch (RejectedExecutionException rejected) {
            inFlight.remove(cached, created);
            created.completeExceptionally(rejected);
        }
        return created;
    }

    private void runGeneration(Path original, Path cached, Variant variant, CompletableFuture<Path> result) {
        boolean decodeSlot = false;
        try {
            if (!isFresh(cached, original)) {
                decodePermits.acquire();
                decodeSlot = true;
                // 기다리는 사이 다른 작업이 만들었을 수 있다.
                if (!isFresh(cached, original)) {
                    generate(original, cached, variant);
                    generatedCount.incrementAndGet();
                }
            }
            result.complete(cached);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            result.completeExceptionally(interrupted);
        } catch (IOException | RuntimeException failure) {
            log.warn("Destination card thumbnail could not be written, the original is served:"
                    + " file={}, width={}, failureType={}",
                    cached.getFileName(), variant.width, failure.getClass().getSimpleName());
            result.completeExceptionally(failure);
        } catch (Error error) {
            result.completeExceptionally(error);
            throw error;
        } finally {
            if (decodeSlot) {
                decodePermits.release();
            }
            inFlight.remove(cached, result);
        }
    }

    /** 이 크기 썸네일의 캐시 파일 위치. 캐시 폴더 밖을 가리키면 empty. */
    private Optional<Path> cachePath(Variant variant, String fileName) {
        Path cacheDirectory = uploadRoot.resolve(CACHE_DIRECTORY)
                .resolve(DestinationCardThumbnails.VERSION)
                .resolve(String.valueOf(variant.width))
                .normalize();
        Path cached = cacheDirectory.resolve(fileName).normalize();
        return cached.startsWith(cacheDirectory) ? Optional.of(cached) : Optional.empty();
    }

    int generatedCount() {
        return generatedCount.get();
    }

    @Override
    public void destroy() {
        prewarmWorker.shutdownNow();
        requestWorkers.shutdownNow();
    }

    private static ThreadFactory daemonThreads(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            // 사이트 응답보다 앞서지 않게 조금 낮춘다.
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
    }

    /** 여행지 업로드 폴더 안의 원본. 규칙에 맞지 않거나 없으면 empty. */
    private Optional<Path> original(String fileName) {
        if (!DestinationCardThumbnails.isFileName(fileName)) {
            return Optional.empty();
        }
        Path originalDirectory = uploadRoot.resolve(ORIGINAL_DIRECTORY).normalize();
        Path original = originalDirectory.resolve(fileName).normalize();
        if (!original.startsWith(originalDirectory) || !Files.isRegularFile(original)) {
            return Optional.empty();
        }
        return Optional.of(original);
    }

    /**
     * 보이는 방향(EXIF 회전 반영) 그대로의 원본 치수. 원본이 바뀌면 다시 읽는다.
     *
     * @return JPEG/PNG 가 아니거나 읽을 수 없으면 {@code null}
     */
    private UprightSize uprightSize(String fileName, Path original) {
        try {
            FileTime modified = Files.getLastModifiedTime(original);
            UprightSize cached = uprightSizes.get(fileName);
            if (cached != null && cached.modified().equals(modified)) {
                return cached;
            }
            byte[] header;
            try (InputStream input = Files.newInputStream(original)) {
                header = input.readNBytes(HEADER_BYTES);
            }
            String format = imageIoFormat(header);
            if (format == null) {
                return null;
            }
            int[] dimensions = readDimensions(header, format);
            if (dimensions == null) {
                // 앞부분에 치수가 없는 드문 파일(큰 색 프로필 등)은 전체를 읽는다.
                dimensions = readDimensions(Files.readAllBytes(original), format);
            }
            if (dimensions == null) {
                return null;
            }
            boolean swap = JpegOrientation.swapsEdges(JpegOrientation.read(header));
            UprightSize size = new UprightSize(swap ? dimensions[1] : dimensions[0],
                    swap ? dimensions[0] : dimensions[1], modified);
            uprightSizes.put(fileName, size);
            return size;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static int[] readDimensions(byte[] bytes, String format) {
        try {
            return RasterImageResizer.readDimensions(bytes, format);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * 카드 이미지 주소. {@code srcset} 은 "주소 폭w" 후보를 쉼표로 이은 값이다.
     *
     * @param width  바로 선 원본의 가로 픽셀 (비율 계산용)
     * @param height 바로 선 원본의 세로 픽셀
     */
    public record CardImage(String src, String srcset, int width, int height) {

        /**
         * {@code object-fit: cover} 로 {@code boxWidth:boxHeight} 칸을 채울 때 실제로 그려지는 사진 폭 ÷ 칸 폭.
         *
         * <p>칸보다 가로로 긴 사진은 높이에 맞춰 커지고 양옆이 잘린다. 브라우저는 srcset 의 폭(w)만 보고 고르므로
         * {@code sizes} 에 이 배율을 곱해 알려 줘야 파노라마에서 낮은 해상도를 고르지 않는다. 칸보다 좁은 사진은 1 이다.
         * 조금 모자라게 고르지 않도록 소수 둘째 자리에서 올린다.
         */
        public double coverScale(int boxWidth, int boxHeight) {
            double scale = Math.max(1, (double) width * boxHeight / ((double) height * boxWidth));
            return Math.ceil(scale * 100) / 100;
        }
    }

    private record UprightSize(int width, int height, FileTime modified) {
    }

    /** 원본보다 늦게 만든 썸네일만 쓴다. 원본이 바뀌면 다시 만든다. */
    private static boolean isFresh(Path cached, Path original) {
        try {
            return Files.isRegularFile(cached)
                    && Files.getLastModifiedTime(cached).compareTo(Files.getLastModifiedTime(original)) >= 0;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 상자보다 큰 사진은 줄인 결과를 쓴다. 줄였으면 언제나 그 결과를 써야 srcset 에 적은 폭과 실제 픽셀이 맞는다.
     * 이미 작은 사진이나 줄일 수 없는 파일은 원본 바이트를 그대로 두되, 캐시에 써 두어 다음 요청에서 다시 판단하지 않게 한다.
     */
    private static void generate(Path original, Path cached, Variant variant) throws IOException {
        byte[] source = Files.readAllBytes(original);
        byte[] content = source;
        String format = imageIoFormat(source);
        if (format != null && RasterImageResizer.canResize(format)) {
            content = RasterImageResizer.coverThumbnail(source, format, "png".equals(format),
                    variant.width, variant.height, DECODE_EDGE);
        }
        Files.createDirectories(cached.getParent());
        Path temporary = Files.createTempFile(cached.getParent(), ".thumbnail-", ".tmp");
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, cached, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, cached, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** 확장자가 아니라 파일 앞머리로 형식을 정한다. JPEG/PNG 가 아니면 줄이지 않는다. */
    private static String imageIoFormat(byte[] source) {
        if (source.length > 3 && (source[0] & 0xFF) == 0xFF && (source[1] & 0xFF) == 0xD8) {
            return "jpeg";
        }
        if (source.length > 8 && (source[0] & 0xFF) == 0x89 && source[1] == 'P'
                && source[2] == 'N' && source[3] == 'G') {
            return "png";
        }
        return null;
    }
}
