package com.tripbora.service.destination;

import com.tripbora.model.CountryCategory;
import com.tripbora.model.Destination;
import com.tripbora.model.DestinationImage;
import com.tripbora.model.DestinationImageCommonsSource;
import com.tripbora.model.DestinationTranslation;
import com.tripbora.repository.destination.DestinationMapper;
import com.tripbora.service.category.CountryCategoryService;
import com.tripbora.service.kto.InvalidKtoPhotoUrlException;
import com.tripbora.service.kto.KtoDownloadedPhoto;
import com.tripbora.service.kto.KtoPhotoDownloadException;
import com.tripbora.service.kto.KtoPhotoDownloadService;
import com.tripbora.service.kto.PhotoDownloadRateLimitedException;
import com.tripbora.service.pixabay.PixabayApiException;
import com.tripbora.service.pixabay.PixabayHit;
import com.tripbora.service.pixabay.PixabayImageSearchService;
import com.tripbora.service.pixabay.PixabayImageSearchService.PixabaySearchResult;
import com.tripbora.service.pixabay.PixabayPhotoException;
import com.tripbora.service.pixabay.PreparedPixabayPhoto;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 관리자 여행지 이미지 관리 화면의 Pixabay 스톡 사진(국내·해외 공통).
 *
 * <p>검색 결과는 화면에 잠시 보여 주기만 하고, 관리자가 고른 사진만 서버에 내려받아 기존 여행지 이미지와 같은
 * 저장소·테이블에 저장한다. 저장 요청은 Pixabay 사진 ID만 받는다. 내려받을 URL·원본 페이지·작가는 서버가 최근
 * 24시간 안에 검색으로 받은 값에서 찾고, 검색한 적 없는 ID는 거부한다.</p>
 */
@Slf4j
@Service
public class DestinationPixabayImageManagementService {

    /** 한 번에 추가할 수 있는 사진 수. 저장 요청 안에서 차례로 내려받으므로 너무 크게 두지 않는다. */
    public static final int MAX_SELECTION = 20;
    static final String SOURCE_NAME = "Pixabay";
    static final String LICENSE_TYPE = "PIXABAY_CONTENT_LICENSE";
    static final String LICENSE_NAME = "Pixabay Content License";
    static final String LICENSE_URL = "https://pixabay.com/service/license-summary/";
    private static final int MAX_PARALLEL_DOWNLOADS = 3;

    private final PixabayImageSearchService searchService;
    private final KtoPhotoDownloadService downloadService;
    private final DestinationSavePersistenceService persistenceService;
    private final DestinationImageService destinationImageService;
    private final DestinationCommonsImageManagementService commonsImageManagementService;
    private final DestinationMapper destinationMapper;
    private final CountryCategoryService countryCategoryService;
    private final Clock clock;
    private final Executor executor;
    private final ExecutorService ownedExecutor;

    @Autowired
    public DestinationPixabayImageManagementService(PixabayImageSearchService searchService,
                                                    KtoPhotoDownloadService downloadService,
                                                    DestinationSavePersistenceService persistenceService,
                                                    DestinationImageService destinationImageService,
                                                    DestinationCommonsImageManagementService commonsImageManagementService,
                                                    DestinationMapper destinationMapper,
                                                    CountryCategoryService countryCategoryService) {
        this(searchService, downloadService, persistenceService, destinationImageService,
                commonsImageManagementService, destinationMapper, countryCategoryService,
                Clock.systemDefaultZone(), null);
    }

    DestinationPixabayImageManagementService(PixabayImageSearchService searchService,
                                             KtoPhotoDownloadService downloadService,
                                             DestinationSavePersistenceService persistenceService,
                                             DestinationImageService destinationImageService,
                                             DestinationCommonsImageManagementService commonsImageManagementService,
                                             DestinationMapper destinationMapper,
                                             CountryCategoryService countryCategoryService,
                                             Clock clock, Executor executor) {
        this.searchService = searchService;
        this.downloadService = downloadService;
        this.persistenceService = persistenceService;
        this.destinationImageService = destinationImageService;
        this.commonsImageManagementService = commonsImageManagementService;
        this.destinationMapper = destinationMapper;
        this.countryCategoryService = countryCategoryService;
        this.clock = clock;
        if (executor == null) {
            this.ownedExecutor = Executors.newFixedThreadPool(MAX_PARALLEL_DOWNLOADS, task -> {
                Thread thread = new Thread(task, "pixabay-photo-import");
                thread.setDaemon(true);
                return thread;
            });
            this.executor = ownedExecutor;
        } else {
            this.ownedExecutor = null;
            this.executor = executor;
        }
    }

    public boolean isConfigured() {
        return searchService.isConfigured();
    }

    /**
     * 기본 검색어. Pixabay 는 영어 검색 결과가 훨씬 많아 국내·해외 모두 저장된 영문 번역을 먼저 쓴다.
     * 영문 여행지명이 있으면 영문 여행지명 + 영문 도시/지역 + 영문 국가로 만들고, 영문 이름이 없는 지역은 건너뛴다
     * (예: Gyeongbokgung Palace Seoul South Korea, Eiffel Tower Paris France).
     * 영문 여행지명이 없을 때만 한국어 여행지명 + 한국어 지역명(국내는 서울 같은 지역, 해외는 도시 + 국가)으로 검색한다.
     */
    public String defaultSearchQuery(Long destinationId) {
        List<DestinationTranslation> translations = destinationMapper.findTranslationsByDestinationId(destinationId);
        List<CountryCategory> path = regionPath(destinationId);
        boolean overseas = commonsImageManagementService.isOverseasDestination(destinationId);
        List<String> parts = new ArrayList<>();
        String englishName = translationName(translations, "en");
        if (englishName != null) {
            parts.add(englishName);
            for (CountryCategory region : searchRegions(path, overseas, true)) addRegion(parts, region.getNameEn());
        } else {
            String koreanName = translationName(translations, "ko");
            if (koreanName != null) parts.add(koreanName);
            // 한국어 fallback 은 기존 규칙 그대로: 국내는 '대한민국'을 붙이지 않고, 해외는 도시 + 국가를 붙인다.
            for (CountryCategory region : searchRegions(path, overseas, overseas)) addRegion(parts, region.getRegionName());
        }
        String query = String.join(" ", parts).strip().replaceAll("\\s+", " ");
        // Pixabay 검색어 한도(100자)를 넘으면 단어 경계에서 자른다.
        while (query.length() > 100 && query.contains(" ")) {
            query = query.substring(0, query.lastIndexOf(' ')).strip();
        }
        return query.length() > 100 ? query.substring(0, 100) : query;
    }

    /** 이 여행지에 이미 저장된 Pixabay 사진 ID. */
    public Set<String> registeredPixabayIds(List<DestinationImage> images) {
        return images.stream()
                .filter(DestinationImage::isPixabayImage)
                .map(DestinationImage::getExternalContentId)
                .filter(Objects::nonNull)
                .map(String::strip)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 검색 결과 한 묶음. 이미 이 여행지에 저장된 사진은 registered 로 표시한다. */
    public PhotoPage search(Long destinationId, String query, int offset) {
        PixabaySearchResult result = searchService.search(query, offset);
        Set<String> registered = registeredPixabayIds(destinationImageService.getImages(destinationId));
        List<Photo> photos = result.hits().stream()
                .map(hit -> new Photo(hit.id(), hit.pageUrl(), hit.thumbnailUrl(), hit.imageWidth(),
                        hit.imageHeight(), hit.user(), hit.tags(), registered.contains(String.valueOf(hit.id()))))
                .toList();
        return new PhotoPage(result.query(), result.offset(), result.totalHits(), photos, result.nextOffset(),
                MAX_SELECTION);
    }

    /**
     * 고른 Pixabay 사진만 내려받아 저장한다. 전부 저장하거나 하나도 저장하지 않는다.
     * 여행지에 대표 사진이 없으면 첫 사진을 대표로 둔다(기존 대표 사진은 바꾸지 않는다).
     *
     * @return 추가한 사진 수
     */
    public int addPhotos(Long destinationId, List<Long> pixabayIds) {
        if (!searchService.isConfigured()) {
            throw PixabayApiException.notConfigured();
        }
        List<Long> ids = selection(pixabayIds);
        List<DestinationImage> images = destinationImageService.getImages(destinationId);
        Set<String> registered = registeredPixabayIds(images);
        List<String> duplicates = ids.stream().map(String::valueOf).filter(registered::contains).toList();
        if (!duplicates.isEmpty()) {
            throw new PixabayPhotoException("이미 이 여행지에 등록된 Pixabay 사진입니다: ID "
                    + String.join(", ", duplicates));
        }

        List<PixabayHit> hits = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (Long id : ids) {
            searchService.findSearchedHit(id).ifPresentOrElse(hits::add, () -> unknown.add(String.valueOf(id)));
        }
        if (!unknown.isEmpty()) {
            throw new PixabayPhotoException("최근 검색 결과에서 확인할 수 없는 Pixabay 사진입니다(ID "
                    + String.join(", ", unknown) + "). 다시 검색한 뒤 선택해 주세요.");
        }

        boolean hasMain = images.stream().anyMatch(image -> Boolean.TRUE.equals(image.getIsMain()));
        List<PreparedPixabayPhoto> prepared = download(hits, !hasMain);
        try {
            persistenceService.addPixabayPhotosToExistingDestination(destinationId, prepared);
        } catch (RuntimeException exception) {
            cleanup(prepared);
            throw exception;
        }
        return prepared.size();
    }

    private List<Long> selection(List<Long> pixabayIds) {
        if (pixabayIds == null || pixabayIds.isEmpty()) {
            throw new PixabayPhotoException("추가할 Pixabay 사진을 선택해 주세요.");
        }
        if (pixabayIds.size() > MAX_SELECTION) {
            throw new PixabayPhotoException("Pixabay 사진은 한 번에 최대 " + MAX_SELECTION + "장까지 추가할 수 있습니다.");
        }
        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : pixabayIds) {
            if (id == null || id <= 0) {
                throw new PixabayPhotoException("선택한 Pixabay 사진 정보가 올바르지 않습니다. 다시 검색해 주세요.");
            }
            if (!unique.add(id)) {
                throw new PixabayPhotoException("같은 Pixabay 사진을 두 번 선택했습니다: ID " + id);
            }
        }
        return List.copyOf(unique);
    }

    /**
     * 동시에 최대 {@value #MAX_PARALLEL_DOWNLOADS}장씩 내려받는다. 하나라도 실패하면 나머지가 끝나기를 기다린 뒤
     * 이번에 받은 파일을 모두 지운다. 요청 한도(429)는 다시 시도하지 않고 바로 알린다.
     */
    private List<PreparedPixabayPhoto> download(List<PixabayHit> hits, boolean assignDefaultMain) {
        LocalDateTime checkedAt = LocalDateTime.now(clock);
        List<CompletableFuture<PreparedPixabayPhoto>> downloads = new ArrayList<>();
        for (int index = 0; index < hits.size(); index++) {
            PixabayHit hit = hits.get(index);
            boolean main = assignDefaultMain && index == 0;
            downloads.add(CompletableFuture.supplyAsync(() -> downloadOne(hit, main, checkedAt), executor));
        }
        List<PreparedPixabayPhoto> prepared = new ArrayList<>();
        RuntimeException firstFailure = null;
        for (CompletableFuture<PreparedPixabayPhoto> download : downloads) {
            try {
                prepared.add(join(download));
            } catch (RuntimeException exception) {
                if (firstFailure == null || (exception instanceof PixabayApiException
                        && !(firstFailure instanceof PixabayApiException))) {
                    firstFailure = exception;
                }
            }
        }
        if (firstFailure != null) {
            cleanup(prepared);
            throw firstFailure;
        }
        return List.copyOf(prepared);
    }

    /**
     * 응답에 있는 가장 좋은 판(imageURL → fullHDURL → largeImageURL)부터 받는다. 크기·형식 문제로 받지 못하면
     * 다음 판으로 한 번씩만 넘어간다. 요청 한도는 더 시도하지 않는다.
     */
    private PreparedPixabayPhoto downloadOne(PixabayHit hit, boolean main, LocalDateTime checkedAt) {
        String lastReason = "내려받을 이미지 주소가 없습니다.";
        for (PixabayHit.DownloadCandidate candidate : hit.downloadCandidates()) {
            try {
                KtoDownloadedPhoto downloaded = downloadService.downloadPixabayImage(candidate.url());
                return new PreparedPixabayPhoto(downloaded.localImageUrl(), main, source(hit, candidate, checkedAt));
            } catch (PhotoDownloadRateLimitedException exception) {
                throw PixabayApiException.rateLimited();
            } catch (InvalidKtoPhotoUrlException exception) {
                lastReason = "허용되지 않은 이미지 주소";
            } catch (KtoPhotoDownloadException exception) {
                lastReason = exception.reason() == null ? "다운로드 실패" : exception.reason();
            }
        }
        throw new PixabayPhotoException("Pixabay 사진을 내려받지 못했습니다(ID " + hit.id() + ", " + lastReason
                + "). 잠시 후 다시 시도하거나 이 사진을 빼고 추가해 주세요. 선택한 사진은 하나도 저장되지 않았습니다.");
    }

    private DestinationImageCommonsSource source(PixabayHit hit, PixabayHit.DownloadCandidate candidate,
                                                 LocalDateTime checkedAt) {
        DestinationImageCommonsSource source = new DestinationImageCommonsSource();
        source.setSourceName(SOURCE_NAME);
        source.setExternalContentId(String.valueOf(hit.id()));
        source.setSourceTitle(hit.tags());
        source.setAuthorText(hit.user());
        source.setWorkPageUrl(hit.pageUrl());
        source.setOriginalImageUrl(candidate.url());
        // Pixabay Content License 는 CC0 가 아니다. 라이선스 이름·요약 페이지를 그대로 남긴다.
        source.setLicenseType(LICENSE_TYPE);
        source.setLicenseName(LICENSE_NAME);
        source.setLicenseUrl(LICENSE_URL);
        source.setAttributionText("사진: " + (hit.user() == null ? "작가 정보 없음" : hit.user()) + " / " + SOURCE_NAME);
        source.setLicenseEvidenceDetail("Pixabay API 검색 응답 · 사진 ID " + hit.id()
                + " · 저장 파일: " + candidate.field()
                + (hit.imageWidth() > 0 && hit.imageHeight() > 0
                ? " · 원본 크기 " + hit.imageWidth() + "x" + hit.imageHeight() : ""));
        source.setAttributionRequired(false);
        source.setChangesRequired(false);
        source.setShareAlikeRequired(false);
        source.setContentModified(false);
        source.setLicenseCheckedAt(checkedAt);
        return source;
    }

    private void cleanup(List<PreparedPixabayPhoto> photos) {
        for (PreparedPixabayPhoto photo : photos) {
            try {
                downloadService.deleteDownloadedPhoto(photo.localImageUrl());
            } catch (RuntimeException cleanupFailure) {
                log.warn("Pixabay 사진 등록 실패 파일을 정리하지 못했습니다. (원인: {})",
                        cleanupFailure.getClass().getSimpleName());
            }
        }
    }

    private static String translationName(List<DestinationTranslation> translations, String languageCode) {
        if (translations == null) return null;
        return translations.stream()
                .filter(translation -> languageCode.equals(translation.getLanguageCode()))
                .map(DestinationTranslation::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::strip)
                .findFirst()
                .orElse(null);
    }

    /**
     * 검색어에 붙일 지역(좁은 지역 → 국가 순). 해외는 depth 3 도시와 depth 2 국가,
     * 국내는 최상위(대한민국) 아래 첫 단계 지역(예: 서울)과 최상위 국가다.
     */
    private static List<CountryCategory> searchRegions(List<CountryCategory> path, boolean overseas, boolean includeCountry) {
        List<CountryCategory> regions = new ArrayList<>();
        if (overseas) {
            regionAtDepth(path, 3).ifPresent(regions::add);
            if (includeCountry) regionAtDepth(path, 2).ifPresent(regions::add);
        } else {
            if (path.size() > 1) regions.add(path.get(1));
            if (includeCountry && !path.isEmpty()) regions.add(path.get(0));
        }
        return regions;
    }

    private static Optional<CountryCategory> regionAtDepth(List<CountryCategory> path, int depth) {
        return path.stream().filter(region -> region.getDepth() != null && region.getDepth() == depth).findFirst();
    }

    /** 비어 있거나 이미 검색어에 들어 있는 지역 이름(예: 'Seoul Tower'의 Seoul)은 다시 붙이지 않는다. */
    private static void addRegion(List<String> parts, String regionName) {
        if (regionName == null || regionName.isBlank()) return;
        String name = regionName.strip();
        String lower = name.toLowerCase(Locale.ROOT);
        if (parts.stream().noneMatch(part -> part.toLowerCase(Locale.ROOT).contains(lower))) parts.add(name);
    }

    private List<CountryCategory> regionPath(Long destinationId) {
        Destination destination = destinationMapper.findById(destinationId);
        if (destination == null || destination.getRegionId() == null) return List.of();
        return countryCategoryService.getRegionPath(destination.getRegionId());
    }

    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            throw exception;
        }
    }

    @PreDestroy
    void stop() {
        if (ownedExecutor != null) ownedExecutor.shutdownNow();
    }

    /** 관리 화면 카드 한 장. 저장용 URL은 화면에 보내지 않는다. */
    public record Photo(long id, String pageUrl, String thumbnailUrl, int width, int height,
                        String user, String tags, boolean registered) {
    }

    /** nextOffset 이 null 이면 더 볼 사진이 없다. */
    public record PhotoPage(String query, int offset, int totalHits, List<Photo> photos, Integer nextOffset,
                            int selectionLimit) {
    }
}
