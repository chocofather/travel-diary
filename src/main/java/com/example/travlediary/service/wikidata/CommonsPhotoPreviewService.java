package com.example.travlediary.service.wikidata;

import com.example.travlediary.dto.wikidata.CommonsPhotoPreview;
import com.example.travlediary.dto.wikidata.CommonsPhotoPreview.CreditLink;
import com.example.travlediary.dto.wikidata.CommonsPhotoPreview.Photo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CommonsPhotoPreviewService {
    /** 한 번에 저장할 수 있는 Commons 사진 수(선택 한도). 후보 표시 수와는 별개다. */
    static final int MAX_PHOTOS = 5;
    /** 처음과 '사진 더 보기' 한 번에 보여줄 JPEG·PNG 사진 수의 목표. */
    static final int PAGE_SIZE = 16;
    /**
     * 카테고리 한 번 요청에 받는 파일 수. 라이선스 템플릿(tltemplates) 조회는 파일 8개까지는 1초 안쪽이지만
     * 16개를 한 번에 물으면 Commons에서 20초 넘게 걸리는 경우가 있어(2026-09 Category:Eiffel Tower 확인) 8개로 나눈다.
     */
    static final int CATEGORY_BATCH = 8;
    /** 한 번 표시할 때 이어서 보내는 카테고리 요청 수 상한. 음성·문서 파일이 많은 카테고리에서도 응답 시간을 묶어 둔다. */
    static final int MAX_BATCHES_PER_PAGE = 4;
    /**
     * 미리보기가 탐색하고 저장 재검증이 후보로 인정하는 카테고리 앞쪽 파일 수(Commons cmlimit 최대값).
     * 두 곳이 같은 정렬 순서의 같은 범위를 보므로, 미리보기에서 고른 사진은 저장 때도 후보로 확인된다.
     */
    static final int CATEGORY_SCAN_LIMIT = 500;
    /** '사진 더 보기' 위치: 지금까지 살펴본 카테고리 파일 수 + Commons 이어받기 값. */
    private static final Pattern CURSOR = Pattern.compile("(\\d{1,3}):([A-Za-z0-9|]{1,1200})");
    /** 저장할 때 내려받는 Commons 렌디션 폭. 원본이 더 작으면 Commons가 원본 URL을 준다. */
    static final int SAVE_RENDITION_WIDTH = 1920;
    private static final int PREVIEW_TEXT_LIMIT = 500;
    private static final int PREVIEW_LINK_LIMIT = 8;
    private static final int PREVIEW_LINK_LABEL_LIMIT = 120;
    private static final Set<String> SAVABLE_MIME_TYPES = Set.of("image/jpeg", "image/png");
    private static final Pattern UNKNOWN_AUTHOR = Pattern.compile(
            "(?i)(unknown|anonymous)( author| photographer| artist)?|작자 ?미상|저작자 ?미상");
    static final int PREVIEW_TTL_MINUTES = 5;
    private static final int MAX_CACHED_PREVIEWS = 100;
    private static final Pattern QID = Pattern.compile("Q[1-9][0-9]{0,14}");
    private final WikidataApiClient wikidataApiClient;
    private final CommonsApiClient commonsApiClient;
    private final WikidataAutofillCache autofillCache;
    /** 같은 여행지에서 후보를 다시 불러올 때 쓰는 짧은 미리보기 캐시. 저장할 출처 정보로는 쓰지 않는다. */
    private final WikidataAutofillCache.ExpiringCache<CommonsPhotoPreview> previewCache =
            new WikidataAutofillCache.ExpiringCache<>(Duration.ofMinutes(PREVIEW_TTL_MINUTES), MAX_CACHED_PREVIEWS,
                    Clock.systemUTC());

    /** '사진 더 보기'가 같은 QID의 P18·카테고리를 다시 조회하지 않도록 짧게 둔다. */
    private final WikidataAutofillCache.ExpiringCache<PhotoSources> sourcesCache =
            new WikidataAutofillCache.ExpiringCache<>(Duration.ofMinutes(PREVIEW_TTL_MINUTES), MAX_CACHED_PREVIEWS,
                    Clock.systemUTC());

    /** 첫 사진 후보 묶음. */
    public CommonsPhotoPreview preview(String qid) {
        return preview(qid, null);
    }

    /**
     * 등록폼·이미지 관리 화면의 사진 후보 한 묶음. cursor 가 없으면 처음(P18 대표 이미지 + 카테고리 앞쪽),
     * 있으면 앞 묶음의 nextCursor 부터 이어서 보여준다. JPEG·PNG 파일만 후보로 보여준다.
     *
     * <p>같은 QID·위치를 다시 불러오면 {@value #PREVIEW_TTL_MINUTES}분 동안 캐시를 쓴다.
     * 일부 조회가 실패한 응답은 캐시하지 않는다. 저장 전 재검증은 이 캐시를 쓰지 않고 새로 조회한다.</p>
     */
    public CommonsPhotoPreview preview(String qid, String cursor) {
        if (qid == null || !QID.matcher(qid).matches()) {
            throw new IllegalArgumentException("올바르지 않은 Wikidata QID입니다.");
        }
        PageCursor start = PageCursor.parse(cursor);
        String key = start == null ? qid : qid + "|" + cursor;
        return previewCache.getIf(key, () -> loadPreview(qid, start),
                preview -> "AVAILABLE".equals(preview.status()) || "NO_PHOTOS".equals(preview.status()));
    }

    /** 기본정보 조회로 이미 받아 둔 엔티티가 있으면 쓰고, 없으면 사진에 필요한 값만 가볍게 받는다. */
    private PhotoSources sources(String qid) {
        return sourcesCache.get(qid, () -> {
            JsonNode entity = autofillCache.cachedEntity(qid);
            return photoSources(entity != null ? entity : photoEntity(qid));
        });
    }

    /**
     * 후보 한 묶음을 만든다. 카테고리는 {@value #CATEGORY_BATCH}개씩 받아 JPEG·PNG만 남기고,
     * {@value #PAGE_SIZE}장을 채우거나 {@value #MAX_BATCHES_PER_PAGE}번 요청할 때까지 이어 받는다.
     * 음성·문서·영상 등 이미지가 아닌 파일은 표시 수에 넣지 않는다.
     */
    private CommonsPhotoPreview loadPreview(String qid, PageCursor start) {
        PhotoSources sources = sources(qid);
        String category = sources.category();
        String representative = candidateTitle(sources.representative());
        int scanned = start == null ? 0 : start.scanned();
        String token = start == null ? null : start.token();
        if (start == null && representative == null && category == null) {
            return new CommonsPhotoPreview(qid, null, "NO_PHOTOS", "연결된 Commons 사진이 없습니다.", List.of(),
                    null, MAX_PHOTOS);
        }

        // P18 파일 정보(처음 묶음만)와 첫 카테고리 묶음은 서로 독립이라 함께 조회한다.
        CompletableFuture<JsonNode> representativeInfo = start != null || representative == null
                ? CompletableFuture.completedFuture(null)
                : autofillCache.async(() -> commonsApiClient.getImageInfo(List.of(representative)));
        int firstLimit = Math.min(CATEGORY_BATCH, CATEGORY_SCAN_LIMIT - scanned);
        CompletableFuture<JsonNode> firstBatch = category == null || firstLimit <= 0
                ? CompletableFuture.completedFuture(null)
                : autofillCache.async(() -> commonsApiClient.getCategoryImageInfo(category, firstLimit, token));

        List<Photo> photos = new ArrayList<>();
        // P18은 카테고리에도 들어 있는 경우가 많다. 이어지는 묶음에서도 다시 보여주지 않는다.
        Set<String> seen = new HashSet<>();
        if (representative != null) seen.add(normalizeFile(representative));
        String warning = null;
        try {
            JsonNode response = WikidataAutofillCache.join(representativeInfo);
            if (response != null) addPhoto(photos, pagesByFile(response).get(normalizeFile(representative)), "P18");
        } catch (CommonsApiException exception) {
            warning = exception.getMessage();
        }

        // 이어 보기 요청이 통째로 실패하면 같은 위치를 돌려줘 '사진 더 보기'를 다시 누를 수 있게 한다.
        String next = token;
        try {
            JsonNode response = WikidataAutofillCache.join(firstBatch);
            int requests = 0;
            while (response != null) {
                requests++;
                Map<String, JsonNode> pages = pagesByFile(response);
                for (JsonNode member : response.path("query").path("categorymembers")) {
                    scanned++;
                    String title = member.path("title").asText("");
                    if (member.path("ns").asInt(-1) != 6 || !title.startsWith("File:")) continue;
                    String key = normalizeFile(title);
                    if (seen.add(key)) addPhoto(photos, pages.get(key), "CATEGORY");
                }
                String continuation = response.path("continue").path("cmcontinue").asText("");
                next = scanned < CATEGORY_SCAN_LIMIT && PageCursor.TOKEN.matcher(continuation).matches()
                        ? continuation : null;
                if (next == null || photos.size() >= PAGE_SIZE || requests >= MAX_BATCHES_PER_PAGE) break;
                // 모자란 장수만큼만 더 받는다. 남은 파일은 다음 '사진 더 보기'에서 이어진다.
                response = commonsApiClient.getCategoryImageInfo(category, Math.min(
                        Math.min(CATEGORY_BATCH, CATEGORY_SCAN_LIMIT - scanned), PAGE_SIZE - photos.size()), next);
            }
        } catch (CommonsApiException exception) {
            // 받은 묶음까지는 보여주고, 마지막으로 받은 위치에서 다시 '사진 더 보기'를 할 수 있게 둔다.
            if (warning == null) warning = exception.getMessage();
        }

        String nextCursor = next == null ? null : scanned + ":" + next;
        boolean empty = photos.isEmpty() && nextCursor == null;
        String status = empty ? (warning == null ? "NO_PHOTOS" : "ERROR")
                : warning == null ? "AVAILABLE" : "PARTIAL";
        String message = warning != null ? warning
                : empty && start == null ? "표시할 수 있는 JPEG·PNG 사진이 없습니다." : null;
        return new CommonsPhotoPreview(qid, category, status, message, List.copyOf(photos), nextCursor, MAX_PHOTOS);
    }

    /** JPEG·PNG 사진만 후보에 넣는다. 형식은 Commons imageinfo 의 실제 MIME 으로 판단한다. */
    private void addPhoto(List<Photo> photos, JsonNode page, String source) {
        if (page == null || page.has("missing") || !page.path("imageinfo").isArray()
                || page.path("imageinfo").isEmpty()
                || !SAVABLE_MIME_TYPES.contains(page.path("imageinfo").get(0).path("mime").asText(""))) return;
        CommonsFileMetadata metadata = evaluate(page, source);
        if (metadata != null) photos.add(previewPhoto(metadata));
    }

    /** Commons 파일 제목으로 쓸 수 있는 값만 File: 접두어를 붙여 돌려준다. */
    private static String candidateTitle(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        String title = (fileName.startsWith("File:") ? fileName : "File:" + fileName).replace('_', ' ');
        if (title.length() > 260 || title.indexOf('|') >= 0
                || title.chars().anyMatch(Character::isISOControl)) return null;
        return title;
    }

    /** '사진 더 보기' 위치. 화면이 받은 값을 그대로 돌려보내므로 형식을 엄격히 확인한다. */
    private record PageCursor(int scanned, String token) {
        static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9|]{1,1200}");

        static PageCursor parse(String cursor) {
            if (cursor == null || cursor.isEmpty()) return null;
            var matcher = CURSOR.matcher(cursor);
            if (!matcher.matches() || Integer.parseInt(matcher.group(1)) >= CATEGORY_SCAN_LIMIT) {
                throw new IllegalArgumentException("사진 목록 위치가 올바르지 않습니다. 후보를 다시 불러와 주세요.");
            }
            return new PageCursor(Integer.parseInt(matcher.group(1)), matcher.group(2));
        }
    }

    /** 저장 전 재검증용 후보. 캐시 없이 Wikidata에서 새로 조회한다. */
    Candidates candidates(String qid) {
        return candidates(photoEntity(qid));
    }

    /**
     * 사진 후보에 필요한 값(P18·P373 claims와 Commons 카테고리 연결)만 새로 받는다.
     * 수백 KB인 전체 claims 대신 작은 요청 셋을 함께 보낸다.
     */
    JsonNode photoEntity(String qid) {
        CompletableFuture<JsonNode> representative = autofillCache.async(() -> wikidataApiClient.getClaims(qid, "P18"));
        CompletableFuture<JsonNode> category = autofillCache.async(() -> wikidataApiClient.getClaims(qid, "P373"));
        ObjectNode entity = wikidataApiClient.getCommonsSitelinkEntity(qid).deepCopy();
        ObjectNode claims = entity.putObject("claims");
        for (JsonNode response : List.of(WikidataAutofillCache.join(representative), WikidataAutofillCache.join(category))) {
            response.path("claims").fields().forEachRemaining(claim -> claims.set(claim.getKey(), claim.getValue()));
        }
        return entity;
    }

    private PhotoSources photoSources(JsonNode entity) {
        String representative = claimString(entity, "P18");
        String category = claimString(entity, "P373");
        if (category != null && category.startsWith("Category:")) {
            category = category.substring("Category:".length());
        }
        if (category == null) {
            String commonsTitle = entity.path("sitelinks").path("commonswiki").path("title").asText("");
            if (commonsTitle.startsWith("Category:")) category = commonsTitle.substring("Category:".length());
        }
        return new PhotoSources(representative, category);
    }

    /** QID의 P18 대표 이미지와 연결 카테고리 파일. 미리보기와 저장 재검증이 같은 후보 규칙을 쓴다. */
    Candidates candidates(JsonNode entity) {
        PhotoSources sources = photoSources(entity);
        String category = sources.category();
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        addCandidate(candidates, sources.representative(), "P18");
        String warning = null;
        if (category != null) {
            try {
                for (String title : commonsApiClient.listCategoryFiles(category)) {
                    addCandidate(candidates, title, "CATEGORY");
                }
            } catch (CommonsRateLimitException limited) {
                // 저장 재검증에서 요청 제한을 '후보에 없는 사진'으로 거부하지 않는다. 잠시 뒤 다시 확인하게 올린다.
                throw limited;
            } catch (CommonsApiException exception) {
                warning = exception.getMessage();
            }
        }
        return new Candidates(category, candidates, warning);
    }

    Map<String, JsonNode> pagesByFile(JsonNode response) {
        Map<String, JsonNode> pages = new LinkedHashMap<>();
        for (JsonNode page : response.path("query").path("pages")) {
            pages.put(normalizeFile(page.path("title").asText("")), page);
        }
        return pages;
    }

    private void addCandidate(Map<String, Candidate> candidates, String fileName, String source) {
        if (fileName == null || fileName.isBlank()) return;
        String title = fileName.startsWith("File:") ? fileName : "File:" + fileName;
        if (title.length() > 260 || title.indexOf('|') >= 0
                || title.chars().anyMatch(Character::isISOControl)) return;
        candidates.putIfAbsent(normalizeFile(title), new Candidate(title.replace('_', ' '), source));
    }

    /** 파일명 비교 키. File: 접두어·밑줄·첫 글자 대소문자 차이를 Commons 제목 규칙대로 맞춘다. */
    static String normalizeFile(String title) {
        String name = title.replace('_', ' ').replaceFirst("(?i)^File:", "")
                .trim().replaceAll("\\s+", " ");
        return name.isEmpty() ? name : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    private String claimString(JsonNode entity, String property) {
        JsonNode fallback = null;
        for (JsonNode claim : entity.path("claims").path(property)) {
            if ("deprecated".equals(claim.path("rank").asText())) continue;
            JsonNode value = claim.path("mainsnak").path("datavalue").path("value");
            if (!value.isTextual() || value.asText().isBlank()) continue;
            if ("preferred".equals(claim.path("rank").asText())) return value.asText();
            if (fallback == null) fallback = value;
        }
        return fallback == null ? null : fallback.asText();
    }

    /** imageinfo 한 건을 줄이지 않은 값으로 해석한다. 라이선스 판정 규칙은 미리보기와 저장이 공유한다. */
    CommonsFileMetadata evaluate(JsonNode page, String source) {
        String title = page.path("title").asText("");
        if (!title.startsWith("File:")) return null;
        JsonNode info = page.path("imageinfo").get(0);
        JsonNode metadata = info.path("extmetadata");
        String authorHtml = metadataValue(metadata, "Artist");
        String creditHtml = metadataValue(metadata, "Credit");
        if (creditHtml == null) creditHtml = metadataValue(metadata, "Source");
        String customHtml = metadataValue(metadata, "Attribution");
        String author = plainText(authorHtml);
        String sourceCredit = plainText(creditHtml);
        String customAttribution = plainText(customHtml);
        List<CreditLink> creditLinks = creditLinks(authorHtml, creditHtml, customHtml);

        String thumbnail = imageUrl(info.path("thumburl").asText(null), "thumb.wikimedia.org", "upload.wikimedia.org");
        String original = imageUrl(info.path("url").asText(null), "upload.wikimedia.org");
        String filePage = filePageUrl(info.path("descriptionurl").asText(null));
        int width = info.path("width").asInt(0);
        int height = info.path("height").asInt(0);
        String mime = info.path("mime").asText("");
        boolean selectable = thumbnail != null && original != null && filePage != null
                && width > 0 && height > 0 && width <= 50000 && height <= 50000
                && Set.of("image/jpeg", "image/png", "image/webp", "image/gif").contains(mime);

        String licenseCode = lowerCase(plainText(metadataValue(metadata, "License")));
        String rawLicenseUrl = plainText(metadataValue(metadata, "LicenseUrl"));
        String restrictions = plainText(metadataValue(metadata, "Restrictions"));
        CommonsLicenseRules.Decision license = CommonsLicenseRules.decide(licenseCode,
                plainText(metadataValue(metadata, "LicenseShortName")), rawLicenseUrl,
                plainText(metadataValue(metadata, "AttributionRequired")),
                plainText(metadataValue(metadata, "Copyrighted")), licenseTemplates(page));

        // 한 가지 사유만 보여준다: 형식 → 이용 제한 → 라이선스 근거 → 저작자 순으로 먼저 걸린 것.
        String category = license.category();
        String reason = license.reason();
        if (license.eligible() && license.attributionRequired()
                && (author == null || UNKNOWN_AUTHOR.matcher(author).matches())) {
            category = CommonsLicenseRules.LICENSE_EVIDENCE_MISSING;
            reason = "저작자 정보가 없어 " + license.licenseName() + "에 필요한 저작자 표시를 할 수 없습니다.";
        }
        if (restrictions != null) {
            category = CommonsLicenseRules.RESTRICTED;
            reason = CommonsLicenseRules.restrictionReason(restrictions);
        }
        if (!selectable) {
            category = CommonsLicenseRules.UNSUPPORTED_FORMAT;
            reason = "이미지 파일 형식·크기 또는 Commons 파일 주소를 확인하지 못했습니다" + (mime.isEmpty() ? "." : "(" + mime + ").");
        } else if (!SAVABLE_MIME_TYPES.contains(mime)) {
            category = CommonsLicenseRules.UNSUPPORTED_FORMAT;
            reason = "자동 저장은 JPEG·PNG 사진만 지원합니다(현재 " + mime + ").";
        }
        String licenseName = license.licenseName() != null ? license.licenseName()
                : plainText(metadataValue(metadata, "LicenseShortName"));

        return new CommonsFileMetadata(title, title.substring(5), source, selectable, thumbnail, original,
                filePage, width, height, mime, page.path("pageid").asLong(0),
                page.path("lastrevid").asLong(0), author, sourceCredit, customAttribution,
                creditLinks, licenseCode, license.licenseType(), licenseName, license.licenseVersion(),
                license.licenseUrl(), category, license.attributionRequired(), license.changesRequired(),
                license.shareAlikeRequired(), license.conditions(), restrictions, reason, reason,
                license.evidence());
    }

    /** 이 응답에 담긴 라이선스 템플릿 이름(접두어 'Template:' 제외). tltemplates 로 판별용 템플릿만 받는다. */
    private Set<String> licenseTemplates(JsonNode page) {
        Set<String> templates = new LinkedHashSet<>();
        for (JsonNode template : page.path("templates")) {
            String title = template.path("title").asText("");
            if (title.startsWith("Template:")) templates.add(title.substring("Template:".length()));
        }
        return templates;
    }

    private Photo previewPhoto(CommonsFileMetadata metadata) {
        List<CreditLink> links = metadata.creditLinks().stream()
                .limit(PREVIEW_LINK_LIMIT)
                .map(link -> new CreditLink(truncate(link.label(), PREVIEW_LINK_LABEL_LIMIT), link.url()))
                .toList();
        return new Photo(metadata.fileName(), metadata.source(), metadata.selectable(),
                metadata.thumbnailUrl(), metadata.originalUrl(), metadata.filePageUrl(),
                metadata.width(), metadata.height(), truncate(metadata.author(), PREVIEW_TEXT_LIMIT),
                truncate(metadata.sourceCredit(), PREVIEW_TEXT_LIMIT),
                truncate(metadata.customAttribution(), PREVIEW_TEXT_LIMIT), links,
                metadata.licenseType(), truncate(metadata.licenseName(), PREVIEW_TEXT_LIMIT),
                metadata.licenseVersion(), metadata.licenseUrl(), metadata.reuseStatus(),
                metadata.attributionRequired(), metadata.changesRequired(),
                metadata.shareAlikeRequired(), metadata.conditions(),
                truncate(metadata.restrictions(), PREVIEW_TEXT_LIMIT), metadata.reviewReason(),
                metadata.saveBlockReason() == null, metadata.saveBlockReason(),
                truncate(metadata.licenseEvidence(), PREVIEW_TEXT_LIMIT));
    }

    private String metadataValue(JsonNode metadata, String field) {
        return metadata.path(field).path("value").asText(null);
    }

    private String plainText(String html) {
        if (html == null || html.isBlank()) return null;
        String value = Jsoup.parseBodyFragment(html).text().trim();
        return value.isBlank() ? null : value;
    }

    private String lowerCase(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String truncate(String value, int limit) {
        return value == null ? null : value.substring(0, Math.min(value.length(), limit));
    }

    private List<CreditLink> creditLinks(String... fragments) {
        List<CreditLink> links = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String fragment : fragments) {
            if (fragment == null) continue;
            for (Element anchor : Jsoup.parseBodyFragment(fragment).select("a[href]")) {
                String href = anchor.attr("href").trim();
                if (href.startsWith("//")) href = "https:" + href;
                String url = safeHttpUrl(href);
                String label = anchor.text().trim();
                if (url != null && !label.isBlank() && seen.add(url)) {
                    links.add(new CreditLink(label, url));
                }
            }
        }
        return List.copyOf(links);
    }

    /** Commons 파일 URL. API가 붙이는 utm 추적 쿼리는 파일을 식별하지 않으므로 떼고 쓴다. */
    private String imageUrl(String url, String... hosts) {
        String safe = safeHttpUrl(url);
        if (safe == null) return null;
        URI uri = URI.create(safe);
        if (!"https".equals(uri.getScheme()) || !uri.getPath().startsWith("/wikipedia/commons/")) return null;
        int query = safe.indexOf('?');
        int fragment = safe.indexOf('#');
        int end = query < 0 ? fragment : fragment < 0 ? query : Math.min(query, fragment);
        String withoutQuery = end < 0 ? safe : safe.substring(0, end);
        for (String host : hosts) if (host.equals(uri.getHost())) return withoutQuery;
        return null;
    }

    private String filePageUrl(String url) {
        String safe = safeHttpUrl(url);
        if (safe == null) return null;
        URI uri = URI.create(safe);
        return "https".equals(uri.getScheme()) && "commons.wikimedia.org".equals(uri.getHost())
                && uri.getPath().startsWith("/wiki/File:") ? safe : null;
    }

    private String safeHttpUrl(String url) {
        if (url == null || url.isBlank() || url.length() > 1500) return null;
        try {
            URI uri = URI.create(url);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null ? url : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    record Candidate(String title, String source) {
    }

    record Candidates(String category, Map<String, Candidate> files, String warning) {
    }

    private record PhotoSources(String representative, String category) {
    }
}
