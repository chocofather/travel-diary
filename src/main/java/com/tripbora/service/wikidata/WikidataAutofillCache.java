package com.tripbora.service.wikidata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 관리자 등록폼 자동입력(검색 상세·기본정보·Wikipedia·Commons 후보) 전용 Wikidata 캐시.
 *
 * <p>같은 QID를 여러 화면 요청이 동시에 찾으면 외부 호출 하나를 함께 기다린다. 값은 10분 뒤 만료되고,
 * 실패한 호출은 캐시에 남기지 않는다. 최종 등록의 저장 전 재검증은 이 캐시를 쓰지 않고 항상 새로 조회한다.</p>
 */
@Component
public class WikidataAutofillCache {

    static final Duration TTL = Duration.ofMinutes(10);
    private static final Pattern QID = Pattern.compile("Q[1-9][0-9]{0,14}");
    /** 전체 claims 를 담는 엔티티는 크므로 적게 둔다. */
    private static final int MAX_ENTITIES = 50;
    private static final int MAX_SMALL_ENTRIES = 500;

    private final WikidataApiClient apiClient;
    private final ExpiringCache<JsonNode> entities;
    private final ExpiringCache<JsonNode> labels;
    private final ExpiringCache<JsonNode> regions;
    private final ExecutorService executor = Executors.newFixedThreadPool(4, task -> {
        Thread thread = new Thread(task, "wikidata-autofill");
        thread.setDaemon(true);
        return thread;
    });

    @Autowired
    public WikidataAutofillCache(WikidataApiClient apiClient) {
        this(apiClient, Clock.systemUTC());
    }

    WikidataAutofillCache(WikidataApiClient apiClient, Clock clock) {
        this.apiClient = apiClient;
        this.entities = new ExpiringCache<>(TTL, MAX_ENTITIES, clock);
        this.labels = new ExpiringCache<>(TTL, MAX_SMALL_ENTRIES, clock);
        this.regions = new ExpiringCache<>(TTL, MAX_SMALL_ENTRIES, clock);
    }

    /** 라벨·설명·claims·Wikipedia/Commons 연결을 담은 엔티티. 없는 항목이면 null. */
    public JsonNode entity(String qid) {
        return entities(Collections.singletonList(qid)).get(qid);
    }

    /** 검색 상세·기본정보 조회로 이미 받아 둔 엔티티만 돌려준다. 없으면 null이며 외부 호출을 하지 않는다. */
    public JsonNode cachedEntity(String qid) {
        requireQids(Collections.singletonList(qid));
        return entities.peek(qid);
    }

    /** 검색 상세처럼 여러 후보를 한 번의 외부 호출로 받아 캐시에 채운다. */
    public Map<String, JsonNode> entities(List<String> qids) {
        return entities.getAll(requireQids(qids), apiClient::getAutofillEntities);
    }

    /** 국가·지역 이름 표시용 라벨·설명만 담은 엔티티. */
    public Map<String, JsonNode> labels(List<String> qids) {
        return labels.getAll(requireQids(qids), ids -> apiClient.getEntities(ids, false));
    }

    /** 캐시 키로 쓰기 전에 형식을 확인한다. 잘못된 값은 외부 호출도, 캐시 기록도 하지 않는다. */
    private static List<String> requireQids(List<String> qids) {
        if (qids == null || qids.stream().anyMatch(qid -> qid == null || !QID.matcher(qid).matches())) {
            throw new IllegalArgumentException("올바르지 않은 Wikidata QID입니다.");
        }
        return qids;
    }

    /**
     * 상위 지역 한 단계. 수백 KB인 전체 claims 대신 라벨과 P131(상위 행정구역)만 병렬로 받아 합친다.
     * 없는 항목이면 null.
     */
    public JsonNode region(String qid) {
        requireQids(Collections.singletonList(qid));
        return regions.get(qid, () -> {
            CompletableFuture<JsonNode> claims = async(() -> apiClient.getClaims(qid, "P131"));
            JsonNode label = labels(List.of(qid)).get(qid);
            return label == null ? null : withParentRegionClaims(label, join(claims));
        });
    }

    /** 라벨 엔티티에 wbgetclaims 로 받은 P131 claims 를 붙인다. 캐시와 저장 전 재검증이 같은 모양을 쓴다. */
    public static JsonNode withParentRegionClaims(JsonNode labels, JsonNode claimsResponse) {
        ObjectNode merged = labels.deepCopy();
        merged.set("claims", claimsResponse.path("claims"));
        return merged;
    }

    public <T> CompletableFuture<T> async(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    /** 비동기 작업의 원래 예외(WikidataApiException 등)를 그대로 다시 던진다. */
    public static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            if (exception.getCause() instanceof Error error) throw error;
            throw exception;
        }
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    /**
     * 만료 시각과 최대 개수가 있는 단일 요청 공유 캐시. null 값(없는 항목)도 만료 전까지 기억한다.
     * Pixabay 검색 결과 캐시도 같은 규칙(동시 요청은 외부 호출 하나를 함께 기다림, 실패는 남기지 않음)으로 쓴다.
     */
    public static final class ExpiringCache<V> {
        private final Duration ttl;
        private final int maxEntries;
        private final Clock clock;
        private final LinkedHashMap<String, Entry<V>> map = new LinkedHashMap<>(16, 0.75f, true);

        public ExpiringCache(Duration ttl, int maxEntries, Clock clock) {
            this.ttl = ttl;
            this.maxEntries = maxEntries;
            this.clock = clock;
        }

        public V get(String key, Supplier<V> loader) {
            return getIf(key, loader, value -> true);
        }

        /** cacheable 이 false 인 값(예: 일부 실패한 응답)은 돌려주기만 하고 캐시에 남기지 않는다. */
        V getIf(String key, Supplier<V> loader, Predicate<V> cacheable) {
            V value = getAll(List.of(key), keys -> {
                Map<String, V> loaded = new HashMap<>();
                loaded.put(key, loader.get());
                return loaded;
            }).get(key);
            if (value != null && !cacheable.test(value)) {
                synchronized (map) {
                    Entry<V> entry = map.get(key);
                    if (entry != null && entry.future().isDone() && entry.future().getNow(null) == value) map.remove(key);
                }
            }
            return value;
        }

        /** 이미 받아 둔 값만 돌려준다. 없거나 만료됐거나 아직 받는 중이면 null(외부 호출 없음). */
        V peek(String key) {
            synchronized (map) {
                Entry<V> entry = map.get(key);
                if (entry == null || !clock.instant().isBefore(entry.createdAt().plus(ttl))
                        || !entry.future().isDone() || entry.future().isCompletedExceptionally()) {
                    return null;
                }
                return entry.future().getNow(null);
            }
        }

        Map<String, V> getAll(List<String> keys, Function<List<String>, Map<String, V>> loader) {
            Map<String, CompletableFuture<V>> futures = new LinkedHashMap<>();
            List<String> owned = new ArrayList<>();
            synchronized (map) {
                Instant now = clock.instant();
                for (String key : new LinkedHashSet<>(keys)) {
                    Entry<V> entry = map.get(key);
                    if (entry == null || !now.isBefore(entry.createdAt().plus(ttl))) {
                        entry = new Entry<>(new CompletableFuture<>(), now);
                        map.put(key, entry);
                        owned.add(key);
                    }
                    futures.put(key, entry.future());
                }
                evictOverflow();
            }
            if (!owned.isEmpty()) {
                load(owned, futures, loader);
            }
            Map<String, V> result = new LinkedHashMap<>();
            for (Map.Entry<String, CompletableFuture<V>> future : futures.entrySet()) {
                V value = join(future.getValue());
                if (value != null) result.put(future.getKey(), value);
            }
            return result;
        }

        private void load(List<String> owned, Map<String, CompletableFuture<V>> futures,
                          Function<List<String>, Map<String, V>> loader) {
            try {
                Map<String, V> loaded = loader.apply(List.copyOf(owned));
                for (String key : owned) futures.get(key).complete(loaded == null ? null : loaded.get(key));
            } catch (RuntimeException | Error failure) {
                // 실패는 캐시에 남기지 않는다. 함께 기다리던 요청에는 같은 오류를 전달한다.
                synchronized (map) {
                    for (String key : owned) {
                        Entry<V> entry = map.get(key);
                        if (entry != null && entry.future() == futures.get(key)) map.remove(key);
                    }
                }
                for (String key : owned) futures.get(key).completeExceptionally(failure);
                throw failure;
            }
        }

        private void evictOverflow() {
            Iterator<Map.Entry<String, Entry<V>>> eldest = map.entrySet().iterator();
            while (map.size() > maxEntries && eldest.hasNext()) {
                eldest.next();
                eldest.remove();
            }
        }

        int size() {
            synchronized (map) {
                return map.size();
            }
        }

        private record Entry<V>(CompletableFuture<V> future, Instant createdAt) {
        }
    }
}
