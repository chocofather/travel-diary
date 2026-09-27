package com.example.travlediary.service.wikidata;

import com.example.travlediary.dto.wikidata.WikidataDestinationCandidate;
import com.example.travlediary.dto.wikidata.WikidataDestinationPreview;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.service.category.CountryCategoryService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WikidataDestinationService {

    private static final Pattern QID = Pattern.compile("Q[1-9][0-9]{0,14}");
    /**
     * 등록폼 언어 → Wikidata 언어 코드(앞쪽 우선). 번체(zh-TW)는 대만 표기 zh-tw 가 있으면 쓰고,
     * 없으면 일반 번체 zh-hant 를 쓴다. 두 코드 모두 해당 언어로 직접 입력된 값만 쓰며, 간체 값으로 대체하지 않는다.
     */
    private static final Map<String, List<String>> LANGUAGE_CODES = Map.of(
            "ko", List.of("ko"), "en", List.of("en"), "ja", List.of("ja"),
            "zh-CN", List.of("zh-hans"), "zh-TW", List.of("zh-tw", "zh-hant"));
    /**
     * 간단 설명 언어 코드. 간체는 일반 간체 zh-hans, 없으면 중국 대륙 표기 zh-cn 을 쓴다.
     * 표기가 정해지지 않은 zh 설명은 {@link #CHINESE_SCRIPT_CHECK}로 원문 표기를 확인한 경우에만 원문으로 쓴다.
     */
    private static final Map<String, List<String>> DESCRIPTION_LANGUAGE_CODES = Map.of(
            "ko", List.of("ko"), "en", List.of("en"), "ja", List.of("ja"),
            "zh-CN", List.of("zh-hans", "zh-cn"), "zh-TW", List.of("zh-tw", "zh-hant"));
    /**
     * 중국어 Wikipedia 변환기의 표기 기준. 바꿔도 zh 원문이 그대로면 이미 그 표기로 쓰인 원문이고,
     * 그 표기의 원문이 끝내 없을 때만 반대 표기 원문을 이 기준으로 바꾼 문장을 '중국어 표기 자동 변환'으로 쓴다.
     */
    private static final Map<String, String> CHINESE_SCRIPT_CHECK = Map.of("zh-CN", "zh-hans", "zh-TW", "zh-hant");
    private static final Map<String, String> OPPOSITE_CHINESE = Map.of("zh-CN", "zh-TW", "zh-TW", "zh-CN");
    /** Wikidata 설명 한도. 변환 결과가 이보다 길면 쓰지 않는다. */
    private static final int SHORT_DESCRIPTION_MAX_LENGTH = 250;
    private static final int MAX_LOCATION_LEVELS = 4;

    private static final int MAX_SEARCH_RESULTS = 10;
    /** 유형별 상세정보의 homepage_url · contact_number 최대 길이(VARCHAR(255)). 유형별로 더 짧은 칸은 화면에서 거른다. */
    private static final int HOMEPAGE_MAX_LENGTH = 255;
    private static final int CONTACT_MAX_LENGTH = 255;
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9][0-9 ()./-]{3,60}");
    /** 공식 웹사이트 언어 우선순위: 한국어(Q9176) → 영어(Q1860·영국 영어 Q7979·미국 영어 Q7976, 같은 순위). */
    private static final List<Set<String>> WEBSITE_LANGUAGE_PREFERENCE = List.of(
            Set.of("Q9176"), Set.of("Q1860", "Q7979", "Q7976"));

    private final WikidataApiClient apiClient;
    private final CountryCategoryService countryCategoryService;
    private final WikidataAutofillCache autofillCache;
    private final WikipediaApiClient wikipediaApiClient;

    /**
     * 검색 결과 목록을 먼저 보여주기 위한 최소 정보(QID·이름·설명). 외부 호출은 검색 1회뿐이다.
     * 국가·지역·이미지와 장소 여부는 {@link #searchDetails(List)}가 따로 채운다.
     */
    public List<WikidataDestinationCandidate> quickSearch(String keyword) {
        String query = searchQuery(keyword);
        return toCandidates(apiClient.searchHits(query, searchLanguage(query)));
    }

    /** 검색 결과 한 페이지(최소 정보)와 다음 페이지 위치. 해외 일괄 등록의 '검색 결과 더 보기'가 쓴다. */
    public QuickSearchPage quickSearchPage(String keyword, int offset) {
        String query = searchQuery(keyword);
        WikidataApiClient.SearchPage page = apiClient.searchPage(query, searchLanguage(query), offset);
        return new QuickSearchPage(toCandidates(page.hits()), page.nextOffset());
    }

    private String searchQuery(String keyword) {
        String query = keyword == null ? "" : keyword.strip();
        if (query.length() < 2 || query.length() > 100) {
            throw new IllegalArgumentException("검색어를 2~100자로 입력해 주세요.");
        }
        return query;
    }

    private String searchLanguage(String query) {
        return query.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HANGUL)
                ? "ko" : "en";
    }

    private List<WikidataDestinationCandidate> toCandidates(List<WikidataApiClient.SearchHit> hits) {
        return hits.stream()
                .map(hit -> new WikidataDestinationCandidate(hit.qid(), hit.label(), hit.labelLanguage(),
                        hit.description(), hit.descriptionLanguage(), null, null, null, null))
                .toList();
    }

    public record QuickSearchPage(List<WikidataDestinationCandidate> candidates, Integer nextOffset) {
    }

    /**
     * 검색 후보의 국가·지역·대표 이미지. 지리 정보가 없는 항목은 빼고 돌려준다.
     * 받은 엔티티는 자동입력 캐시에 남아, 후보를 고르면 기본정보·Wikipedia·Commons가 다시 받지 않는다.
     */
    public List<WikidataDestinationCandidate> searchDetails(List<String> requestedQids) {
        List<String> qids = requestedQids == null ? List.of() : requestedQids.stream()
                .map(qid -> qid == null ? "" : qid.strip().toUpperCase())
                .distinct().toList();
        if (qids.isEmpty() || qids.size() > MAX_SEARCH_RESULTS
                || qids.stream().anyMatch(qid -> !QID.matcher(qid).matches())) {
            throw new IllegalArgumentException("올바른 Wikidata QID 목록이 아닙니다.");
        }
        Map<String, JsonNode> entities = autofillCache.entities(qids);
        Set<String> referenceQids = new LinkedHashSet<>();
        for (JsonNode entity : entities.values()) {
            referenceQids.addAll(claimEntityIds(entity, "P17"));
            referenceQids.addAll(claimEntityIds(entity, "P131"));
        }
        Map<String, JsonNode> references = referenceQids.isEmpty()
                ? Map.of() : autofillCache.labels(List.copyOf(referenceQids));

        List<WikidataDestinationCandidate> candidates = new ArrayList<>();
        for (String qid : qids) {
            JsonNode entity = entities.get(qid);
            if (entity == null || !hasGeographicEvidence(entity)) {
                continue;
            }
            DisplayText name = displayText(entity, "labels");
            DisplayText description = displayText(entity, "descriptions");
            String countryQid = onlyValue(claimEntityIds(entity, "P17"));
            String regionQid = onlyValue(claimEntityIds(entity, "P131"));
            String imageFileName = claimString(entity, "P18");
            candidates.add(new WikidataDestinationCandidate(
                    qid, name.value(), name.languageCode(),
                    description.value(), description.languageCode(),
                    displayText(references.get(countryQid), "labels").value(),
                    displayText(references.get(regionQid), "labels").value(),
                    imageFileName, imageUrl(imageFileName)));
        }
        return List.copyOf(candidates);
    }

    /** 저장 전 재검증용. 캐시 없이 항상 Wikidata에서 새로 조회한다. */
    public WikidataDestinationPreview preview(String qid) {
        String normalizedQid = qid == null ? "" : qid.strip().toUpperCase();
        if (!QID.matcher(normalizedQid).matches()) {
            throw new IllegalArgumentException("올바른 Wikidata QID를 입력해 주세요.");
        }
        return revalidate(normalizedQid, apiClient.getAutofillEntities(List.of(normalizedQid)).get(normalizedQid));
    }

    /**
     * 저장 전 재검증. 이번 등록 요청에서 새로 받은 엔티티(freshEntity)를 쓰고, 국가·상위 지역도 캐시 없이 새로 조회한다.
     * 국가 조회는 상위 지역 탐색과 병렬로 진행하고, 상위 지역은 전체 claims 대신 라벨과 P131만 받는다.
     */
    public WikidataDestinationPreview revalidate(String qid, JsonNode freshEntity) {
        return preview(qid, new EntitySource() {
            public JsonNode entity(String id) {
                return freshEntity;
            }

            public CompletableFuture<JsonNode> country(String id) {
                return autofillCache.async(() -> apiClient.getEntities(List.of(id), false).get(id));
            }

            public JsonNode region(String id) {
                CompletableFuture<JsonNode> claims = autofillCache.async(() -> apiClient.getClaims(id, "P131"));
                JsonNode labels = apiClient.getEntities(List.of(id), false).get(id);
                return labels == null ? null
                        : WikidataAutofillCache.withParentRegionClaims(labels, WikidataAutofillCache.join(claims));
            }
        });
    }

    /**
     * 등록폼 자동입력용. 검색 상세·Wikipedia·Commons와 같은 캐시 엔티티를 쓰고,
     * 국가 조회를 상위 지역 탐색과 병렬로 진행한다. 판정 규칙은 {@link #preview(String)}와 같다.
     */
    public WikidataDestinationPreview previewForAutofill(String qid) {
        return preview(qid, new EntitySource() {
            public JsonNode entity(String id) {
                return autofillCache.entity(id);
            }

            public CompletableFuture<JsonNode> country(String id) {
                return autofillCache.async(() -> autofillCache.labels(List.of(id)).get(id));
            }

            public JsonNode region(String id) {
                return autofillCache.region(id);
            }

            public boolean completesChineseDescriptions() {
                return true;
            }
        });
    }

    private WikidataDestinationPreview preview(String qid, EntitySource source) {
        String normalizedQid = qid == null ? "" : qid.strip().toUpperCase();
        if (!QID.matcher(normalizedQid).matches()) {
            throw new IllegalArgumentException("올바른 Wikidata QID를 입력해 주세요.");
        }
        JsonNode entity = source.entity(normalizedQid);
        if (entity == null) {
            throw new java.util.NoSuchElementException("Wikidata 항목을 찾지 못했습니다.");
        }

        String countryQid = onlyValue(claimEntityIds(entity, "P17"));
        CompletableFuture<JsonNode> country = countryQid == null
                ? CompletableFuture.completedFuture(null) : source.country(countryQid);
        Map<String, String> shortDescriptions = new LinkedHashMap<>(
                localizedValues(entity, "descriptions", DESCRIPTION_LANGUAGE_CODES));
        // 중국어 표기 확인·변환은 상위 지역 탐색과 병렬로 진행한다.
        CompletableFuture<ChineseDescriptions> chinese = source.completesChineseDescriptions()
                ? completeChineseDescriptions(entity)
                : CompletableFuture.completedFuture(new ChineseDescriptions(Map.of(), Map.of()));
        List<JsonNode> regionEntities = locationChain(entity, source);
        JsonNode countryEntity = WikidataAutofillCache.join(country);
        ChineseDescriptions chineseDescriptions = WikidataAutofillCache.join(chinese);
        shortDescriptions.putAll(chineseDescriptions.values());
        String imageFileName = claimString(entity, "P18");
        JsonNode coordinate = claimValue(entity, "P625");
        Double latitude = coordinate.path("latitude").isNumber()
                ? coordinate.path("latitude").asDouble() : null;
        Double longitude = coordinate.path("longitude").isNumber()
                ? coordinate.path("longitude").asDouble() : null;

        return new WikidataDestinationPreview(
                normalizedQid,
                localizedValues(entity, "labels", LANGUAGE_CODES),
                Map.copyOf(shortDescriptions),
                countryQid,
                displayText(countryEntity, "labels").value(),
                regionEntities.stream().map(region -> displayText(region, "labels").value())
                        .filter(Objects::nonNull).toList(),
                latitude, longitude, imageFileName, imageUrl(imageFileName),
                imagePageUrl(imageFileName),
                matchRegion(countryEntity, regionEntities),
                travelInfo(entity),
                chineseDescriptions.conversions());
    }

    private WikidataDestinationPreview.TravelInfo travelInfo(JsonNode entity) {
        return new WikidataDestinationPreview.TravelInfo(officialWebsite(entity), phoneNumber(entity),
                !activeStatements(entity, "P3025").isEmpty(), !activeStatements(entity, "P2555").isEmpty());
    }

    /**
     * 공식 웹사이트(P856). 여러 언어 페이지가 있으면 preferred 순위를 먼저 보고,
     * 그 안에서 한국어 → 영어 → 언어 표시 없는 페이지 → 첫 값 순으로 고른다.
     * http/https 절대 주소이고 저장 칸(255자)에 들어갈 때만 쓴다.
     */
    private String officialWebsite(JsonNode entity) {
        List<JsonNode> statements = preferredFirst(activeStatements(entity, "P856"));
        JsonNode chosen = null;
        for (Set<String> languages : WEBSITE_LANGUAGE_PREFERENCE) {
            chosen = statements.stream().filter(statement -> {
                String language = websiteLanguage(statement);
                return language != null && languages.contains(language);
            }).findFirst().orElse(null);
            if (chosen != null) break;
        }
        if (chosen == null) {
            chosen = statements.stream().filter(statement -> websiteLanguage(statement) == null)
                    .findFirst().orElse(statements.isEmpty() ? null : statements.get(0));
        }
        String url = chosen == null ? null : chosen.path("mainsnak").path("datavalue").path("value").asText("").strip();
        if (url == null || url.isEmpty() || url.length() > HOMEPAGE_MAX_LENGTH) return null;
        try {
            java.net.URI uri = java.net.URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            return ("http".equals(scheme) || "https".equals(scheme)) && uri.getHost() != null
                    && uri.getRawUserInfo() == null ? url : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String websiteLanguage(JsonNode statement) {
        for (JsonNode qualifier : statement.path("qualifiers").path("P407")) {
            String id = qualifier.path("datavalue").path("value").path("id").asText(null);
            if (id != null) return id;
        }
        return null;
    }

    /** 전화번호(P1329). preferred 순위를 먼저 보고, 숫자·공백·+()-./ 로만 된 값만 쓴다. */
    private String phoneNumber(JsonNode entity) {
        return preferredFirst(activeStatements(entity, "P1329")).stream()
                .map(statement -> statement.path("mainsnak").path("datavalue").path("value").asText("").strip())
                .filter(value -> value.length() <= CONTACT_MAX_LENGTH && PHONE.matcher(value).matches())
                .findFirst().orElse(null);
    }

    private List<JsonNode> activeStatements(JsonNode entity, String property) {
        List<JsonNode> statements = new ArrayList<>();
        for (JsonNode statement : entity.path("claims").path(property)) {
            if (!"deprecated".equals(statement.path("rank").asText())
                    && !statement.path("mainsnak").path("datavalue").path("value").isMissingNode()) {
                statements.add(statement);
            }
        }
        return statements;
    }

    private List<JsonNode> preferredFirst(List<JsonNode> statements) {
        List<JsonNode> preferred = statements.stream()
                .filter(statement -> "preferred".equals(statement.path("rank").asText())).toList();
        return preferred.isEmpty() ? statements : preferred;
    }

    /** 상위 지역은 앞 단계의 P131을 알아야 다음을 찾을 수 있어 순서대로 탐색한다. */
    private List<JsonNode> locationChain(JsonNode entity, EntitySource source) {
        List<JsonNode> chain = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        String nextQid = onlyValue(claimEntityIds(entity, "P131"));
        while (nextQid != null && visited.add(nextQid) && chain.size() < MAX_LOCATION_LEVELS) {
            JsonNode region = source.region(nextQid);
            if (region == null) {
                break;
            }
            chain.add(region);
            nextQid = onlyValue(claimEntityIds(region, "P131"));
        }
        return List.copyOf(chain);
    }

    private WikidataDestinationPreview.RegionMatch matchRegion(
            JsonNode countryEntity, List<JsonNode> regionEntities) {
        if (countryEntity == null) {
            return review(null, "국가 정보가 없거나 여러 국가가 지정되어 지역을 확인해 주세요.");
        }
        List<CountryCategory> countries = countryCategoryService.getRegionsByDepth(2).stream()
                .filter(country -> namesMatch(country, countryEntity))
                .toList();
        if (countries.size() != 1) {
            return review(null, "국가가 기존 지역과 유일하게 일치하지 않습니다. 직접 확인해 주세요.");
        }
        Long countryId = countries.get(0).getId();
        List<CountryCategory> matchedRegions = countryCategoryService.getRegionsByParentId(countryId).stream()
                .filter(region -> regionEntities.stream().anyMatch(source -> namesMatch(region, source)))
                .toList();
        if (matchedRegions.size() != 1) {
            return review(countryId, "도시·지역이 기존 지역과 유일하게 일치하지 않습니다. 직접 확인해 주세요.");
        }
        CountryCategory matchedRegion = matchedRegions.get(0);
        List<CountryCategory> path = countryCategoryService.getRegionPath(matchedRegion.getId());
        if (path.size() != 3
                || !countryId.equals(path.get(1).getId())
                || !matchedRegion.getId().equals(path.get(2).getId())
                || !path.get(0).getId().equals(path.get(1).getParentId())
                || !countryId.equals(path.get(2).getParentId())) {
            return review(countryId, "기존 지역의 전체 경로를 확인하지 못했습니다. 직접 선택해 주세요.");
        }
        return new WikidataDestinationPreview.RegionMatch(
                countryId, matchedRegion.getId(), true,
                "국가와 도시 이름이 기존 지역에 각각 유일하게 일치합니다. 등록 전 확인해 주세요.",
                path.stream().map(region -> new WikidataDestinationPreview.RegionMatch.RegionPathItem(
                        region.getId(), region.getRegionName())).toList());
    }

    private WikidataDestinationPreview.RegionMatch review(Long countryId, String message) {
        return new WikidataDestinationPreview.RegionMatch(countryId, null, false, message, List.of());
    }

    private boolean namesMatch(CountryCategory category, JsonNode entity) {
        String korean = directValue(entity, "labels", "ko");
        String english = directValue(entity, "labels", "en");
        return korean != null && korean.equals(category.getRegionName())
                || english != null && english.equalsIgnoreCase(category.getNameEn());
    }

    /** 중국어 간단 설명 원문. code 는 Wikidata 언어 코드(zh-hans·zh-cn·zh-tw·zh-hant·zh). */
    private record ChineseOriginal(String code, String text) {
    }

    /**
     * values: 보완한 zh-CN·zh-TW 간단 설명, conversions: 그중 반대 표기에서 변환한 언어 → 변환 전 원문의 언어 코드.
     */
    private record ChineseDescriptions(Map<String, String> values, Map<String, String> conversions) {
    }

    /**
     * 빈 간체·번체 간단 설명을 보완한다. 해당 표기의 원문이 끝내 없을 때만 변환 문장을 쓴다.
     * 빈 언어마다 변환기를 한 번만 부른다. zh 원문과 반대 표기 원문을 한 요청에 넣어
     * ① zh 원문이 변환해도 그대로면 그 표기의 원문(zh)으로 쓰고, ② 아니면 반대 표기 원문의 변환 문장을 쓴다.
     * 변환 요청이 실패하거나 결과가 {@link #SHORT_DESCRIPTION_MAX_LENGTH}자를 넘으면 비워 둔다.
     */
    private CompletableFuture<ChineseDescriptions> completeChineseDescriptions(JsonNode entity) {
        Map<String, ChineseOriginal> originals = new LinkedHashMap<>();
        for (String language : List.of("zh-CN", "zh-TW")) {
            for (String code : DESCRIPTION_LANGUAGE_CODES.get(language)) {
                String value = directValue(entity, "descriptions", code);
                if (value != null) {
                    originals.put(language, new ChineseOriginal(code, value));
                    break;
                }
            }
        }
        String generic = directValue(entity, "descriptions", "zh");
        Map<String, CompletableFuture<Map<String, String>>> conversions = new LinkedHashMap<>();
        for (String language : List.of("zh-CN", "zh-TW")) {
            if (originals.containsKey(language)) continue;
            LinkedHashSet<String> texts = new LinkedHashSet<>();
            if (generic != null) texts.add(generic);
            ChineseOriginal opposite = originals.get(OPPOSITE_CHINESE.get(language));
            if (opposite != null) texts.add(opposite.text());
            if (texts.isEmpty()) continue;
            String variant = CHINESE_SCRIPT_CHECK.get(language);
            conversions.put(language, autofillCache.async(() -> convertChinese(List.copyOf(texts), variant))
                    .exceptionally(failure -> Map.of()));
        }
        return CompletableFuture.allOf(conversions.values().toArray(CompletableFuture[]::new))
                .thenApply(ignored -> resolveChineseDescriptions(originals, generic, conversions));
    }

    private ChineseDescriptions resolveChineseDescriptions(Map<String, ChineseOriginal> originals, String generic,
                                                           Map<String, CompletableFuture<Map<String, String>>> conversions) {
        Map<String, ChineseOriginal> resolved = new LinkedHashMap<>(originals);
        conversions.forEach((language, conversion) -> {
            if (generic == null) return;
            String converted = conversion.join().get(generic);
            if (converted != null && normalizeSpaces(converted).equals(normalizeSpaces(generic))) {
                resolved.put(language, new ChineseOriginal("zh", generic));
            }
        });
        Map<String, String> values = new LinkedHashMap<>();
        resolved.forEach((language, original) -> values.put(language, original.text()));
        Map<String, String> sources = new LinkedHashMap<>();
        conversions.forEach((language, conversion) -> {
            ChineseOriginal opposite = resolved.get(OPPOSITE_CHINESE.get(language));
            if (resolved.containsKey(language) || opposite == null) return;
            String converted = conversion.join().get(opposite.text());
            if (converted == null || converted.isBlank() || converted.length() > SHORT_DESCRIPTION_MAX_LENGTH) return;
            values.put(language, converted);
            sources.put(language, opposite.code());
        });
        return new ChineseDescriptions(Map.copyOf(values), Map.copyOf(sources));
    }

    /** 원문 → 변환 문장. 요청이 실패하면 빈 맵이라 해당 칸은 비워 둔다. */
    private Map<String, String> convertChinese(List<String> texts, String variant) {
        try {
            List<String> converted = wikipediaApiClient.convertChineseScript(texts, variant);
            Map<String, String> result = new LinkedHashMap<>();
            for (int index = 0; index < texts.size(); index++) result.put(texts.get(index), converted.get(index));
            return result;
        } catch (IllegalArgumentException | WikipediaApiException exception) {
            return Map.of();
        }
    }

    private static String normalizeSpaces(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    private Map<String, String> localizedValues(JsonNode entity, String property,
                                                Map<String, List<String>> languageCodes) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> language : languageCodes.entrySet()) {
            language.getValue().stream()
                    .map(code -> directValue(entity, property, code))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .ifPresent(value -> values.put(language.getKey(), value));
        }
        return Map.copyOf(values);
    }

    private DisplayText displayText(JsonNode entity, String property) {
        String korean = directValue(entity, property, "ko");
        if (korean != null) {
            return new DisplayText(korean, "ko");
        }
        String english = directValue(entity, property, "en");
        return new DisplayText(english, english == null ? null : "en");
    }

    private String directValue(JsonNode entity, String property, String language) {
        if (entity == null) {
            return null;
        }
        JsonNode localized = entity.path(property).path(language);
        String sourceLanguage = localized.path("language").asText(null);
        if (sourceLanguage != null && !language.equalsIgnoreCase(sourceLanguage)) {
            return null;
        }
        String value = localized.path("value").asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private boolean hasGeographicEvidence(JsonNode entity) {
        return !claimEntityIds(entity, "P17").isEmpty()
                || !claimEntityIds(entity, "P131").isEmpty()
                || !claimValue(entity, "P625").isMissingNode();
    }

    private List<String> claimEntityIds(JsonNode entity, String property) {
        List<String> ids = new ArrayList<>();
        for (JsonNode statement : entity.path("claims").path(property)) {
            if ("deprecated".equals(statement.path("rank").asText())) {
                continue;
            }
            String id = statement.path("mainsnak").path("datavalue")
                    .path("value").path("id").asText(null);
            if (id != null && QID.matcher(id).matches() && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private JsonNode claimValue(JsonNode entity, String property) {
        JsonNode statements = entity.path("claims").path(property);
        JsonNode first = null;
        for (JsonNode statement : statements) {
            if ("deprecated".equals(statement.path("rank").asText())) {
                continue;
            }
            JsonNode value = statement.path("mainsnak").path("datavalue").path("value");
            if (value.isMissingNode()) {
                continue;
            }
            if ("preferred".equals(statement.path("rank").asText())) {
                return value;
            }
            if (first == null) {
                first = value;
            }
        }
        return first == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : first;
    }

    private String claimString(JsonNode entity, String property) {
        String value = claimValue(entity, property).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private String onlyValue(List<String> values) {
        return values.size() == 1 ? values.get(0) : null;
    }

    private String imageUrl(String fileName) {
        return fileName == null ? null : "https://commons.wikimedia.org/wiki/Special:FilePath/"
                + encodedFileName(fileName) + "?width=240";
    }

    private String imagePageUrl(String fileName) {
        return fileName == null ? null : "https://commons.wikimedia.org/wiki/File:"
                + encodedFileName(fileName);
    }

    private String encodedFileName(String fileName) {
        return UriUtils.encodePathSegment(fileName.replace(' ', '_'), StandardCharsets.UTF_8);
    }

    private record DisplayText(String value, String languageCode) {
    }

    /** 미리보기가 읽는 Wikidata 값의 출처. 재검증은 매번 새로 조회하고, 자동입력은 캐시를 쓴다. */
    private interface EntitySource {
        JsonNode entity(String qid);

        CompletableFuture<JsonNode> country(String qid);

        /** 라벨과 P131 claims가 있는 상위 지역. 없는 항목이면 null. */
        JsonNode region(String qid);

        /** 빈 간체·번체 간단 설명을 zh 원문 확인·반대 표기 변환으로 채울지. 자동입력만 쓰고 저장 재검증은 쓰지 않는다. */
        default boolean completesChineseDescriptions() {
            return false;
        }
    }
}
