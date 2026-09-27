package com.example.travlediary.service.wikidata;

import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.dto.DestinationTranslationForm;
import com.example.travlediary.dto.wikidata.WikidataDestinationPreview;
import com.example.travlediary.dto.wikidata.WikipediaDescriptionPreview;
import com.example.travlediary.dto.wikidata.WikipediaDescriptionPreview.LanguageEntry;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.model.DestinationSeason;
import com.example.travlediary.model.DestinationTranslationSource;
import com.example.travlediary.service.category.CountryCategoryService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class WikidataRegistrationService {
    private static final List<String> LANGUAGES = List.of("ko", "en", "ja", "zh-CN", "zh-TW");
    /** 관리자 오류 문구에 언어 코드 대신 쓰는 이름. LANGUAGES 와 같은 순서다. */
    private static final List<String> LANGUAGE_LABELS = List.of("한국어", "영어", "일본어", "중국어(간체)", "중국어(번체)");
    private final WikidataDestinationService wikidataDestinationService;
    private final WikipediaDescriptionService wikipediaDescriptionService;
    private final CountryCategoryService countryCategoryService;
    private final WikidataApiClient wikidataApiClient;
    private final CommonsPhotoImportService commonsPhotoImportService;
    /** 등록 한 건의 재검증 3개(기본정보·Wikipedia·Commons)를 동시에 돌린다. 여러 관리자가 동시에 등록하면 순서를 기다린다. */
    private final ExecutorService executor = Executors.newFixedThreadPool(6, task -> {
        Thread thread = new Thread(task, "wikidata-registration");
        thread.setDaemon(true);
        return thread;
    });

    /** 재검증을 마친 Wikipedia 출처와 내려받은 Commons 사진. DB 저장만 남은 상태다. */
    public record PreparedRegistration(Map<String, DestinationTranslationSource> sources,
                                       List<PreparedCommonsPhoto> photos) {
        static final PreparedRegistration EMPTY = new PreparedRegistration(Map.of(), List.of());
    }

    /** 사진 없이 Wikidata·Wikipedia 출처만 재검증한다. */
    public Map<String, DestinationTranslationSource> prepare(DestinationForm form) {
        return prepareRegistration(form, List.of(), false).sources();
    }

    /**
     * 최종 등록 직전 재검증. 외부 호출과 사진 다운로드는 DB 트랜잭션 전에 끝낸다.
     *
     * <p>입력값 검사를 먼저 마치고, Wikidata 엔티티는 이번 요청에서 한 번만 새로 받아 기본정보·Wikipedia·Commons
     * 재검증이 함께 쓴다. 세 재검증은 서로 독립이라 병렬로 진행한다. 검색·자동입력 캐시는 쓰지 않는다.
     * 하나라도 실패하면 사진 다운로드가 끝나기를 기다려 이번에 받은 파일을 지운 뒤 원래 오류를 던진다.</p>
     */
    public PreparedRegistration prepareRegistration(DestinationForm form,
                                                    List<CommonsPhotoImportService.Selection> photos,
                                                    boolean otherMainSelected) {
        String qid = form.getWikidataQid();
        if (qid == null || qid.isBlank()) {
            if (form.getWikipediaRevisionIds() != null
                    && form.getWikipediaRevisionIds().stream().anyMatch(id -> id != null)) {
                throw new IllegalArgumentException("Wikipedia 설명을 저장하려면 Wikidata 후보를 먼저 적용해 주세요.");
            }
            return PreparedRegistration.EMPTY;
        }
        qid = qid.strip().toUpperCase();
        if (!qid.matches("Q[1-9][0-9]{0,14}")) {
            throw new IllegalArgumentException("Wikidata QID 형식이 올바르지 않습니다.");
        }
        if (form.getType() == null || form.getSeason() == null || form.getRegionId() == null) {
            throw new IllegalArgumentException("여행지 유형·시즌·지역을 모두 선택해 주세요.");
        }
        try {
            DestinationSeason.valueOf(form.getSeason());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("올바른 여행 시즌을 선택해 주세요.");
        }
        if (outOfRange(form.getLatitude(), -90, 90) || outOfRange(form.getLongitude(), -180, 180)) {
            throw new IllegalArgumentException("위도·경도 범위를 확인해 주세요.");
        }
        List<CountryCategory> path = countryCategoryService.getRegionPath(form.getRegionId());
        if (path.size() < 2 || !countryCategoryService.getOverseasRootIds().contains(path.get(0).getId())) {
            throw new IllegalArgumentException("해외 여행지의 국가·지역을 확인해 선택해 주세요.");
        }
        List<DestinationTranslationForm> translations = form.getTranslations();
        if (translations == null || translations.size() != LANGUAGES.size()) {
            throw new IllegalArgumentException("다국어 입력 항목을 확인해 주세요.");
        }
        for (int i = 0; i < LANGUAGES.size(); i++) {
            DestinationTranslationForm translation = translations.get(i);
            if (translation == null || !LANGUAGES.get(i).equals(translation.getLanguageCode())) {
                throw new IllegalArgumentException("다국어 입력 언어가 일치하지 않습니다.");
            }
            if (i == 0 && !hasText(translation.getName())) {
                throw new IllegalArgumentException("한국어 여행지명을 입력해 주세요.");
            }
            String label = LANGUAGE_LABELS.get(i);
            if ((hasText(translation.getDescription()) || hasText(translation.getShortDescription()))
                    && !hasText(translation.getName())) {
                throw new IllegalArgumentException(label + " 여행지명을 입력해 주세요.");
            }
            if (translation.getName() != null && translation.getName().codePointCount(0, translation.getName().length()) > 255) {
                throw new IllegalArgumentException(label + " 여행지명이 255자를 넘습니다.");
            }
            if (translation.getShortDescription() != null
                    && translation.getShortDescription().codePointCount(0, translation.getShortDescription().length()) > 255) {
                throw new IllegalArgumentException(label + " 간단 설명이 255자를 넘습니다.");
            }
            if (translation.getDescription() != null && translation.getDescription().getBytes(StandardCharsets.UTF_8).length > 65535) {
                throw new IllegalArgumentException(label + " 상세 설명이 DB 저장 한도를 넘습니다.");
            }
        }
        List<Long> revisions = form.getWikipediaRevisionIds();
        if (revisions == null || revisions.size() != LANGUAGES.size()) {
            throw new IllegalArgumentException("Wikipedia 판본 확인 항목이 올바르지 않습니다.");
        }

        final String verifiedQid = qid;
        JsonNode entity = wikidataApiClient.getAutofillEntities(List.of(qid)).get(qid);
        if (entity == null) {
            throw new NoSuchElementException("Wikidata 항목을 찾지 못했습니다.");
        }
        boolean wikipediaRequested = revisions.stream().anyMatch(Objects::nonNull);
        CompletableFuture<WikidataDestinationPreview> candidateCheck = CompletableFuture.supplyAsync(
                () -> wikidataDestinationService.revalidate(verifiedQid, entity), executor);
        CompletableFuture<WikipediaDescriptionPreview> wikipediaCheck = wikipediaRequested
                ? CompletableFuture.supplyAsync(() -> wikipediaDescriptionService.revalidate(verifiedQid, entity), executor)
                : CompletableFuture.completedFuture(null);
        CompletableFuture<List<PreparedCommonsPhoto>> photoImport = photos == null || photos.isEmpty()
                ? CompletableFuture.completedFuture(List.of())
                : CompletableFuture.supplyAsync(() -> commonsPhotoImportService.prepare(
                        verifiedQid, photos, otherMainSelected, entity), executor);
        try {
            var candidate = WikidataAutofillCache.join(candidateCheck);
            if (!verifiedQid.equals(candidate.qid())
                    || (candidate.countryQid() == null
                    && (candidate.regionPath() == null || candidate.regionPath().isEmpty())
                    && (candidate.latitude() == null || candidate.longitude() == null))) {
                throw new IllegalArgumentException("Wikidata 후보를 확인하지 못했습니다.");
            }
            Map<String, DestinationTranslationSource> sources = wikipediaRequested
                    ? wikipediaSources(verifiedQid, revisions, translations, WikidataAutofillCache.join(wikipediaCheck))
                    : Map.of();
            List<PreparedCommonsPhoto> prepared = WikidataAutofillCache.join(photoImport);
            form.setWikidataQid(verifiedQid);
            return new PreparedRegistration(sources, prepared);
        } catch (RuntimeException | Error failure) {
            photoImport.handle((prepared, error) -> {
                if (prepared != null) commonsPhotoImportService.cleanup(prepared);
                return null;
            }).join();
            throw failure;
        }
    }

    private Map<String, DestinationTranslationSource> wikipediaSources(String qid, List<Long> revisions,
                                                                     List<DestinationTranslationForm> translations,
                                                                     WikipediaDescriptionPreview preview) {
        Map<String, DestinationTranslationSource> sources = new LinkedHashMap<>();
        if (!qid.equals(preview.qid())) {
            throw new IllegalArgumentException("Wikipedia 문서의 Wikidata QID가 일치하지 않습니다.");
        }
        for (int i = 0; i < LANGUAGES.size(); i++) {
            Long expectedRevision = revisions.get(i);
            if (expectedRevision == null) continue;
            String language = LANGUAGES.get(i);
            String label = LANGUAGE_LABELS.get(i);
            LanguageEntry entry = preview.languages().stream()
                    .filter(item -> language.equals(item.language())).findFirst().orElse(null);
            if (entry == null || !"AVAILABLE".equals(entry.status())
                    || entry.revisionId() == null || !entry.revisionId().equals(expectedRevision)
                    || entry.title() == null || entry.sourceUrl() == null
                    || entry.licenseName() == null || entry.licenseName().isBlank()
                    || entry.licenseUrl() == null || !entry.licenseUrl().startsWith("https://creativecommons.org/")) {
                throw new IllegalArgumentException(label + " Wikipedia 문서·판본·라이선스가 바뀌었거나 불완전합니다. 후보를 다시 선택해 적용해 주세요.");
            }
            String expectedSourceLanguage = language.startsWith("zh-") ? "zh" : language;
            String expectedVariant = language.equals("zh-CN") ? "zh-cn"
                    : language.equals("zh-TW") ? "zh-tw" : null;
            if (!expectedSourceLanguage.equals(entry.sourceLanguage())
                    || !java.util.Objects.equals(expectedVariant, entry.variant())) {
                throw new IllegalArgumentException(label + " Wikipedia 원문 언어가 일치하지 않습니다.");
            }
            String description = translations.get(i).getDescription();
            if (!hasText(description)) {
                throw new IllegalArgumentException(label + " Wikipedia 설명을 확인해 주세요.");
            }
            DestinationTranslationSource source = new DestinationTranslationSource();
            source.setWikidataQid(qid);
            source.setSourceTitle(entry.title());
            source.setSourceUrl(entry.sourceUrl() + (entry.sourceUrl().contains("?") ? "&" : "?")
                    + "oldid=" + expectedRevision);
            source.setSourceRevisionId(expectedRevision);
            source.setSourceLanguage(entry.sourceLanguage());
            source.setSourceVariant(entry.variant());
            source.setLicenseName(entry.licenseName());
            source.setLicenseUrl(entry.licenseUrl());
            source.setAttributionText("Wikipedia contributors");
            source.setOriginalContentSha256(sha256(entry.description()));
            source.setContentModified(!Arrays.equals(sha256(description), source.getOriginalContentSha256()));
            source.setSourceCheckedAt(LocalDateTime.now());
            sources.put(language, source);
        }
        return Map.copyOf(sources);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    public static byte[] sha256(String text) {
        try {
            String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
            return MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private boolean outOfRange(BigDecimal value, int minimum, int maximum) {
        return value != null && (value.compareTo(BigDecimal.valueOf(minimum)) < 0
                || value.compareTo(BigDecimal.valueOf(maximum)) > 0);
    }
}
