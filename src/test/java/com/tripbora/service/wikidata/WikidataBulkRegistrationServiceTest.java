package com.tripbora.service.wikidata;

import com.tripbora.dto.DestinationForm;
import com.tripbora.dto.wikidata.WikidataDestinationCandidate;
import com.tripbora.dto.wikidata.WikidataDestinationPreview;
import com.tripbora.dto.wikidata.WikidataDestinationPreview.RegionMatch;
import com.tripbora.dto.wikidata.WikidataDestinationPreview.RegionMatch.RegionPathItem;
import com.tripbora.dto.wikidata.WikipediaDescriptionPreview;
import com.tripbora.model.DestinationType;
import com.tripbora.service.destination.DestinationSaveOrchestrationService;
import com.tripbora.service.destination.DestinationService;
import com.tripbora.service.destination.DuplicateWikidataDestinationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WikidataBulkRegistrationServiceTest {
    private final WikidataDestinationService wikidata = mock(WikidataDestinationService.class);
    private final WikipediaDescriptionService wikipedia = mock(WikipediaDescriptionService.class);
    private final DestinationService destinations = mock(DestinationService.class);
    private final DestinationSaveOrchestrationService orchestration = mock(DestinationSaveOrchestrationService.class);
    private final WikidataRegionExplorer explorer = mock(WikidataRegionExplorer.class);
    /** 테스트가 시각을 옮기는 시계. 대기는 실제로 자지 않고 시각만 기록한다. */
    private final MutableClock clock = new MutableClock();
    private final ExternalApiRateLimiter limiter = new ExternalApiRateLimiter(clock, duration -> { }, () -> 0.5,
            ExternalApiRateLimiter.MAX_ATTEMPTS);
    private final WikidataBulkRegistrationService service = new WikidataBulkRegistrationService(wikidata, wikipedia,
            new WikidataDestinationFormBuilder(new ObjectMapper()), destinations, orchestration, explorer, limiter);

    {
        // 등록되지 않은 QID는 null 이다(Mockito 기본값 0L 로 두지 않는다).
        when(destinations.findWikidataDestinationId(anyString())).thenReturn(null);
    }

    @Test
    void searchKeepsOnlyPlacesMarksRegisteredQidsAndPassesTheNextPage() {
        when(wikidata.quickSearchPage("에펠탑", 10)).thenReturn(new WikidataDestinationService.QuickSearchPage(List.of(
                candidate("Q243", "에펠탑"), candidate("Q1", "에펠탑 (영화)"), candidate("Q2", "에펠탑 모형")), 20));
        when(wikidata.searchDetails(List.of("Q243", "Q1", "Q2"))).thenReturn(List.of(
                new WikidataDestinationCandidate("Q243", "에펠탑", "ko", "파리의 탑", "ko", "프랑스", "파리", null, null),
                new WikidataDestinationCandidate("Q2", null, null, null, null, "일본", null, null, null)));
        when(destinations.findRegisteredWikidataQids(List.of("Q243", "Q1", "Q2"))).thenReturn(Set.of("Q243"));

        var result = service.search("에펠탑", 10);

        assertThat(result.candidates()).extracting("qid").containsExactly("Q243", "Q2");
        assertThat(result.candidates()).extracting("registered").containsExactly(true, false);
        // 상세에 이름이 없으면 검색 목록의 이름을 쓴다.
        assertThat(result.candidates().get(1).name()).isEqualTo("에펠탑 모형");
        assertThat(result.excludedCount()).isEqualTo(1);
        assertThat(result.nextOffset()).isEqualTo(20);
    }

    @Test
    void regionPagesShowTwentyPlacesAtATimeWithTheSameDisplayRulesAsKeywordSearch() {
        List<String> qids = java.util.stream.IntStream.rangeClosed(1, 45).mapToObj(index -> "Q" + (1000 + index)).toList();
        var resolution = new WikidataRegionExplorer.Resolution(55L, "후쿠오카", "Q123258", "후쿠오카현", "ISO", null, List.of());
        when(explorer.places(55L, null)).thenReturn(new WikidataRegionExplorer.Places("Q123258", qids, false));
        when(explorer.resolve(55L)).thenReturn(resolution);
        when(wikidata.searchDetails(any())).thenAnswer(invocation -> invocation.<List<String>>getArgument(0).stream()
                // 좌표·소재지로 장소를 확인하지 못한 항목은 기존 규칙대로 빠진다.
                .filter(qid -> !qid.equals("Q1003"))
                .map(qid -> new WikidataDestinationCandidate(qid, "장소 " + qid, "ko", null, null, "일본", null, null, null))
                .toList());
        when(destinations.findRegisteredWikidataQids(any())).thenReturn(Set.of("Q1002"));

        var first = service.regionSearch(55L, null, 0);
        var last = service.regionSearch(55L, null, 40);

        assertThat(first.candidates()).hasSize(19);
        assertThat(first.excludedCount()).isEqualTo(1);
        assertThat(first.candidates().get(1).registered()).isTrue();
        assertThat(first.nextOffset()).isEqualTo(20);
        assertThat(first.total()).isEqualTo(45);
        assertThat(first.region().qid()).isEqualTo("Q123258");
        assertThat(last.candidates()).extracting("qid").containsExactly("Q1041", "Q1042", "Q1043", "Q1044", "Q1045");
        assertThat(last.nextOffset()).isNull();
        // 상세 조회는 한 번에 10곳씩 나눠 부른다(첫 페이지 10+10, 마지막 페이지 5).
        verify(wikidata, times(2)).searchDetails(argThat(list -> list != null && list.size() == 10));
        verify(wikidata, times(1)).searchDetails(argThat(list -> list != null && list.size() == 5));
    }

    @Test
    void eachDestinationIsRegisteredThroughTheExistingPathAndOneFailureDoesNotStopTheOthers() {
        stubPreview("Q243", new RegionMatch(200L, 300L, true, "",
                List.of(new RegionPathItem(1L, "유럽"), new RegionPathItem(200L, "프랑스"), new RegionPathItem(300L, "파리"))));
        stubPreview("Q10285", new RegionMatch(210L, null, false, "", List.of()));
        doThrow(new CommonsPhotoDownloadException("Commons 사진을 내려받지 못했습니다: A.jpg", null))
                .when(orchestration).registerDestination(argThat(form -> "Q10285".equals(form.getWikidataQid())), eq(7L), any());
        when(destinations.findWikidataDestinationId("Q243")).thenReturn(null, 501L);

        var failed = service.register(new WikidataBulkRegistrationService.RegisterRequest(
                "Q10285", "ATTRACTION", "SPRING", 311L, null, "A.jpg"), 7L);
        var success = service.register(new WikidataBulkRegistrationService.RegisterRequest(
                "q243", "ATTRACTION", "ALL_SEASONS", null, null, "Tour Eiffel.jpg"), 7L);

        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.message()).contains("내려받지 못했습니다");
        assertThat(success.status()).isEqualTo("SUCCESS");
        assertThat(success.destinationId()).isEqualTo(501L);
        ArgumentCaptor<DestinationForm> forms = ArgumentCaptor.forClass(DestinationForm.class);
        verify(orchestration, times(2)).registerDestination(forms.capture(), eq(7L), eq(List.of()));
        DestinationForm saved = forms.getAllValues().get(1);
        assertThat(saved.getWikidataQid()).isEqualTo("Q243");
        assertThat(saved.getType()).isEqualTo(DestinationType.ATTRACTION);
        assertThat(saved.getSeason()).isEqualTo("ALL_SEASONS");
        // 지역을 따로 고르지 않으면 자동 매핑된 지역을 쓴다.
        assertThat(saved.getRegionId()).isEqualTo(300L);
        assertThat(saved.getCommonsSelectedPhotosJson()).contains("\"fileName\":\"Tour Eiffel.jpg\"", "\"main\":true");
        assertThat(forms.getAllValues().get(0).getRegionId()).isEqualTo(311L);
    }

    @Test
    void missingChoicesAndAlreadyRegisteredQidsNeverReachTheSavePath() {
        stubPreview("Q243", new RegionMatch(200L, null, false, "", List.of()));
        when(destinations.findWikidataDestinationId("Q999")).thenReturn(42L);

        var registered = service.register(new WikidataBulkRegistrationService.RegisterRequest(
                "Q999", "ATTRACTION", "SPRING", 1L, null, null), 7L);
        var noSeason = service.register(new WikidataBulkRegistrationService.RegisterRequest(
                "Q243", "ATTRACTION", "", 1L, null, null), 7L);
        var noType = service.register(new WikidataBulkRegistrationService.RegisterRequest(
                "Q243", "PLANET", "SPRING", 1L, null, null), 7L);
        var noRegion = service.register(new WikidataBulkRegistrationService.RegisterRequest(
                "Q243", "ATTRACTION", "SPRING", null, null, null), 7L);

        assertThat(registered.status()).isEqualTo("DUPLICATE");
        assertThat(registered.destinationId()).isEqualTo(42L);
        assertThat(noSeason.message()).isEqualTo("시즌을 선택해 주세요.");
        assertThat(noType.message()).isEqualTo("유형을 선택해 주세요.");
        assertThat(noRegion.message()).isEqualTo("지역을 선택해 주세요.");
        verify(orchestration, never()).registerDestination(any(), any(), any());
    }

    @Test
    void aConcurrentRegistrationOfTheSameQidIsReportedAsAlreadyRegistered() {
        stubPreview("Q243", new RegionMatch(200L, null, false, "", List.of()));
        doThrow(new DuplicateWikidataDestinationException("Q243"))
                .when(orchestration).registerDestination(any(), any(), any());
        when(destinations.findWikidataDestinationId("Q243")).thenReturn(null, 501L);

        var result = service.register(new WikidataBulkRegistrationService.RegisterRequest(
                "Q243", "SHOP", "WINTER", 5L, null, null), 7L);

        assertThat(result.status()).isEqualTo("DUPLICATE");
        assertThat(result.destinationId()).isEqualTo(501L);
    }

    @Test
    void nineteenDestinationsElevenSucceedThenTheRestShareTheWaitAndAreRetriedWithoutDuplicates() {
        RegionMatch paris = new RegionMatch(200L, 300L, true, "",
                List.of(new RegionPathItem(1L, "유럽"), new RegionPathItem(200L, "프랑스"), new RegionPathItem(300L, "파리")));
        List<String> qids = java.util.stream.IntStream.rangeClosed(1, 19).mapToObj(index -> "Q" + (5000 + index)).toList();
        qids.forEach(qid -> stubPreview(qid, paris));
        Map<String, Long> saved = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.concurrent.atomic.AtomicBoolean limited = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicInteger apiCalls = new java.util.concurrent.atomic.AtomicInteger();
        when(destinations.findWikidataDestinationId(anyString())).thenAnswer(invocation -> saved.get(invocation.<String>getArgument(0)));
        // 기존 등록 경로 안의 Wikidata 재검증을 흉내 낸다: 실제 클라이언트처럼 공유 대기 관리자를 거친다.
        org.mockito.Mockito.doAnswer(invocation -> limiter.call(ExternalApiRateLimiter.Service.WIKIDATA, () -> {
            apiCalls.incrementAndGet();
            if (limited.get()) throw new ExternalApiRateLimiter.Limited(java.time.Duration.ofSeconds(30));
            DestinationForm form = invocation.getArgument(0);
            if (saved.putIfAbsent(form.getWikidataQid(), 900L + saved.size()) != null) {
                throw new DuplicateWikidataDestinationException(form.getWikidataQid());
            }
            return null;
        }, wait -> new WikidataRateLimitException(ExternalApiRateLimiter.Service.WIKIDATA, wait)))
                .when(orchestration).registerDestination(any(), any(), any());

        List<WikidataBulkRegistrationService.ItemResult> first = new java.util.ArrayList<>();
        for (int index = 0; index < qids.size(); index++) {
            if (index == 11) limited.set(true);
            first.add(service.register(request(qids.get(index)), 7L));
        }

        assertThat(first.subList(0, 11)).extracting("status").containsOnly("SUCCESS");
        assertThat(first.subList(11, 19)).extracting("status").containsOnly("RATE_LIMITED");
        assertThat(first.get(11).retryAfterSeconds()).isEqualTo(30);
        assertThat(first.get(11).service()).isEqualTo("Wikidata");
        // 한 곳이 제한을 받으면 나머지 7곳은 같은 API를 부르지 않고 남은 대기 시간만 받는다.
        assertThat(apiCalls.get()).isEqualTo(12);
        assertThat(first.get(18).retryAfterSeconds()).isBetween(29, 30);
        // Wikidata 제한은 Commons·Wikipedia 대기와 섞이지 않는다.
        assertThat(limiter.remaining(ExternalApiRateLimiter.Service.COMMONS)).isZero();
        assertThat(limiter.remaining(ExternalApiRateLimiter.Service.WIKIPEDIA)).isZero();

        clock.advance(java.time.Duration.ofSeconds(31));
        limited.set(false);
        List<String> retried = qids.subList(11, 19);
        assertThat(retried.stream().map(qid -> service.register(request(qid), 7L).status()))
                .containsOnly("SUCCESS");
        assertThat(saved).hasSize(19);
        // 이미 성공한 곳을 다시 보내도 저장하지 않는다.
        assertThat(service.register(request(qids.get(0)), 7L).status()).isEqualTo("DUPLICATE");
        assertThat(apiCalls.get()).isEqualTo(20);
    }

    @Test
    void onlyTemporaryLimitsAreRetryableAndCommonsLimitsDoNotBlockDestinationsWithoutPhotos() {
        stubPreview("Q10", new RegionMatch(200L, null, false, "", List.of()));
        doThrow(new CommonsPhotoSelectionException("다음 Commons 사진은 자동 저장할 수 없습니다. A.jpg: 라이선스"))
                .doThrow(new IllegalStateException("prepare failed", new CommonsRateLimitException(java.time.Duration.ofSeconds(8))))
                .doNothing()
                .when(orchestration).registerDestination(any(), any(), any());

        var license = service.register(new WikidataBulkRegistrationService.RegisterRequest(
                "Q10", "ATTRACTION", "SPRING", 5L, null, "A.jpg"), 7L);
        var wrappedLimit = service.register(new WikidataBulkRegistrationService.RegisterRequest(
                "Q10", "ATTRACTION", "SPRING", 5L, null, "A.jpg"), 7L);

        assertThat(license.status()).isEqualTo("FAILED");
        assertThat(license.retryAfterSeconds()).isNull();
        assertThat(wrappedLimit.status()).isEqualTo("RATE_LIMITED");
        assertThat(wrappedLimit.service()).isEqualTo("Wikimedia Commons");
        assertThat(wrappedLimit.retryAfterSeconds()).isEqualTo(8);

        // Commons 가 막혀 있어도 사진 없이 등록하는 여행지는 진행하고, 사진이 있으면 호출 없이 기다린다.
        limiter.recordLimited(ExternalApiRateLimiter.Service.COMMONS, java.time.Duration.ofSeconds(60));
        var withPhoto = service.register(new WikidataBulkRegistrationService.RegisterRequest(
                "Q10", "ATTRACTION", "SPRING", 5L, null, "A.jpg"), 7L);
        var withoutPhoto = service.register(new WikidataBulkRegistrationService.RegisterRequest(
                "Q10", "ATTRACTION", "SPRING", 5L, null, null), 7L);
        assertThat(withPhoto.status()).isEqualTo("RATE_LIMITED");
        assertThat(withoutPhoto.status()).isEqualTo("SUCCESS");
        verify(orchestration, times(3)).registerDestination(any(), any(), any());
    }

    private WikidataBulkRegistrationService.RegisterRequest request(String qid) {
        return new WikidataBulkRegistrationService.RegisterRequest(qid, "ATTRACTION", "SPRING", null, null, null);
    }

    static final class MutableClock extends java.time.Clock {
        private java.time.Instant now = java.time.Instant.parse("2026-09-26T00:00:00Z");

        void advance(java.time.Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return now;
        }
    }

    private void stubPreview(String qid, RegionMatch match) {
        when(wikidata.previewForAutofill(qid)).thenReturn(new WikidataDestinationPreview(qid,
                Map.of("ko", "여행지 " + qid), Map.of(), null, "국가", List.of(), 10.0, 20.0, null, null, null,
                match, null, Map.of()));
        when(wikipedia.previewForAutofill(qid)).thenReturn(new WikipediaDescriptionPreview(qid, List.of()));
    }

    private WikidataDestinationCandidate candidate(String qid, String name) {
        return new WikidataDestinationCandidate(qid, name, "ko", null, null, null, null, null, null);
    }
}
