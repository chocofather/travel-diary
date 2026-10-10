package com.tripbora.service.pixabay;

import com.tripbora.service.wikidata.WikidataAutofillCache.ExpiringCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pixabay 검색과 24시간 캐시.
 *
 * <p>화면은 처음 30장, '사진 더 보기'마다 20장씩 보여 준다. API는 한 번에 50장(per_page)씩 받아 캐시에 두고,
 * 화면 위치(offset)에 필요한 API 페이지만 차례로 꺼내 쓴다. 그래서 처음 30장 뒤의 첫 '더 보기'(31~50번째)는
 * 외부 호출 없이 캐시에서, 다음 '더 보기'(51~70번째)에서 2페이지를 받는다. API 페이지를 합칠 때 같은 사진 ID는
 * 한 번만 남겨 화면에 같은 사진이 두 번 나오지 않게 한다.</p>
 *
 * <p>검색으로 받은 사진은 ID별로 기억해 둔다. 저장 요청은 사진 ID만 받고, 다운로드 URL·원본 페이지·작가는
 * 여기서 서버가 받은 값을 쓴다. 기억에 없거나 24시간이 지난 ID는 저장할 수 없다.</p>
 */
@Service
public class PixabayImageSearchService {

    public static final int FIRST_PAGE_SIZE = 30;
    public static final int MORE_PAGE_SIZE = 20;
    static final int API_PAGE_SIZE = 50;
    /** Pixabay API는 검색어 하나에 최대 500장까지만 돌려준다. */
    static final int MAX_RESULTS = 500;
    static final int MAX_QUERY_LENGTH = 100;
    /** Pixabay API 이용 조건: 검색 요청은 24시간 캐시해야 한다. 검색 응답의 이미지 URL도 이 기간만 쓴다. */
    static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final int MAX_CACHED_PAGES = 300;
    private static final int MAX_INDEXED_HITS = 10_000;
    private static final Pattern HANGUL = Pattern.compile("[\\uAC00-\\uD7A3\\u3131-\\u318E]");

    private final PixabayApiClient apiClient;
    private final Clock clock;
    private final ExpiringCache<PixabaySearchPage> pages;
    /** 검색으로 받은 사진(ID → 받은 시각과 값). 접근 순서로 오래된 것부터 밀어낸다. */
    private final LinkedHashMap<Long, PixabaySearchPage.IndexedHit> searchedHits =
            new LinkedHashMap<>(256, 0.75f, true);

    @Autowired
    public PixabayImageSearchService(PixabayApiClient apiClient) {
        this(apiClient, Clock.systemUTC());
    }

    PixabayImageSearchService(PixabayApiClient apiClient, Clock clock) {
        this.apiClient = apiClient;
        this.clock = clock;
        this.pages = new ExpiringCache<>(CACHE_TTL, MAX_CACHED_PAGES, clock);
    }

    public boolean isConfigured() {
        return apiClient.isConfigured();
    }

    /**
     * @param offset 이미 화면에 보인 사진 수. 0이면 처음 30장, 그 밖에는 이어서 20장을 준다.
     */
    public PixabaySearchResult search(String rawQuery, int offset) {
        String query = normalizeQuery(rawQuery);
        if (offset < 0 || offset >= MAX_RESULTS) {
            throw new IllegalArgumentException("더 불러올 수 있는 Pixabay 사진이 없습니다.");
        }
        if (!apiClient.isConfigured()) {
            throw PixabayApiException.notConfigured();
        }
        int limit = offset == 0 ? FIRST_PAGE_SIZE : MORE_PAGE_SIZE;
        int needed = Math.min(offset + limit, MAX_RESULTS);
        String language = HANGUL.matcher(query).find() ? "ko" : "en";

        List<PixabayHit> merged = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        int totalHits = 0;
        boolean exhausted = false;
        for (int page = 1; merged.size() < needed && !exhausted; page++) {
            int currentPage = page;
            PixabaySearchPage result = pages.get(cacheKey(query, language, currentPage),
                    () -> apiClient.searchPhotos(query, language, currentPage, API_PAGE_SIZE));
            remember(result);
            totalHits = result.totalHits();
            for (PixabayHit hit : result.hits()) {
                if (seen.add(hit.id())) merged.add(hit);
            }
            int reachable = Math.min(result.totalHits(), MAX_RESULTS);
            exhausted = result.hits().size() < API_PAGE_SIZE || page * API_PAGE_SIZE >= reachable;
        }

        List<PixabayHit> shown = offset >= merged.size() ? List.of()
                : merged.subList(offset, Math.min(merged.size(), offset + limit));
        int nextOffset = offset + shown.size();
        boolean more = !shown.isEmpty() && nextOffset < MAX_RESULTS
                && (merged.size() > nextOffset || !exhausted);
        return new PixabaySearchResult(query, offset, totalHits, List.copyOf(shown), more ? nextOffset : null);
    }

    /** 최근 24시간 안에 검색으로 받은 사진. 없으면 비어 있다(외부 호출 없음). */
    public Optional<PixabayHit> findSearchedHit(long id) {
        synchronized (searchedHits) {
            PixabaySearchPage.IndexedHit indexed = searchedHits.get(id);
            if (indexed == null) return Optional.empty();
            if (!clock.instant().isBefore(indexed.fetchedAt().plus(CACHE_TTL))) {
                searchedHits.remove(id);
                return Optional.empty();
            }
            return Optional.of(indexed.hit());
        }
    }

    /** 공백을 한 칸으로 줄인 검색어. 비었거나 100자를 넘거나 제어 문자가 있으면 거부한다. */
    static String normalizeQuery(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.strip().replaceAll("\\s+", " ");
        if (query.isEmpty()) {
            throw new IllegalArgumentException("검색어를 입력해 주세요.");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("Pixabay 검색어는 " + MAX_QUERY_LENGTH + "자 이하로 입력해 주세요.");
        }
        if (query.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("검색어에 사용할 수 없는 문자가 있습니다.");
        }
        return query;
    }

    /** 같은 검색어·페이지·per_page·검색 옵션이면 같은 키다. */
    private static String cacheKey(String query, String language, int page) {
        return String.join("|", "image_type=photo", "safesearch=true", "order=popular",
                "lang=" + language, "per_page=" + API_PAGE_SIZE, "page=" + page, "q=" + query);
    }

    /** 같은 사진을 더 늦게 받았으면 그 값(새 URL)으로 바꾼다. */
    private void remember(PixabaySearchPage page) {
        Instant fetchedAt = page.fetchedAt();
        synchronized (searchedHits) {
            for (PixabayHit hit : page.hits()) {
                PixabaySearchPage.IndexedHit current = searchedHits.get(hit.id());
                if (current == null || current.fetchedAt().isBefore(fetchedAt)) {
                    searchedHits.put(hit.id(), new PixabaySearchPage.IndexedHit(hit, fetchedAt));
                }
            }
            var eldest = searchedHits.entrySet().iterator();
            while (searchedHits.size() > MAX_INDEXED_HITS && eldest.hasNext()) {
                eldest.next();
                eldest.remove();
            }
        }
    }

    /** nextOffset 이 null 이면 더 볼 사진이 없다. */
    public record PixabaySearchResult(String query, int offset, int totalHits,
                                      List<PixabayHit> hits, Integer nextOffset) {
    }
}
