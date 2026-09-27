package com.example.travlediary.service.wikidata;

import com.example.travlediary.dto.wikidata.WikipediaDescriptionPreview;
import com.example.travlediary.dto.wikidata.WikipediaDescriptionPreview.LanguageEntry;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class WikipediaDescriptionService {
    private final WikidataApiClient wikidataApiClient;
    private final WikipediaApiClient wikipediaApiClient;
    private final WikidataAutofillCache autofillCache;
    // One bounded pool for preview and save-time revalidation; the number of external calls is unchanged.
    // Five threads let all five language pages load in a single wave instead of two.
    private final ExecutorService languageExecutor = Executors.newFixedThreadPool(5, task -> {
        Thread thread = new Thread(task, "wikipedia-description-preview");
        thread.setDaemon(true);
        return thread;
    });

    /** 저장 전 재검증용. Wikidata 문서 연결부터 매번 새로 조회한다. */
    public WikipediaDescriptionPreview preview(String qid) {
        return preview(qid, wikidataApiClient.getWikipediaSitelinks(qid));
    }

    /**
     * 저장 전 재검증. 문서 연결은 이번 등록 요청에서 새로 받은 엔티티에서 읽고,
     * 본문·판본·라이선스는 언어별로 새로 조회한다.
     */
    public WikipediaDescriptionPreview revalidate(String qid, JsonNode freshEntity) {
        return preview(qid, WikidataApiClient.wikipediaSitelinks(freshEntity));
    }

    /**
     * 등록폼 자동입력용. 문서 연결은 기본정보·Commons와 공유하는 캐시 엔티티에서 읽는다.
     * Wikipedia 본문·판본·라이선스는 캐시하지 않고 매번 조회한다.
     */
    public WikipediaDescriptionPreview previewForAutofill(String qid) {
        JsonNode entity = autofillCache.entity(qid);
        if (entity == null) {
            throw new NoSuchElementException("해당 Wikidata 후보를 찾을 수 없습니다.");
        }
        return preview(qid, WikidataApiClient.wikipediaSitelinks(entity));
    }

    private WikipediaDescriptionPreview preview(String qid, Map<String, String> sitelinks) {
        List<CompletableFuture<LanguageEntry>> tasks = List.of(
                language(qid, "ko", "kowiki", "ko", null, sitelinks),
                language(qid, "en", "enwiki", "en", null, sitelinks),
                language(qid, "ja", "jawiki", "ja", null, sitelinks),
                language(qid, "zh-CN", "zhwiki", "zh", "zh-cn", sitelinks),
                language(qid, "zh-TW", "zhwiki", "zh", "zh-tw", sitelinks));
        // 요청 제한 예외가 CompletionException 에 싸이지 않게 원래 예외로 풀어 올린다.
        return new WikipediaDescriptionPreview(qid, tasks.stream().map(WikidataAutofillCache::join).toList());
    }

    private CompletableFuture<LanguageEntry> language(String qid, String language, String site,
            String sourceLanguage, String variant, Map<String, String> sitelinks) {
        if (!sitelinks.containsKey(site)) {
            return CompletableFuture.completedFuture(unavailable(language, "NO_SITELINK",
                    "해당 언어 Wikipedia 문서가 제공되지 않습니다."));
        }
        return CompletableFuture.supplyAsync(
                () -> load(qid, language, site, sourceLanguage, variant, sitelinks), languageExecutor);
    }

    @PreDestroy
    void stop() {
        languageExecutor.shutdownNow();
    }

    private LanguageEntry load(String qid, String language, String site, String sourceLanguage,
                               String variant, Map<String, String> sitelinks) {
        String sitelinkTitle = sitelinks.get(site);
        if (sitelinkTitle == null) {
            return unavailable(language, "NO_SITELINK", "해당 언어 Wikipedia 문서가 제공되지 않습니다.");
        }
        try {
            JsonNode response = wikipediaApiClient.getPage(sourceLanguage, sitelinkTitle, variant);
            JsonNode pages = response.path("query").path("pages");
            if (pages.isEmpty() || pages.get(0).has("missing")) {
                return unavailable(language, "NO_CONTENT", "연결된 Wikipedia 문서를 찾을 수 없습니다.");
            }
            JsonNode page = pages.get(0);
            if (!qid.equals(page.path("pageprops").path("wikibase_item").asText())
                    || !sourceLanguage.equals(page.path("pagelanguage").asText())) {
                return unavailable(language, "QID_MISMATCH", "문서의 Wikidata QID 또는 원문 언어가 일치하지 않아 표시하지 않습니다.");
            }
            String description = page.path("extract").asText("").trim();
            if (description.isEmpty()) {
                return unavailable(language, "NO_CONTENT", "이 문서의 소개 내용이 제공되지 않습니다.");
            }
            String url = page.path("fullurl").asText("");
            if (!isWikipediaUrl(url, sourceLanguage)) {
                return unavailable(language, "ERROR", "Wikipedia 원문 주소를 확인하지 못했습니다.");
            }
            if (variant != null) url += (url.contains("?") ? "&" : "?") + "variant=" + variant;
            JsonNode rights = response.path("query").path("rightsinfo");
            String licenseUrl = rights.path("url").asText("");
            String title = page.path("title").asText(sitelinkTitle);
            return new LanguageEntry(language, "AVAILABLE", title,
                    description, description.codePointCount(0, description.length()), url, sourceLanguage,
                    variant, rights.path("text").asText(""),
                    licenseUrl.startsWith("https://creativecommons.org/") ? licenseUrl : null,
                    page.path("lastrevid").isNumber() ? page.path("lastrevid").asLong() : null, null,
                    displayTitle(page, title, variant));
        } catch (WikipediaRateLimitException limited) {
            // 요청 제한은 '문서 없음'처럼 보이면 안 된다. 그대로 올려 잠시 뒤 다시 조회하게 한다.
            throw limited;
        } catch (WikipediaApiException exception) {
            return unavailable(language, "ERROR", exception.getMessage());
        }
    }

    private boolean isWikipediaUrl(String url, String language) {
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme())
                    && (language + ".wikipedia.org").equals(uri.getHost());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 중국어 문서 제목은 한 가지 표기로 저장돼 있어(예: 艾菲爾鐵塔) 변형을 요청해도 title 은 바뀌지 않는다.
     * 변형 입력칸에는 Wikipedia가 변환한 varianttitles 의 해당 변형 제목만 쓰고, 없으면 제목을 비워 둔다.
     */
    private String displayTitle(JsonNode page, String title, String variant) {
        if (variant == null) return title;
        String converted = page.path("varianttitles").path(variant).asText("").trim();
        return converted.isEmpty() ? null : converted;
    }

    private LanguageEntry unavailable(String language, String status, String message) {
        return new LanguageEntry(language, status, null, null, 0, null, null,
                null, null, null, null, message, null);
    }
}
