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
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WikidataDestinationService {

    private static final Pattern QID = Pattern.compile("Q[1-9][0-9]{0,14}");
    private static final Map<String, String> LANGUAGE_CODES = Map.of(
            "ko", "ko", "en", "en", "ja", "ja", "zh-CN", "zh-hans", "zh-TW", "zh-hant");
    private static final int MAX_LOCATION_LEVELS = 4;

    private final WikidataApiClient apiClient;
    private final CountryCategoryService countryCategoryService;

    public List<WikidataDestinationCandidate> search(String keyword) {
        String query = keyword == null ? "" : keyword.strip();
        if (query.length() < 2 || query.length() > 100) {
            throw new IllegalArgumentException("검색어를 2~100자로 입력해 주세요.");
        }
        String language = query.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HANGUL)
                ? "ko" : "en";
        List<String> qids = apiClient.searchIds(query, language);
        if (qids.isEmpty()) {
            return List.of();
        }
        Map<String, JsonNode> entities = apiClient.getEntities(qids, true);
        Set<String> referenceQids = new LinkedHashSet<>();
        for (JsonNode entity : entities.values()) {
            referenceQids.addAll(claimEntityIds(entity, "P17"));
            referenceQids.addAll(claimEntityIds(entity, "P131"));
        }
        Map<String, JsonNode> references = referenceQids.isEmpty()
                ? Map.of() : apiClient.getEntities(List.copyOf(referenceQids), false);

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

    public WikidataDestinationPreview preview(String qid) {
        String normalizedQid = qid == null ? "" : qid.strip().toUpperCase();
        if (!QID.matcher(normalizedQid).matches()) {
            throw new IllegalArgumentException("올바른 Wikidata QID를 입력해 주세요.");
        }
        JsonNode entity = apiClient.getEntities(List.of(normalizedQid), true).get(normalizedQid);
        if (entity == null) {
            throw new java.util.NoSuchElementException("Wikidata 항목을 찾지 못했습니다.");
        }

        String countryQid = onlyValue(claimEntityIds(entity, "P17"));
        JsonNode countryEntity = countryQid == null ? null
                : apiClient.getEntities(List.of(countryQid), false).get(countryQid);
        List<JsonNode> regionEntities = locationChain(entity);
        String imageFileName = claimString(entity, "P18");
        JsonNode coordinate = claimValue(entity, "P625");
        Double latitude = coordinate.path("latitude").isNumber()
                ? coordinate.path("latitude").asDouble() : null;
        Double longitude = coordinate.path("longitude").isNumber()
                ? coordinate.path("longitude").asDouble() : null;

        return new WikidataDestinationPreview(
                normalizedQid,
                localizedValues(entity, "labels"),
                localizedValues(entity, "descriptions"),
                countryQid,
                displayText(countryEntity, "labels").value(),
                regionEntities.stream().map(region -> displayText(region, "labels").value())
                        .filter(Objects::nonNull).toList(),
                latitude, longitude, imageFileName, imageUrl(imageFileName),
                imagePageUrl(imageFileName),
                matchRegion(countryEntity, regionEntities));
    }

    private List<JsonNode> locationChain(JsonNode entity) {
        List<JsonNode> chain = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        String nextQid = onlyValue(claimEntityIds(entity, "P131"));
        while (nextQid != null && visited.add(nextQid) && chain.size() < MAX_LOCATION_LEVELS) {
            JsonNode region = apiClient.getEntities(List.of(nextQid), true).get(nextQid);
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
        return new WikidataDestinationPreview.RegionMatch(
                countryId, matchedRegions.get(0).getId(), true,
                "국가와 도시 이름이 기존 지역에 각각 유일하게 일치합니다. 등록 전 확인해 주세요.");
    }

    private WikidataDestinationPreview.RegionMatch review(Long countryId, String message) {
        return new WikidataDestinationPreview.RegionMatch(countryId, null, false, message);
    }

    private boolean namesMatch(CountryCategory category, JsonNode entity) {
        String korean = directValue(entity, "labels", "ko");
        String english = directValue(entity, "labels", "en");
        return korean != null && korean.equals(category.getRegionName())
                || english != null && english.equalsIgnoreCase(category.getNameEn());
    }

    private Map<String, String> localizedValues(JsonNode entity, String property) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> language : LANGUAGE_CODES.entrySet()) {
            String value = directValue(entity, property, language.getValue());
            if (value != null) {
                values.put(language.getKey(), value);
            }
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
}
