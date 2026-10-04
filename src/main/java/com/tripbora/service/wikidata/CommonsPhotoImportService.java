package com.tripbora.service.wikidata;

import com.tripbora.dto.wikidata.CommonsPhotoPreview.CreditLink;
import com.tripbora.model.DestinationImageCommonsSource;
import com.tripbora.service.kto.KtoDownloadedPhoto;
import com.tripbora.service.kto.KtoPhotoDownloadService;
import com.tripbora.service.kto.PhotoDownloadRateLimitedException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * 등록폼에서 선택한 Commons 사진을 저장 직전에 다시 검증하고 내려받는다.
 *
 * <p>브라우저는 파일명과 대표 여부만 보낸다. URL·저작자·라이선스는 서버가 Commons API에서
 * 다시 조회한 전체 길이 값만 쓰며, 미리보기용으로 줄인 값은 저장에 쓰지 않는다.
 * 외부 호출과 내려받기는 DB 트랜잭션 전에 끝내고, 실패하면 이번에 내려받은 파일을 지운다.</p>
 */
@Slf4j
@Service
public class CommonsPhotoImportService {

    static final String SOURCE_NAME = "Wikimedia Commons";
    static final String LICENSE_TYPE = "CREATIVE_COMMONS";
    static final String PUBLIC_DOMAIN_LICENSE_TYPE = "PUBLIC_DOMAIN";
    private static final int MAX_SELECTION_JSON_LENGTH = 8 * 1024;
    private static final int MAX_FILE_NAME_LENGTH = 240;
    /** destination_image_sources 의 TEXT 컬럼 한도. 넘으면 자르지 않고 저장을 막는다. */
    private static final int TEXT_COLUMN_MAX_BYTES = 65_535;

    private final CommonsPhotoPreviewService previewService;
    private final CommonsApiClient commonsApiClient;
    private final KtoPhotoDownloadService downloadService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    /** 한 등록에서 선택할 수 있는 사진은 최대 5장이다. Commons 부하를 고려해 동시에 3장까지만 받는다. */
    private static final int MAX_PARALLEL_DOWNLOADS = 3;
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_PARALLEL_DOWNLOADS, task -> {
        Thread thread = new Thread(task, "commons-photo-import");
        thread.setDaemon(true);
        return thread;
    });

    /** 사진 다운로드(upload.wikimedia.org)도 Commons 요청 제한 대기 상태를 함께 지킨다. */
    private final ExternalApiRateLimiter rateLimiter;

    @Autowired
    public CommonsPhotoImportService(CommonsPhotoPreviewService previewService,
                                     CommonsApiClient commonsApiClient,
                                     KtoPhotoDownloadService downloadService,
                                     ObjectMapper objectMapper,
                                     ExternalApiRateLimiter rateLimiter) {
        this(previewService, commonsApiClient, downloadService, objectMapper, Clock.systemDefaultZone(), rateLimiter);
    }

    CommonsPhotoImportService(CommonsPhotoPreviewService previewService,
                              CommonsApiClient commonsApiClient,
                              KtoPhotoDownloadService downloadService,
                              ObjectMapper objectMapper,
                              Clock clock) {
        this(previewService, commonsApiClient, downloadService, objectMapper, clock,
                ExternalApiRateLimiter.singleAttempt());
    }

    CommonsPhotoImportService(CommonsPhotoPreviewService previewService,
                              CommonsApiClient commonsApiClient,
                              KtoPhotoDownloadService downloadService,
                              ObjectMapper objectMapper,
                              Clock clock,
                              ExternalApiRateLimiter rateLimiter) {
        this.previewService = previewService;
        this.commonsApiClient = commonsApiClient;
        this.downloadService = downloadService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.rateLimiter = rateLimiter;
    }

    public record Selection(String fileName, boolean main) {
    }

    /** 선택 파일명을 저장된 commons_file_title 과 같은 형태('File:' + Commons 제목 규칙)로 바꾼다. */
    public static String commonsFileTitle(String fileName) {
        return "File:" + CommonsPhotoPreviewService.normalizeFile(fileName);
    }

    /**
     * 등록폼의 선택값 {"qid":"Q243","photos":[{"fileName":"...","main":true}]}를 해석한다.
     * 다른 QID에서 고른 선택은 섞이지 않도록 거부한다.
     */
    public List<Selection> parseSelections(String json, String formQid) {
        if (json == null || json.isBlank()) return List.of();
        if (json.length() > MAX_SELECTION_JSON_LENGTH) throw invalidSelection();
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw invalidSelection();
        }
        if (root == null || !root.isObject() || !root.path("photos").isArray()) throw invalidSelection();
        JsonNode photos = root.path("photos");
        if (photos.isEmpty()) return List.of();

        String qid = formQid == null ? "" : formQid.strip().toUpperCase();
        if (qid.isEmpty()) {
            throw new CommonsPhotoSelectionException("Commons 사진을 저장하려면 Wikidata 후보를 먼저 적용해 주세요.");
        }
        if (!root.path("qid").isTextual() || !qid.equals(root.path("qid").asText().strip().toUpperCase())) {
            throw new CommonsPhotoSelectionException(
                    "Wikidata 후보가 바뀌어 사진 선택이 초기화되었습니다. 사진을 다시 선택해 주세요.");
        }
        if (photos.size() > CommonsPhotoPreviewService.MAX_PHOTOS) {
            throw new CommonsPhotoSelectionException(
                    "Commons 사진은 최대 " + CommonsPhotoPreviewService.MAX_PHOTOS + "장까지 선택할 수 있습니다.");
        }

        List<Selection> selections = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int mainCount = 0;
        for (JsonNode photo : photos) {
            JsonNode fileNode = photo.path("fileName");
            JsonNode mainNode = photo.path("main");
            if (!fileNode.isTextual() || !(mainNode.isMissingNode() || mainNode.isBoolean())) {
                throw invalidSelection();
            }
            String fileName = fileNode.asText().strip();
            if (fileName.isEmpty() || fileName.length() > MAX_FILE_NAME_LENGTH || fileName.indexOf('|') >= 0
                    || fileName.chars().anyMatch(Character::isISOControl)) {
                throw invalidSelection();
            }
            String normalized = CommonsPhotoPreviewService.normalizeFile(fileName);
            if (!seen.add(normalized)) {
                throw new CommonsPhotoSelectionException("같은 Commons 사진을 두 번 선택했습니다: " + normalized);
            }
            boolean main = mainNode.asBoolean(false);
            if (main) mainCount++;
            selections.add(new Selection(normalized, main));
        }
        if (mainCount > 1) {
            throw new CommonsPhotoSelectionException("Commons 대표 사진은 1장만 지정할 수 있습니다.");
        }
        return List.copyOf(selections);
    }

    /**
     * 선택 사진을 QID 후보·원본 메타데이터 기준으로 다시 검증하고 내려받는다.
     *
     * @param otherMainSelected 직접 업로드한 사진이 이미 대표로 지정됐는지
     */
    public List<PreparedCommonsPhoto> prepare(String qid, List<Selection> selections, boolean otherMainSelected) {
        return prepare(qid, selections, otherMainSelected, null);
    }

    /**
     * @param freshEntity 이번 등록 요청에서 새로 받은 Wikidata 엔티티. null 이면 여기서 새로 조회한다.
     *                    검색·자동입력 캐시 값은 받지 않는다.
     */
    public List<PreparedCommonsPhoto> prepare(String qid, List<Selection> selections, boolean otherMainSelected,
                                              JsonNode freshEntity) {
        if (selections == null || selections.isEmpty()) return List.of();
        if (selections.stream().anyMatch(Selection::main) && otherMainSelected) {
            throw new CommonsPhotoSelectionException(
                    "직접 업로드한 사진과 Commons 사진을 함께 대표로 지정할 수 없습니다. 대표 사진을 하나만 선택해 주세요.");
        }
        // 새 여행지는 대표가 없으면 첫 사진을 대표로 둔다.
        return verifyAndDownload(qid, selections, !otherMainSelected, freshEntity);
    }

    /**
     * 이미 등록된 여행지에 Commons 사진을 더한다. 재검증·다운로드 규칙은 최초 등록과 같다.
     * 대표 사진은 관리자가 명시적으로 고른 경우에만 바꾸고, 여행지에 대표 사진이 없을 때만 첫 사진을 대표로 둔다.
     */
    public List<PreparedCommonsPhoto> prepareForExistingDestination(String qid, List<Selection> selections,
                                                                    boolean destinationHasMain) {
        if (selections == null || selections.isEmpty()) return List.of();
        return verifyAndDownload(qid, selections, !destinationHasMain, null);
    }

    private List<PreparedCommonsPhoto> verifyAndDownload(String qid, List<Selection> selections,
                                                         boolean assignDefaultMain, JsonNode freshEntity) {
        boolean commonsMainSelected = selections.stream().anyMatch(Selection::main);
        String normalizedQid = qid.strip().toUpperCase();

        // 후보 목록(P18·카테고리)과 선택 파일의 원본 메타데이터는 서로 독립이라 함께 조회한다.
        // 후보에 없는 파일은 아래에서 거부하므로, 메타데이터를 먼저 받아도 내려받지는 않는다.
        CompletableFuture<CommonsPhotoPreviewService.Candidates> candidateLookup = CompletableFuture.supplyAsync(
                () -> freshEntity == null ? previewService.candidates(normalizedQid) : previewService.candidates(freshEntity),
                executor);
        List<String> titles = selections.stream().map(selection -> "File:" + selection.fileName()).toList();
        CompletableFuture<JsonNode> imageInfo = CompletableFuture.supplyAsync(
                () -> commonsApiClient.getImageInfoForSave(titles), executor);

        CommonsPhotoPreviewService.Candidates candidates = WikidataAutofillCache.join(candidateLookup);
        List<String> rejections = new ArrayList<>();
        for (Selection selection : selections) {
            CommonsPhotoPreviewService.Candidate candidate = candidates.files().get(selection.fileName());
            if (candidate == null) {
                rejections.add(selection.fileName() + ": " + (candidates.warning() == null
                        ? "현재 Wikidata 후보에 없는 사진입니다. 사진 후보를 다시 불러와 주세요."
                        : "Commons 후보를 다시 확인하지 못했습니다. " + candidates.warning()));
            }
        }
        throwIfRejected(rejections);

        Map<String, JsonNode> pages = previewService.pagesByFile(WikidataAutofillCache.join(imageInfo));
        LocalDateTime checkedAt = LocalDateTime.now(clock);
        List<VerifiedPhoto> verified = new ArrayList<>();
        for (int index = 0; index < selections.size(); index++) {
            Selection selection = selections.get(index);
            JsonNode page = pages.get(selection.fileName());
            if (page == null || page.has("missing") || !page.path("imageinfo").isArray()
                    || page.path("imageinfo").isEmpty()) {
                rejections.add(selection.fileName() + ": Commons 파일을 찾을 수 없습니다.");
                continue;
            }
            String source = candidates.files().get(selection.fileName()).source();
            CommonsFileMetadata metadata = previewService.evaluate(page, source);
            String reason = metadata == null ? "Commons 파일 정보를 확인하지 못했습니다." : saveBlockReason(metadata);
            if (reason != null) {
                rejections.add(selection.fileName() + ": " + reason);
                continue;
            }
            DestinationImageCommonsSource imageSource = imageSource(normalizedQid, metadata, checkedAt);
            if (exceedsTextLimit(imageSource)) {
                rejections.add(selection.fileName()
                        + ": 출처 메타데이터가 저장 한도를 넘습니다. 원본 파일 페이지를 확인해 주세요.");
                continue;
            }
            boolean main = selection.main() || (assignDefaultMain && !commonsMainSelected && index == 0);
            verified.add(new VerifiedPhoto(metadata, imageSource, main));
        }
        throwIfRejected(rejections);
        return download(verified);
    }

    /** 이번 등록에서 내려받은 Commons 파일을 지운다. 정리 실패는 원래 오류를 가리지 않는다. */
    public void cleanup(List<PreparedCommonsPhoto> photos) {
        if (photos == null) return;
        for (PreparedCommonsPhoto photo : photos) {
            if (photo == null) continue;
            try {
                downloadService.deleteDownloadedPhoto(photo.localImageUrl());
            } catch (RuntimeException cleanupFailure) {
                log.warn("Commons 사진 등록 실패 파일을 정리하지 못했습니다. (원인: {})",
                        cleanupFailure.getClass().getSimpleName());
            }
        }
    }

    /**
     * 검증을 마친 사진을 동시에 최대 {@value #MAX_PARALLEL_DOWNLOADS}장까지 내려받는다.
     * 하나라도 실패하면 나머지 다운로드가 끝나기를 기다린 뒤, 이번 등록에서 받은 파일을 모두 지운다.
     */
    private List<PreparedCommonsPhoto> download(List<VerifiedPhoto> verified) {
        List<CompletableFuture<PreparedCommonsPhoto>> downloads = verified.stream()
                .map(photo -> CompletableFuture.supplyAsync(() -> {
                    KtoDownloadedPhoto downloaded = rateLimiter.call(ExternalApiRateLimiter.Service.COMMONS, () -> {
                        try {
                            return downloadService.downloadCommonsImage(photo.metadata().thumbnailUrl());
                        } catch (PhotoDownloadRateLimitedException limited) {
                            throw new ExternalApiRateLimiter.Limited(
                                    ExternalApiRateLimiter.parseRetryAfter(limited.retryAfter()));
                        }
                    }, CommonsRateLimitException::new);
                    return new PreparedCommonsPhoto(downloaded.localImageUrl(), photo.main(), photo.source());
                }, executor))
                .toList();
        List<PreparedCommonsPhoto> prepared = new ArrayList<>();
        RuntimeException firstFailure = null;
        VerifiedPhoto failedPhoto = null;
        for (int index = 0; index < downloads.size(); index++) {
            try {
                prepared.add(WikidataAutofillCache.join(downloads.get(index)));
            } catch (RuntimeException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                    failedPhoto = verified.get(index);
                }
            }
        }
        if (firstFailure != null) {
            cleanup(prepared);
            // 요청 제한은 일시적이라 그대로 올려 화면이 기다렸다가 다시 시도하게 한다. 받은 파일은 위에서 지웠다.
            if (ExternalApiRateLimiter.rateLimitOf(firstFailure).isPresent()) throw firstFailure;
            throw new CommonsPhotoDownloadException("Commons 사진을 내려받지 못했습니다: "
                    + failedPhoto.metadata().fileName()
                    + ". 잠시 후 다시 시도하거나 이 사진을 제외하고 등록해 주세요. 입력값은 저장되지 않았습니다.",
                    firstFailure);
        }
        return List.copyOf(prepared);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    /** 미리보기 판정 외에 실제 저장에 필요한 판본·파일 URL 조건을 확인한다. */
    private String saveBlockReason(CommonsFileMetadata metadata) {
        if (metadata.saveBlockReason() != null) return metadata.saveBlockReason();
        if (!CommonsLicenseRules.ELIGIBLE.equals(metadata.reuseStatus())
                || !Set.of("CC_BY", "CC_BY_SA", "CC0", "PUBLIC_DOMAIN").contains(metadata.licenseType())) {
            return "자동 저장할 수 있는 라이선스가 아닙니다.";
        }
        if (metadata.pageId() <= 0 || metadata.lastRevisionId() <= 0) {
            return "Commons 파일 페이지 판본을 확인하지 못했습니다.";
        }
        if (metadata.thumbnailUrl() == null
                || !(metadata.thumbnailUrl().startsWith("https://thumb.wikimedia.org/wikipedia/commons/")
                || metadata.thumbnailUrl().startsWith("https://upload.wikimedia.org/wikipedia/commons/"))) {
            return "내려받을 Commons 이미지 URL을 확인하지 못했습니다.";
        }
        return null;
    }

    private DestinationImageCommonsSource imageSource(String qid, CommonsFileMetadata metadata,
                                                      LocalDateTime checkedAt) {
        String licenseName = metadata.licenseName() != null ? metadata.licenseName() : standardLicenseName(metadata);
        boolean scaled = metadata.width() > CommonsPhotoPreviewService.SAVE_RENDITION_WIDTH;
        DestinationImageCommonsSource source = new DestinationImageCommonsSource();
        source.setSourceName(SOURCE_NAME);
        source.setExternalContentId("M" + metadata.pageId());
        source.setSourceTitle(metadata.fileName());
        source.setAuthorText(metadata.author());
        source.setWorkPageUrl(metadata.filePageUrl());
        source.setOriginalImageUrl(metadata.originalUrl());
        source.setLicenseType("PUBLIC_DOMAIN".equals(metadata.licenseType())
                ? PUBLIC_DOMAIN_LICENSE_TYPE : LICENSE_TYPE);
        source.setLicenseName(licenseName);
        source.setLicenseVersion(metadata.licenseVersion());
        source.setLicenseUrl(metadata.licenseUrl());
        String credit = metadata.customAttribution() != null ? metadata.customAttribution() : metadata.author();
        source.setAttributionText((credit != null ? credit + ", " : "") + licenseName + ", via " + SOURCE_NAME);
        source.setSourceCredit(metadata.sourceCredit());
        source.setCustomAttribution(metadata.customAttribution());
        source.setCreditLinksJson(creditLinksJson(metadata.creditLinks()));
        source.setLicenseEvidenceUrl("https://commons.wikimedia.org/w/index.php?oldid=" + metadata.lastRevisionId());
        source.setLicenseEvidenceDetail("Commons API extmetadata·템플릿 재조회 · 판별 근거: "
                + metadata.licenseEvidence()
                + " · 파일 페이지 판본 " + metadata.lastRevisionId()
                + " · 저장 파일: " + (scaled
                ? "Commons 제공 " + CommonsPhotoPreviewService.SAVE_RENDITION_WIDTH + "px 폭 렌디션"
                : "원본 크기"));
        source.setLicenseEvidenceRevisionId(metadata.lastRevisionId());
        source.setLicenseConditions(metadata.conditions());
        source.setLicenseRestrictions(metadata.restrictions());
        source.setAttributionRequired(metadata.attributionRequired());
        source.setChangesRequired(metadata.changesRequired());
        source.setShareAlikeRequired(metadata.shareAlikeRequired());
        // 파일 내용은 편집하지 않는다. 폭 축소는 Commons가 만든 렌디션을 그대로 받는다.
        source.setContentModified(false);
        source.setLicenseCheckedAt(checkedAt);
        source.setWikidataQid(qid);
        source.setCommonsFileTitle(metadata.title());
        return source;
    }

    private String standardLicenseName(CommonsFileMetadata metadata) {
        return switch (metadata.licenseType()) {
            case "CC_BY" -> "CC BY " + metadata.licenseVersion();
            case "CC_BY_SA" -> "CC BY-SA " + metadata.licenseVersion();
            case "CC0" -> "CC0 " + metadata.licenseVersion();
            case "PUBLIC_DOMAIN" -> "Public domain";
            default -> metadata.licenseType();
        };
    }

    private String creditLinksJson(List<CreditLink> links) {
        if (links == null || links.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(links);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Commons 크레딧 링크를 저장 형식으로 바꾸지 못했습니다.", exception);
        }
    }

    private boolean exceedsTextLimit(DestinationImageCommonsSource source) {
        return Stream.of(source.getSourceTitle(), source.getAuthorText(), source.getWorkPageUrl(),
                        source.getOriginalImageUrl(), source.getLicenseName(), source.getLicenseUrl(),
                        source.getAttributionText(), source.getSourceCredit(), source.getCustomAttribution(),
                        source.getLicenseEvidenceUrl(), source.getLicenseEvidenceDetail(),
                        source.getLicenseConditions(), source.getLicenseRestrictions(),
                        source.getExternalContentId(), source.getCommonsFileTitle())
                .anyMatch(value -> value != null && value.getBytes(StandardCharsets.UTF_8).length > TEXT_COLUMN_MAX_BYTES)
                || (source.getLicenseVersion() != null && source.getLicenseVersion().length() > 30);
    }

    private void throwIfRejected(List<String> rejections) {
        if (rejections.isEmpty()) return;
        throw new CommonsPhotoSelectionException("다음 Commons 사진은 자동 저장할 수 없습니다. "
                + "선택을 해제한 뒤 다시 등록해 주세요. " + String.join(" / ", rejections));
    }

    private CommonsPhotoSelectionException invalidSelection() {
        return new CommonsPhotoSelectionException("선택한 Commons 사진 정보가 올바르지 않습니다. 사진을 다시 선택해 주세요.");
    }

    private record VerifiedPhoto(CommonsFileMetadata metadata, DestinationImageCommonsSource source, boolean main) {
    }
}
