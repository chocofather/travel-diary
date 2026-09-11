package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoTourAreaCandidateResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 지역 × 콘텐츠 유형 후보 목록을 잠깐 들고 있는다.
 *
 * <p>후보 전체를 한 번 모아 두면 페이지 이동, 등록상태 필터 전환, "전체" 유형 조회가
 * 같은 조건으로 TourAPI 를 다시 호출하지 않는다. 등록 여부는 여기에 담지 않고 매 요청마다
 * DB 로 다시 판정하므로, 등록 직후에도 캐시를 비울 필요가 없다.
 */
@Component
public class KtoTourAreaCandidateCache {

    private static final Duration TIME_TO_LIVE = Duration.ofMinutes(10);
    /** 관리자 몇 명이 동시에 쓰는 화면이라 작게 잡는다. 넘치면 만료된 것부터 버린다. */
    private static final int MAX_ENTRIES = 64;

    private final Map<Key, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public KtoTourAreaCandidateCache() {
        this(Clock.systemDefaultZone());
    }

    KtoTourAreaCandidateCache(Clock clock) {
        this.clock = clock;
    }

    public List<KtoTourAreaCandidateResponse> get(Key key,
                                                  Supplier<List<KtoTourAreaCandidateResponse>> loader) {
        Instant now = clock.instant();
        Entry cached = entries.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.candidates();
        }

        List<KtoTourAreaCandidateResponse> loaded = List.copyOf(loader.get());
        evictIfNeeded(now);
        entries.put(key, new Entry(loaded, now.plus(TIME_TO_LIVE)));
        return loaded;
    }

    private void evictIfNeeded(Instant now) {
        entries.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        Iterator<Key> iterator = entries.keySet().iterator();
        while (entries.size() >= MAX_ENTRIES && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    public record Key(String regionCode, String subRegionCode, String contentTypeId) {
    }

    private record Entry(List<KtoTourAreaCandidateResponse> candidates, Instant expiresAt) {
    }
}
