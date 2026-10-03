package com.example.travlediary.service.file;

import com.example.travlediary.service.destination.DestinationImageService;
import com.example.travlediary.service.file.DestinationCardThumbnails.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * 예전에 만들어 둔 공공누리 제3유형(변경금지) 여행지 카드 썸네일 파일 정리. 관리자가 직접 실행하는 유지보수 작업이다.
 *
 * <p>대상은 DB 에서 지금 제3유형으로 확실히 판정되는 여행지 원본({@code /uploads/destinations/{파일}})에서 만들어진
 * 카드 썸네일 캐시 파일뿐이다. 원본·DB·다른 캐시는 건드리지 않는다. 라이선스는 공통 판정
 * ({@link DestinationImageService#noDerivativeImageUrls}, 출처 행 우선)을 그대로 쓰고, 확인하지 못하면 멈춘다.</p>
 *
 * <p>{@link #plan()}(dry-run)은 아무것도 지우지 않는다. {@link #execute}는 같은 계산을 다시 하고,
 * 관리자가 확인한 dry-run 결과(파일 수·용량)와 같을 때만 지운다. 서버 시작 등 자동 경로에서는 부르지 않는다.</p>
 */
@Service
public class DestinationThumbnailCacheCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DestinationThumbnailCacheCleanupService.class);

    private final Path uploadRoot;
    private final DestinationImageService destinationImageService;

    public DestinationThumbnailCacheCleanupService(@Value("${custom.upload-path}") String uploadPath,
                                                   DestinationImageService destinationImageService) {
        this.uploadRoot = Paths.get(uploadPath).toAbsolutePath().normalize();
        this.destinationImageService = destinationImageService;
    }

    /** dry-run. 지울 파일을 계산만 한다. */
    public CleanupPlan plan() {
        List<String> candidates = candidateFileNames();
        // 라이선스를 확인하지 못하면 예외가 그대로 올라가 계획을 만들지 않는다(추정하지 않는다).
        Set<String> noDerivatives = new TreeSet<>(
                DestinationCardThumbnails.noDerivativeFileNames(destinationImageService, candidates));
        noDerivatives.retainAll(candidates);

        List<CleanupTarget> targets = new ArrayList<>();
        for (String fileName : noDerivatives) {
            for (String version : DestinationCardThumbnails.CACHE_VERSIONS) {
                for (Variant variant : Variant.values()) {
                    Path cached = DestinationCardThumbnails.cacheDirectory(uploadRoot, version, variant.width())
                            .resolve(fileName).normalize();
                    if (isSafeTarget(cached, version, variant.width(), fileName)) {
                        targets.add(new CleanupTarget(version, variant.width(), fileName,
                                uploadRoot.relativize(cached).toString(), size(cached)));
                    }
                }
            }
        }
        return new CleanupPlan(noDerivatives.size(), List.copyOf(targets));
    }

    /**
     * 실제 삭제. 같은 계산을 다시 해 확인한 dry-run 결과와 다르면 아무것도 지우지 않는다.
     * 지우기 직전에 경로를 한 번 더 확인하고, 끝난 뒤 대상이 사라졌는지와 원본이 그대로인지 확인한다.
     */
    public CleanupResult execute(int expectedFileCount, long expectedTotalBytes) {
        CleanupPlan plan = plan();
        if (plan.fileCount() != expectedFileCount || plan.totalBytes() != expectedTotalBytes) {
            throw new PlanChangedException(plan);
        }

        List<String> originalsBefore = existingOriginals(plan);
        List<CleanupTarget> deleted = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (CleanupTarget target : plan.targets()) {
            Path cached = uploadRoot.resolve(target.relativePath()).normalize();
            try {
                if (!isSafeTarget(cached, target.version(), target.width(), target.fileName())) {
                    failed.add(target.relativePath());
                    continue;
                }
                if (Files.deleteIfExists(cached)) {
                    deleted.add(target);
                }
            } catch (IOException | RuntimeException failure) {
                failed.add(target.relativePath());
                log.warn("No-derivatives thumbnail could not be removed: path={}, failureType={}",
                        target.relativePath(), failure.getClass().getSimpleName());
            }
        }

        boolean targetsGone = plan.targets().stream()
                .noneMatch(target -> Files.exists(uploadRoot.resolve(target.relativePath()), LinkOption.NOFOLLOW_LINKS));
        boolean originalsKept = originalsBefore.stream()
                .allMatch(fileName -> Files.isRegularFile(DestinationCardThumbnails.originalDirectory(uploadRoot)
                        .resolve(fileName)));
        log.info("No-derivatives thumbnail cleanup finished: deleted={}, failed={}, targetsGone={}, originalsKept={}",
                deleted.size(), failed.size(), targetsGone, originalsKept);
        return new CleanupResult(plan, List.copyOf(deleted), List.copyOf(failed), targetsGone, originalsKept);
    }

    /** 원본 폴더와 모든 캐시 폴더에 있는 여행지 사진 파일 이름. 원본이 지워진 뒤 남은 캐시도 찾는다. */
    private List<String> candidateFileNames() {
        Set<String> names = new TreeSet<>(listFileNames(DestinationCardThumbnails.originalDirectory(uploadRoot)));
        for (String version : DestinationCardThumbnails.CACHE_VERSIONS) {
            for (Variant variant : Variant.values()) {
                names.addAll(listFileNames(
                        DestinationCardThumbnails.cacheDirectory(uploadRoot, version, variant.width())));
            }
        }
        return List.copyOf(names);
    }

    private List<String> listFileNames(Path directory) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.getFileName().toString())
                    .filter(DestinationCardThumbnails::isFileName)
                    .toList();
        } catch (IOException failure) {
            throw new IllegalStateException("썸네일 정리 대상을 읽지 못했습니다: " + uploadRoot.relativize(directory),
                    failure);
        }
    }

    /**
     * 지워도 되는 캐시 파일인지. 아래를 모두 만족해야 한다.
     * <ul>
     *   <li>파일 이름이 여행지 사진 규칙에 맞고, 계산한 위치가 {@code 캐시/버전/폭/파일 이름} 과 정확히 같다</li>
     *   <li>알려진 규칙 버전(v1·v2)과 폭(480·960)의 캐시 폴더 바로 아래다</li>
     *   <li>실제 경로(심볼릭 링크를 따라간 뒤)도 업로드 폴더의 캐시 폴더 안이다</li>
     *   <li>링크가 아닌 일반 파일이다</li>
     * </ul>
     */
    boolean isSafeTarget(Path cached, String version, int width, String fileName) {
        if (!DestinationCardThumbnails.isFileName(fileName)
                || !DestinationCardThumbnails.CACHE_VERSIONS.contains(version)
                || Variant.ofWidth(width).isEmpty()) {
            return false;
        }
        Path cacheRoot = uploadRoot.resolve(DestinationCardThumbnails.CACHE_DIRECTORY).normalize();
        Path expectedDirectory = DestinationCardThumbnails.cacheDirectory(uploadRoot, version, width);
        Path normalized = cached.toAbsolutePath().normalize();
        if (!normalized.equals(expectedDirectory.resolve(fileName))
                || !normalized.startsWith(cacheRoot)
                || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try {
            Path realCacheRoot = cacheRoot.toRealPath();
            if (!realCacheRoot.startsWith(uploadRoot.toRealPath())) {
                return false;
            }
            Path realExpectedDirectory = realCacheRoot.resolve(version).resolve(String.valueOf(width));
            return normalized.getParent().toRealPath().equals(realExpectedDirectory);
        } catch (IOException failure) {
            return false;
        }
    }

    private List<String> existingOriginals(CleanupPlan plan) {
        Path originals = DestinationCardThumbnails.originalDirectory(uploadRoot);
        return plan.targets().stream()
                .map(CleanupTarget::fileName)
                .distinct()
                .filter(fileName -> Files.isRegularFile(originals.resolve(fileName)))
                .toList();
    }

    private long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException failure) {
            return 0;
        }
    }

    /** 지울 캐시 파일 하나. {@code relativePath} 는 업로드 폴더 기준 위치다. */
    public record CleanupTarget(String version, int width, String fileName, String relativePath, long bytes) {

        public String variantKey() {
            return version + "/" + width;
        }
    }

    /**
     * dry-run 결과.
     *
     * @param noDerivativeOriginalCount 원본·캐시 폴더에 있는 파일 중 DB 에서 제3유형으로 판정된 원본 수
     */
    public record CleanupPlan(int noDerivativeOriginalCount, List<CleanupTarget> targets) {

        public int fileCount() {
            return targets.size();
        }

        public long totalBytes() {
            return targets.stream().mapToLong(CleanupTarget::bytes).sum();
        }

        public boolean isEmpty() {
            return targets.isEmpty();
        }

        /** 버전/폭별 파일 수. 대상이 없는 칸도 0 으로 보여 준다. */
        public Map<String, Long> countsByVariant() {
            Map<String, Long> counts = new LinkedHashMap<>();
            for (String version : DestinationCardThumbnails.CACHE_VERSIONS) {
                Arrays.stream(Variant.values()).forEach(variant -> counts.put(version + "/" + variant.width(), 0L));
            }
            targets.forEach(target -> counts.merge(target.variantKey(), 1L, Long::sum));
            return Collections.unmodifiableMap(counts);
        }
    }

    /** 실제 삭제 결과와 사후 확인. */
    public record CleanupResult(CleanupPlan plan, List<CleanupTarget> deleted, List<String> failed,
                                boolean targetsGone, boolean originalsKept) {

        public long deletedBytes() {
            return deleted.stream().mapToLong(CleanupTarget::bytes).sum();
        }
    }

    /** 확인한 dry-run 이후 대상이 바뀌었다. 아무것도 지우지 않았으니 dry-run 을 다시 봐야 한다. */
    public static class PlanChangedException extends RuntimeException {

        private final transient CleanupPlan currentPlan;

        public PlanChangedException(CleanupPlan currentPlan) {
            super("dry-run 이후 정리 대상이 바뀌었습니다. 아무것도 지우지 않았습니다. dry-run 을 다시 확인해 주세요.");
            this.currentPlan = currentPlan;
        }

        public CleanupPlan getCurrentPlan() {
            return currentPlan;
        }
    }
}
