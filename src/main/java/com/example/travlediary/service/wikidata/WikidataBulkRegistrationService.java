package com.example.travlediary.service.wikidata;

import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.dto.wikidata.WikidataDestinationCandidate;
import com.example.travlediary.dto.wikidata.WikidataDestinationPreview;
import com.example.travlediary.dto.wikidata.WikipediaDescriptionPreview;
import com.example.travlediary.model.DestinationSeason;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.service.destination.DestinationSaveOrchestrationService;
import com.example.travlediary.service.destination.DestinationService;
import com.example.travlediary.service.destination.DuplicateWikidataDestinationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 해외 여행지(Wikidata) 일괄 등록: 검색 → 여행지별 검토 → 여행지별 등록.
 *
 * <p>등록은 한 번에 한 여행지씩 받아 기존 단건 등록 경로({@link DestinationSaveOrchestrationService})를 그대로 부른다.
 * 따라서 Wikidata·Wikipedia·Commons 재검증, 사진 다운로드 보안, 한 여행지 안의 번역·출처·이미지 원자 저장과
 * 실패 시 내려받은 파일 정리는 단건 등록과 같다. 여행지마다 독립된 요청·트랜잭션이라 한 곳의 실패가 다른 여행지를 취소하지 않는다.</p>
 *
 * <p>외부 요청이 한꺼번에 몰리지 않도록 서버 전체에서 동시에 진행하는 등록 수를
 * {@value #MAX_CONCURRENT_REGISTRATIONS}개로 묶는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikidataBulkRegistrationService {
    static final int MAX_CONCURRENT_REGISTRATIONS = 2;
    /** 앞선 등록을 기다리는 최대 시간. 넘으면 실패로 돌려 관리자가 다시 시도하게 한다. */
    static final int REGISTRATION_WAIT_SECONDS = 60;
    private static final Pattern QID = Pattern.compile("Q[1-9][0-9]{0,14}");

    private final WikidataDestinationService wikidataDestinationService;
    private final WikipediaDescriptionService wikipediaDescriptionService;
    private final WikidataDestinationFormBuilder formBuilder;
    private final DestinationService destinationService;
    private final DestinationSaveOrchestrationService orchestrationService;
    private final WikidataRegionExplorer regionExplorer;
    private final ExternalApiRateLimiter rateLimiter;
    private final Semaphore registrations = new Semaphore(MAX_CONCURRENT_REGISTRATIONS, true);

    public record Candidate(String qid, String name, String shortDescription, String country, String region,
                            String imageUrl, boolean registered) {
    }

    /**
     * @param excludedCount 장소가 아니어서(좌표·국가·소재지 없음) 뺀 검색 결과 수
     * @param nextOffset    '검색 결과 더 보기' 위치. 더 없으면 null
     */
    public record SearchResult(List<Candidate> candidates, int excludedCount, Integer nextOffset) {
    }

    /**
     * 검토 화면에 보여줄 여행지별 초안.
     *
     * @param names              저장될 언어별 제목
     * @param autoRegionId       자동 매핑이 확정된 지역. null 이면 관리자가 골라야 한다
     * @param regionPath         자동 매핑된 지역 경로 이름
     * @param wikipediaLanguages Wikipedia 상세 설명을 넣을 언어
     */
    public record Review(String qid, boolean registered, Map<String, String> names, String shortDescription,
                         String country, Long countryId, Long autoRegionId, List<String> regionPath,
                         String regionMessage, List<String> wikipediaLanguages, String homepageUrl,
                         String contactNumber, List<String> notes) {
    }

    /** 여행지 한 곳의 등록 요청. regionId 가 없으면 자동 매핑된 지역을 쓴다. */
    public record RegisterRequest(String qid, String type, String season, Long regionId, String koreanName,
                                  String photoFileName) {
    }

    /**
     * status: SUCCESS · DUPLICATE(이미 등록됨) · FAILED(다시 해도 같은 결과인 오류) ·
     * RATE_LIMITED(외부 API 요청 제한. 아무것도 저장하지 않았고 retryAfterSeconds 뒤 다시 보낼 수 있다)
     */
    public record ItemResult(String qid, String status, Long destinationId, String message,
                             Integer retryAfterSeconds, String service) {
        public ItemResult(String qid, String status, Long destinationId, String message) {
            this(qid, status, destinationId, message, null, null);
        }

        static ItemResult success(String qid, Long destinationId) {
            return new ItemResult(qid, "SUCCESS", destinationId, null);
        }

        static ItemResult duplicate(String qid, Long destinationId) {
            return new ItemResult(qid, "DUPLICATE", destinationId, "이미 등록된 여행지입니다.");
        }

        static ItemResult failed(String qid, String message) {
            return new ItemResult(qid, "FAILED", null, message);
        }

        static ItemResult rateLimited(String qid, ExternalApiRateLimiter.Service service, Duration wait) {
            int seconds = (int) Math.max(1, Math.min(Integer.MAX_VALUE, (wait.toMillis() + 999) / 1000));
            return new ItemResult(qid, "RATE_LIMITED", null, RateLimitMessages.message(service, wait),
                    seconds, service.label());
        }
    }

    /** 검색 한 페이지. 장소로 확인된 후보만 남기고 이미 등록된 QID를 표시한다. */
    public SearchResult search(String keyword, int offset) {
        WikidataDestinationService.QuickSearchPage page = wikidataDestinationService.quickSearchPage(keyword, offset);
        List<String> qids = page.candidates().stream().map(WikidataDestinationCandidate::qid).toList();
        if (qids.isEmpty()) return new SearchResult(List.of(), 0, page.nextOffset());
        Map<String, WikidataDestinationCandidate> quick = new LinkedHashMap<>();
        page.candidates().forEach(candidate -> quick.put(candidate.qid(), candidate));
        List<Candidate> candidates = candidates(qids, quick);
        return new SearchResult(candidates, qids.size() - candidates.size(), page.nextOffset());
    }

    /**
     * 지역별 탐색의 한 페이지({@value #REGION_PAGE_SIZE}곳). 표시 정보·장소 확인·등록 여부는 키워드 검색과 같은 방식으로 채운다.
     *
     * @param region 매핑된 Wikidata 행정구역과 근거
     * @param total  이 지역에서 찾은 여행 관련 장소 수(상한 {@value WikidataRegionExplorer#RESULT_LIMIT})
     */
    public record RegionSearchResult(WikidataRegionExplorer.Resolution region, String areaQid,
                                     List<Candidate> candidates, int excludedCount, Integer nextOffset,
                                     int total, boolean limited) {
    }

    static final int REGION_PAGE_SIZE = 20;

    public WikidataRegionExplorer.Resolution resolveRegion(Long regionId) {
        return regionExplorer.resolve(regionId);
    }

    public RegionSearchResult regionSearch(Long regionId, String cityQid, int offset) {
        WikidataRegionExplorer.Places found = regionExplorer.places(regionId, cityQid);
        List<String> all = found.qids();
        if (offset < 0 || offset > all.size()) throw new IllegalArgumentException("여행지 목록 위치가 올바르지 않습니다.");
        List<String> page = all.subList(offset, Math.min(offset + REGION_PAGE_SIZE, all.size()));
        List<Candidate> candidates = page.isEmpty() ? List.of() : candidates(page, Map.of());
        Integer next = offset + REGION_PAGE_SIZE < all.size() ? offset + REGION_PAGE_SIZE : null;
        return new RegionSearchResult(regionExplorer.resolve(regionId), found.areaQid(), candidates,
                page.size() - candidates.size(), next, all.size(), found.limited());
    }

    /**
     * 표시용 후보. 이름·설명은 기존 다국어 대체 규칙(한국어 → 영어 …)으로, 장소 여부와 국가·소재지·이미지는
     * 기존 검색 상세 조회로 채운다. 한 번에 조회할 수 있는 수만큼 나눠 부른다.
     */
    private List<Candidate> candidates(List<String> qids, Map<String, WikidataDestinationCandidate> quick) {
        List<WikidataDestinationCandidate> places = new ArrayList<>();
        for (int start = 0; start < qids.size(); start += DETAILS_CHUNK) {
            places.addAll(wikidataDestinationService.searchDetails(
                    qids.subList(start, Math.min(start + DETAILS_CHUNK, qids.size()))));
        }
        Set<String> registered = destinationService.findRegisteredWikidataQids(qids);
        List<Candidate> candidates = new ArrayList<>();
        for (WikidataDestinationCandidate place : places) {
            WikidataDestinationCandidate fallback = quick.get(place.qid());
            candidates.add(new Candidate(place.qid(),
                    firstText(place.name(), fallback == null ? null : fallback.name()),
                    firstText(place.shortDescription(), fallback == null ? null : fallback.shortDescription()),
                    place.country(), place.region(), place.imageUrl(), registered.contains(place.qid())));
        }
        return List.copyOf(candidates);
    }

    /** 검색 상세 조회가 한 번에 받는 QID 수. */
    private static final int DETAILS_CHUNK = 10;

    /** 검토용 초안. 자동입력 캐시를 쓰므로 검색·단건 화면에서 이미 받은 값은 다시 받지 않는다. */
    public Review review(String qid) {
        String normalized = normalizeQid(qid);
        Long existing = destinationService.findWikidataDestinationId(normalized);
        WikidataDestinationPreview preview = wikidataDestinationService.previewForAutofill(normalized);
        WikipediaDescriptionPreview wikipedia = wikipediaDescriptionService.previewForAutofill(normalized);
        WikidataDestinationFormBuilder.Draft draft = formBuilder.draft(preview, wikipedia);
        Map<String, String> names = new LinkedHashMap<>();
        for (int index = 0; index < WikidataDestinationFormBuilder.LANGUAGES.size(); index++) {
            String name = draft.form().getTranslations().get(index).getName();
            if (name != null && !name.isBlank()) names.put(WikidataDestinationFormBuilder.LANGUAGES.get(index), name);
        }
        var match = preview.regionMatch();
        List<String> regionPath = draft.autoRegionId() == null || match == null || match.path() == null ? List.of()
                : match.path().stream().map(WikidataDestinationPreview.RegionMatch.RegionPathItem::regionName).toList();
        var travelInfo = preview.travelInfo();
        return new Review(normalized, existing != null, names,
                draft.form().getTranslations().get(0).getShortDescription(), preview.country(),
                match == null ? null : match.countryId(), draft.autoRegionId(), regionPath,
                match == null ? null : match.message(), draft.wikipediaLanguages(),
                travelInfo == null ? null : travelInfo.homepageUrl(),
                travelInfo == null ? null : travelInfo.contactNumber(), draft.notes());
    }

    /**
     * 여행지 한 곳을 기존 단건 등록 경로로 등록한다. 실패는 예외 대신 결과로 돌려 다른 여행지 진행을 막지 않는다.
     */
    public ItemResult register(RegisterRequest request, Long userId) {
        String qid;
        try {
            qid = normalizeQid(request.qid());
        } catch (IllegalArgumentException exception) {
            return ItemResult.failed(request.qid(), exception.getMessage());
        }
        Long existing = destinationService.findWikidataDestinationId(qid);
        if (existing != null) return ItemResult.duplicate(qid, existing);
        DestinationType type = parse(DestinationType.class, request.type());
        DestinationSeason season = parse(DestinationSeason.class, request.season());
        if (type == null || season == null) {
            return ItemResult.failed(qid, (type == null ? "유형" : "시즌") + "을 선택해 주세요.");
        }
        // 이 등록에 필요한 외부 서비스가 이미 요청 제한으로 대기 중이면 호출하지 않고 바로 돌려준다.
        // 다른 여행지가 같은 API를 계속 불러 제한을 늘리지 않게 한다. 사진이 없으면 Commons 대기는 보지 않는다.
        ItemResult blocked = blockedByRateLimit(qid, request.photoFileName() != null && !request.photoFileName().isBlank());
        if (blocked != null) return blocked;

        boolean acquired;
        try {
            acquired = registrations.tryAcquire(REGISTRATION_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ItemResult.failed(qid, "등록을 기다리는 중 중단되었습니다. 다시 시도해 주세요.");
        }
        if (!acquired) return ItemResult.failed(qid, "다른 등록이 오래 진행 중입니다. 잠시 후 다시 시도해 주세요.");
        try {
            WikidataDestinationPreview preview = wikidataDestinationService.previewForAutofill(qid);
            WikipediaDescriptionPreview wikipedia = wikipediaDescriptionService.previewForAutofill(qid);
            DestinationForm form = formBuilder.form(preview, wikipedia, new WikidataDestinationFormBuilder.Choices(
                    type, season.name(), request.regionId(), request.koreanName(), request.photoFileName()));
            if (form.getRegionId() == null) return ItemResult.failed(qid, "지역을 선택해 주세요.");
            orchestrationService.registerDestination(form, userId, List.of());
            return ItemResult.success(qid, destinationService.findWikidataDestinationId(qid));
        } catch (DuplicateWikidataDestinationException exception) {
            return ItemResult.duplicate(qid, destinationService.findWikidataDestinationId(qid));
        } catch (DuplicateKeyException exception) {
            Long registered = destinationService.findWikidataDestinationId(qid);
            if (registered != null) return ItemResult.duplicate(qid, registered);
            log.warn("해외 일괄 등록 실패 (qid={}, 원인=중복 키)", qid);
            return ItemResult.failed(qid, "여행지를 저장하지 못했습니다. 다시 시도해 주세요.");
        } catch (RuntimeException exception) {
            // 요청 제한은 원인 예외 안쪽에 있어도 일시적인 실패로 돌려준다. 저장 전(외부 재검증 단계)에만 일어나므로
            // 이번 시도에서는 아무것도 저장되지 않았고, 내려받은 사진은 등록 경로가 이미 지웠다.
            var limited = ExternalApiRateLimiter.rateLimitOf(exception);
            if (limited.isPresent()) {
                ExternalApiRateLimiter.Service service = limited.get().service();
                Duration wait = longer(limited.get().retryAfter(), rateLimiter.remaining(service));
                return ItemResult.rateLimited(qid, service, wait);
            }
            if (exception instanceof IllegalArgumentException || exception instanceof NoSuchElementException
                    || exception instanceof WikidataApiException || exception instanceof WikipediaApiException
                    || exception instanceof CommonsApiException || exception instanceof CommonsPhotoDownloadException) {
                return ItemResult.failed(qid, exception.getMessage());
            }
            log.warn("해외 일괄 등록 실패 (qid={}, 원인={})", qid, exception.getClass().getSimpleName());
            return ItemResult.failed(qid, "여행지를 저장하지 못했습니다. 다시 시도해 주세요.");
        } finally {
            registrations.release();
        }
    }

    private ItemResult blockedByRateLimit(String qid, boolean usesCommons) {
        List<ExternalApiRateLimiter.Service> services = usesCommons
                ? List.of(ExternalApiRateLimiter.Service.WIKIDATA, ExternalApiRateLimiter.Service.WIKIPEDIA,
                ExternalApiRateLimiter.Service.COMMONS)
                : List.of(ExternalApiRateLimiter.Service.WIKIDATA, ExternalApiRateLimiter.Service.WIKIPEDIA);
        ExternalApiRateLimiter.Service longest = null;
        Duration wait = Duration.ZERO;
        for (ExternalApiRateLimiter.Service service : services) {
            Duration remaining = rateLimiter.remaining(service);
            if (remaining.compareTo(wait) > 0) {
                wait = remaining;
                longest = service;
            }
        }
        return longest == null ? null : ItemResult.rateLimited(qid, longest, wait);
    }

    private static Duration longer(Duration left, Duration right) {
        if (left == null) return right;
        return left.compareTo(right) >= 0 ? left : right;
    }

    private String normalizeQid(String qid) {
        String normalized = qid == null ? "" : qid.strip().toUpperCase();
        if (!QID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("올바른 Wikidata QID가 아닙니다.");
        }
        return normalized;
    }

    private <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value.strip());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
