package com.tripbora.service.file;

import com.tripbora.service.destination.DestinationImageService;
import com.tripbora.service.file.DestinationThumbnailCacheCleanupService.CleanupPlan;
import com.tripbora.service.file.DestinationThumbnailCacheCleanupService.CleanupResult;
import com.tripbora.service.file.DestinationThumbnailCacheCleanupService.CleanupTarget;
import com.tripbora.service.file.DestinationThumbnailCacheCleanupService.PlanChangedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 예전에 만든 공공누리 제3유형(변경금지) 카드 썸네일 캐시 정리. 실제 임시 폴더로 확인한다. */
class DestinationThumbnailCacheCleanupServiceTest {

    @TempDir
    Path workspace;

    private Path uploadRoot;
    private Path outside;
    private Set<String> type3Urls;
    private DestinationImageService licenses;

    @BeforeEach
    void setUp() throws IOException {
        uploadRoot = workspace.resolve("uploads");
        outside = workspace.resolve("outside.jpg");
        Files.write(outside, jpeg());
        type3Urls = Set.of("/uploads/destinations/kogl3a.jpg", "/uploads/destinations/kogl3b.jpg",
                "/uploads/destinations/orphan3.jpg", "/uploads/destinations/link3.jpg");
        licenses = licenses();

        original("kogl1.jpg");
        original("kogl3a.jpg");
        original("kogl3b.jpg"); // 캐시가 없는 3유형: 지울 파일 없음으로 지나간다.
        for (String variant : List.of("v1/480", "v2/480", "v2/960")) {
            cache(variant, "kogl3a.jpg");
            cache(variant, "kogl1.jpg");
        }
        cache("v2/480", "orphan3.jpg"); // 원본은 지워졌지만 DB 에 3유형으로 남은 사진의 캐시
        // 정리 대상이 아닌 것들
        write(uploadRoot.resolve("thumbnail-cache/destinations/v3/480/kogl3a.jpg"));
        write(uploadRoot.resolve("thumbnail-cache/destinations/v2/480/notes.txt"));
        write(uploadRoot.resolve("thumbnail-cache/other/kogl3a.jpg"));
        write(uploadRoot.resolve("travel-info/kogl3a.jpg"));
        Files.createSymbolicLink(uploadRoot.resolve("thumbnail-cache/destinations/v2/960/link3.jpg"), outside);
    }

    @Test
    void dryRunFindsOnlyType3DerivedCachesAndDeletesNothing() throws IOException {
        List<Path> before = allFiles();

        CleanupPlan plan = service().plan();

        assertThat(plan.noDerivativeOriginalCount()).isEqualTo(3);
        assertThat(plan.targets()).extracting(CleanupTarget::relativePath).containsExactlyInAnyOrder(
                "thumbnail-cache/destinations/v1/480/kogl3a.jpg",
                "thumbnail-cache/destinations/v2/480/kogl3a.jpg",
                "thumbnail-cache/destinations/v2/960/kogl3a.jpg",
                "thumbnail-cache/destinations/v2/480/orphan3.jpg");
        assertThat(plan.countsByVariant()).containsExactly(
                java.util.Map.entry("v1/480", 1L), java.util.Map.entry("v1/960", 0L),
                java.util.Map.entry("v2/480", 2L), java.util.Map.entry("v2/960", 1L));
        assertThat(plan.totalBytes()).isEqualTo(4L * jpeg().length);
        assertThat(allFiles()).containsExactlyElementsOf(before);
    }

    @Test
    void executeDeletesOnlyTheConfirmedType3CachesAndKeepsEverythingElse() throws IOException {
        DestinationThumbnailCacheCleanupService service = service();
        CleanupPlan plan = service.plan();

        CleanupResult result = service.execute(plan.fileCount(), plan.totalBytes());

        assertThat(result.deleted()).hasSize(4);
        assertThat(result.failed()).isEmpty();
        assertThat(result.targetsGone()).isTrue();
        assertThat(result.originalsKept()).isTrue();
        for (String variant : List.of("v1/480", "v2/480", "v2/960")) {
            assertThat(cachePath(variant, "kogl3a.jpg")).doesNotExist();
            assertThat(cachePath(variant, "kogl1.jpg")).exists();
        }
        assertThat(cachePath("v2/480", "orphan3.jpg")).doesNotExist();
        for (String name : List.of("kogl1.jpg", "kogl3a.jpg", "kogl3b.jpg")) {
            assertThat(uploadRoot.resolve("destinations").resolve(name)).exists();
        }
        assertThat(uploadRoot.resolve("thumbnail-cache/destinations/v3/480/kogl3a.jpg")).exists();
        assertThat(uploadRoot.resolve("thumbnail-cache/destinations/v2/480/notes.txt")).exists();
        assertThat(uploadRoot.resolve("thumbnail-cache/other/kogl3a.jpg")).exists();
        assertThat(uploadRoot.resolve("travel-info/kogl3a.jpg")).exists();
        // 캐시 안의 링크도, 링크가 가리키는 밖의 파일도 건드리지 않는다.
        assertThat(Files.isSymbolicLink(cachePath("v2/960", "link3.jpg"))).isTrue();
        assertThat(outside).exists();

        // 다시 보면 정리할 파일 없음이다.
        assertThat(service.plan().isEmpty()).isTrue();
    }

    @Test
    void executeRefusesWhenTheConfirmedDryRunNoLongerMatches() throws IOException {
        DestinationThumbnailCacheCleanupService service = service();
        CleanupPlan plan = service.plan();
        List<Path> before = allFiles();

        assertThatThrownBy(() -> service.execute(plan.fileCount() - 1, plan.totalBytes()))
                .isInstanceOf(PlanChangedException.class);
        assertThatThrownBy(() -> service.execute(plan.fileCount(), plan.totalBytes() + 1))
                .isInstanceOf(PlanChangedException.class);
        assertThat(allFiles()).containsExactlyElementsOf(before);
    }

    @Test
    void nothingToCleanIsNotAnError() {
        DestinationThumbnailCacheCleanupService empty = new DestinationThumbnailCacheCleanupService(
                workspace.resolve("empty-uploads").toString(), licenses);

        CleanupPlan plan = empty.plan();

        assertThat(plan.isEmpty()).isTrue();
        assertThat(plan.noDerivativeOriginalCount()).isZero();
        assertThat(empty.execute(0, 0).deleted()).isEmpty();
    }

    /** 라이선스를 확인하지 못하면 계획을 만들지 않는다(추정해서 지우지 않는다). */
    @Test
    void unknownLicenseStateStopsBeforeDeletingAnything() throws IOException {
        DestinationImageService failing = mock(DestinationImageService.class);
        when(failing.noDerivativeImageUrls(any())).thenThrow(new IllegalStateException("db down"));
        DestinationThumbnailCacheCleanupService service =
                new DestinationThumbnailCacheCleanupService(uploadRoot.toString(), failing);
        List<Path> before = allFiles();

        assertThatThrownBy(service::plan).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.execute(4, 4L * jpeg().length)).isInstanceOf(IllegalStateException.class);
        assertThat(allFiles()).containsExactlyElementsOf(before);
    }

    @Test
    void onlyExactCachePathsOfKnownVersionsAndWidthsCanBeTargets() {
        DestinationThumbnailCacheCleanupService service = service();
        Path cacheRoot = uploadRoot.resolve("thumbnail-cache/destinations");

        assertThat(service.isSafeTarget(cacheRoot.resolve("v2/480/kogl3a.jpg"), "v2", 480, "kogl3a.jpg")).isTrue();
        // 원본·다른 폴더·경로 이동·다른 이름·모르는 버전과 폭·링크
        assertThat(service.isSafeTarget(uploadRoot.resolve("destinations/kogl3a.jpg"), "v2", 480, "kogl3a.jpg")).isFalse();
        assertThat(service.isSafeTarget(cacheRoot.resolve("v2/480/../../../destinations/kogl3a.jpg"),
                "v2", 480, "kogl3a.jpg")).isFalse();
        assertThat(service.isSafeTarget(cacheRoot.resolve("v2/480/kogl1.jpg"), "v2", 480, "kogl3a.jpg")).isFalse();
        assertThat(service.isSafeTarget(cacheRoot.resolve("v2/480/kogl3a.jpg"), "v2", 480, "../kogl3a.jpg")).isFalse();
        assertThat(service.isSafeTarget(cacheRoot.resolve("v3/480/kogl3a.jpg"), "v3", 480, "kogl3a.jpg")).isFalse();
        assertThat(service.isSafeTarget(cacheRoot.resolve("v2/700/kogl3a.jpg"), "v2", 700, "kogl3a.jpg")).isFalse();
        assertThat(service.isSafeTarget(cacheRoot.resolve("v2/960/link3.jpg"), "v2", 960, "link3.jpg")).isFalse();
        assertThat(service.isSafeTarget(outside, "v2", 480, "outside.jpg")).isFalse();
    }

    /** 정리한 뒤 예전 썸네일 주소로 다시 요청해도 새 파생 파일을 만들지 않고 원본을 보낸다. */
    @Test
    void afterCleanupAnOldType3ThumbnailRequestServesTheOriginalWithoutRecreatingIt() throws IOException {
        DestinationThumbnailCacheCleanupService cleanup = service();
        CleanupPlan plan = cleanup.plan();
        cleanup.execute(plan.fileCount(), plan.totalBytes());
        DestinationCardThumbnailService thumbnails =
                new DestinationCardThumbnailService(uploadRoot.toString(), licenses);

        for (int width : new int[]{480, 960}) {
            Path served = thumbnails.resolve("v2", width, "kogl3a.jpg").orElseThrow();
            assertThat(served).isEqualTo(uploadRoot.resolve("destinations/kogl3a.jpg").toAbsolutePath().normalize());
            assertThat(thumbnails.isThumbnailFile(served)).isFalse();
            assertThat(cachePath("v2/" + width, "kogl3a.jpg")).doesNotExist();
        }
        assertThat(thumbnails.generatedCount()).isZero();
    }

    private DestinationThumbnailCacheCleanupService service() {
        return new DestinationThumbnailCacheCleanupService(uploadRoot.toString(), licenses);
    }

    @SuppressWarnings("unchecked")
    private DestinationImageService licenses() {
        DestinationImageService service = mock(DestinationImageService.class);
        when(service.noDerivativeImageUrls(any())).thenAnswer(invocation ->
                ((Collection<String>) invocation.getArgument(0)).stream()
                        .filter(type3Urls::contains)
                        .collect(Collectors.toSet()));
        return service;
    }

    private void original(String name) throws IOException {
        write(uploadRoot.resolve("destinations").resolve(name));
    }

    private void cache(String variant, String name) throws IOException {
        write(cachePath(variant, name));
    }

    private Path cachePath(String variant, String name) {
        return uploadRoot.resolve("thumbnail-cache/destinations").resolve(variant).resolve(name);
    }

    private void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, jpeg());
    }

    private List<Path> allFiles() throws IOException {
        try (var files = Files.walk(workspace)) {
            return files.filter(path -> !Files.isDirectory(path)).sorted().toList();
        }
    }

    private static byte[] jpeg() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
        return bytes.toByteArray();
    }
}
