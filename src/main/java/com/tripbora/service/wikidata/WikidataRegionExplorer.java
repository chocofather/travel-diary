package com.tripbora.service.wikidata;

import com.tripbora.model.CountryCategory;
import com.tripbora.service.category.CountryCategoryService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 해외 일괄 등록의 지역별 탐색. 기존 지역(country_categories)을 Wikidata 행정구역에 매핑하고,
 * 그 행정구역에 속한 여행 관련 장소를 Wikidata 공식 SPARQL로 찾는다.
 *
 * <p>지역 매핑 근거(추측하지 않는다)</p>
 * <ol>
 *   <li>국가: 국가 코드 = ISO 3166-1(P297)</li>
 *   <li>하위 지역: 지역 코드 = ISO 3166-2(P300)이고 같은 국가(P17)에 속한 항목. 예: JP-40 → 후쿠오카현</li>
 *   <li>그 밖(도시형 지역): 같은 국가에 속한 현존 행정구역·정착지 중 한국어 이름이 정확히 같은 항목이 하나뿐이면 그 항목,
 *       한국어 일치가 없으면 영어 이름이 정확히 같은 항목이 하나뿐일 때만. 여럿이거나 없으면 확정하지 않는다.</li>
 * </ol>
 *
 * <p>후보 범위: 행정구역(하위 구역 포함, P131 경로)에 속하고 좌표(P625)가 있는 현존 장소를 인지도(위키 사이트링크 수) 순
 * {@value #SCAN_LIMIT}곳까지 본 뒤, 여행 관련 분류(하위 분류 포함)나 문화재 지정(P1435)이 있는 곳만
 * {@value #RESULT_LIMIT}곳까지 남긴다. 인물·문서·학교처럼 분류가 맞지 않는 항목은 빠진다.</p>
 */
@Service
@RequiredArgsConstructor
public class WikidataRegionExplorer {
    /**
     * 인지도 순으로 먼저 추리는 장소 수. 2026-09 측정: 2,000이면 첫 조회가 후쿠오카현·파리에서 25~35초까지 걸려,
     * 절반 수준(오사카시 약 3초, 241곳)인 1,000으로 둔다.
     */
    static final int SCAN_LIMIT = 1000;
    static final int RESULT_LIMIT = 300;
    private static final Pattern QID = Pattern.compile("Q[1-9][0-9]{0,14}");
    private static final Pattern ISO_ALPHA2 = Pattern.compile("[A-Z]{2}");
    private static final Pattern ISO_SUBDIVISION = Pattern.compile("[A-Z]{2}-[A-Z0-9]{1,3}");
    /** 여행 관련 분류. 하위 분류(P279)로 지정된 장소도 포함한다. */
    static final List<String> TRAVEL_CLASSES = List.of(
            "Q570116",  // tourist attraction
            "Q2319498", // architectural landmark
            "Q33506",   // museum
            "Q839954",  // archaeological site
            "Q1081138", // historic site
            "Q22698",   // park
            "Q1440300", // observation tower
            "Q23413",   // castle
            "Q845945",  // Shinto shrine
            "Q5393308", // Buddhist temple
            "Q1107656", // garden
            "Q43501",   // zoo
            "Q2281788", // public aquarium
            "Q194195",  // amusement park
            "Q40080",   // beach
            "Q34038",   // waterfall
            "Q4989906", // monument
            "Q12518",   // tower
            "Q16970",   // church building
            "Q16560",   // palace
            "Q57821",   // fortification
            "Q46169",   // national park
            "Q179049",  // nature reserve
            "Q15243209",// historic district
            "Q177380",  // hot spring
            "Q11315"    // shopping center
    );

    private final WikidataSparqlClient sparqlClient;
    private final CountryCategoryService countryCategoryService;
    private final WikidataAutofillCache.ExpiringCache<String> countries =
            new WikidataAutofillCache.ExpiringCache<>(Duration.ofHours(24), 300, Clock.systemUTC());
    private final WikidataAutofillCache.ExpiringCache<AreaMatch> areas =
            new WikidataAutofillCache.ExpiringCache<>(Duration.ofHours(12), 500, Clock.systemUTC());
    private final WikidataAutofillCache.ExpiringCache<List<Area>> municipalities =
            new WikidataAutofillCache.ExpiringCache<>(Duration.ofHours(12), 500, Clock.systemUTC());
    private final WikidataAutofillCache.ExpiringCache<List<String>> places =
            new WikidataAutofillCache.ExpiringCache<>(Duration.ofMinutes(30), 200, Clock.systemUTC());

    public record Area(String qid, String name) {
    }

    /** method: ISO_3166_2 · NAME, qid 가 null 이면 확정하지 못한 것이다(message 에 이유). */
    record AreaMatch(String qid, String name, String method, String message) {
    }

    /**
     * @param qid            매핑된 Wikidata 행정구역. 확정하지 못했으면 null
     * @param basis          매핑 근거(관리자 안내용)
     * @param municipalities 이 지역 안에서 더 좁혀 볼 수 있는 기초자치단체(시)
     */
    public record Resolution(Long regionId, String regionName, String qid, String wikidataName, String basis,
                             String message, List<Area> municipalities) {
    }

    /** 여행 관련 장소 QID(인지도 순). limited 가 true 면 결과 상한에 걸려 더 있을 수 있다. */
    public record Places(String areaQid, List<String> qids, boolean limited) {
    }

    /** 기존 지역을 Wikidata 행정구역으로 매핑하고, 좁혀 볼 수 있는 하위 시 목록을 함께 돌려준다. */
    public Resolution resolve(Long regionId) {
        List<CountryCategory> path = regionId == null ? List.of() : countryCategoryService.getRegionPath(regionId);
        if (path.isEmpty() || path.get(path.size() - 1) == null || !Objects.equals(path.get(path.size() - 1).getId(), regionId)) {
            throw new IllegalArgumentException("지역을 찾지 못했습니다.");
        }
        if (!countryCategoryService.getOverseasRootIds().contains(path.get(0).getId())) {
            throw new IllegalArgumentException("해외 지역만 탐색할 수 있습니다.");
        }
        CountryCategory region = path.get(path.size() - 1);
        if (path.size() < 3) {
            throw new IllegalArgumentException("국가 전체는 범위가 넓어 탐색하지 않습니다. 국가 아래 지역을 선택해 주세요.");
        }
        CountryCategory country = path.get(1);
        String countryQid = countryQid(country);
        if (countryQid == null) {
            return new Resolution(regionId, region.getRegionName(), null, null, null,
                    "국가 코드로 Wikidata 국가를 확정하지 못했습니다.", List.of());
        }
        AreaMatch match = areas.get("area:" + regionId + ":" + region.getCode() + ":" + region.getRegionName()
                + ":" + region.getNameEn(), () -> matchArea(region, countryQid));
        if (match.qid() == null) {
            return new Resolution(regionId, region.getRegionName(), null, null, null, match.message(), List.of());
        }
        return new Resolution(regionId, region.getRegionName(), match.qid(), match.name(),
                "ISO_3166_2".equals(match.method())
                        ? "ISO 3166-2 지역 코드 " + region.getCode() + " 일치"
                        : "같은 국가의 행정구역 중 이름이 유일하게 일치",
                null, municipalities(match.qid()));
    }

    /**
     * 지역 전체(cityQid 없음) 또는 그 안의 시 한 곳의 여행 관련 장소. 시는 {@link #resolve}가 돌려준 하위 시만 받는다.
     */
    public Places places(Long regionId, String cityQid) {
        Resolution resolution = resolve(regionId);
        if (resolution.qid() == null) throw new IllegalArgumentException(resolution.message());
        String areaQid = resolution.qid();
        if (cityQid != null && !cityQid.isBlank()) {
            String normalized = cityQid.strip().toUpperCase();
            if (resolution.municipalities().stream().noneMatch(area -> area.qid().equals(normalized))) {
                throw new IllegalArgumentException("선택한 지역에 속한 시가 아닙니다. 지역을 다시 선택해 주세요.");
            }
            areaQid = normalized;
        }
        String area = areaQid;
        List<String> qids = places.get("places:" + area, () -> placeQids(area));
        return new Places(area, qids, qids.size() >= RESULT_LIMIT);
    }

    private String countryQid(CountryCategory country) {
        String code = country.getCode() == null ? "" : country.getCode().strip().toUpperCase();
        if (!ISO_ALPHA2.matcher(code).matches()) return null;
        return countries.get("country:" + code, () -> {
            JsonNode rows = sparqlClient.select("SELECT ?c WHERE { ?c wdt:P297 \"" + code + "\" . "
                    + "FILTER NOT EXISTS { ?c wdt:P576 ?end . } }");
            List<String> qids = qids(rows, "c");
            return qids.size() == 1 ? qids.get(0) : null;
        });
    }

    private AreaMatch matchArea(CountryCategory region, String countryQid) {
        String code = region.getCode() == null ? "" : region.getCode().strip().toUpperCase();
        if (ISO_SUBDIVISION.matcher(code).matches()) {
            JsonNode rows = sparqlClient.select("SELECT DISTINCT ?r ?ko ?en WHERE { ?r wdt:P300 \"" + code + "\" ; "
                    + "wdt:P17 wd:" + countryQid + " . FILTER NOT EXISTS { ?r wdt:P576 ?end . } "
                    + labels("r") + " }");
            List<String> qids = qids(rows, "r");
            if (qids.size() == 1) return new AreaMatch(qids.get(0), label(rows.get(0)), "ISO_3166_2", null);
        }
        String korean = literal(region.getRegionName());
        String english = literal(region.getNameEn());
        if (korean == null && english == null) {
            return new AreaMatch(null, null, null, "지역 이름이 없어 Wikidata 행정구역을 확정하지 못했습니다.");
        }
        List<String> unions = new ArrayList<>();
        if (korean != null) unions.add("{ ?r rdfs:label \"" + korean + "\"@ko . }");
        if (english != null) unions.add("{ ?r rdfs:label \"" + english + "\"@en . }");
        JsonNode rows = sparqlClient.select("SELECT DISTINCT ?r ?ko ?en WHERE { hint:Query hint:optimizer \"None\" . "
                + String.join(" UNION ", unions) + " ?r wdt:P17 wd:" + countryQid + " . "
                + "FILTER(EXISTS { ?r wdt:P31/wdt:P279* wd:Q486972 . } || EXISTS { ?r wdt:P31/wdt:P279* wd:Q56061 . }) "
                + "FILTER NOT EXISTS { ?r wdt:P576 ?end . } " + labels("r") + " }");
        Map<String, String[]> candidates = new LinkedHashMap<>();
        for (JsonNode row : rows) {
            String qid = qid(row, "r");
            if (qid != null) candidates.putIfAbsent(qid, new String[]{text(row, "ko"), text(row, "en")});
        }
        List<String> koreanMatches = candidates.entrySet().stream()
                .filter(entry -> region.getRegionName() != null && region.getRegionName().strip().equals(entry.getValue()[0]))
                .map(Map.Entry::getKey).toList();
        List<String> englishMatches = candidates.entrySet().stream()
                .filter(entry -> region.getNameEn() != null && region.getNameEn().strip().equals(entry.getValue()[1]))
                .map(Map.Entry::getKey).toList();
        String chosen = koreanMatches.size() == 1 ? koreanMatches.get(0)
                : koreanMatches.isEmpty() && englishMatches.size() == 1 ? englishMatches.get(0) : null;
        if (chosen == null) {
            return new AreaMatch(null, null, null, candidates.isEmpty()
                    ? "같은 국가에서 이름이 일치하는 Wikidata 행정구역을 찾지 못했습니다."
                    : "이름이 같은 Wikidata 행정구역이 여러 곳이라 확정하지 않았습니다.");
        }
        String[] names = candidates.get(chosen);
        return new AreaMatch(chosen, names[0] != null ? names[0] : names[1], "NAME", null);
    }

    /** 행정구역 바로 아래의 현존 기초자치단체(municipality 하위 분류). 좌표가 있는 곳만. */
    private List<Area> municipalities(String areaQid) {
        return municipalities.get("municipalities:" + areaQid, () -> {
            JsonNode rows = sparqlClient.select("SELECT DISTINCT ?m ?ko ?ja ?en WHERE { hint:Query hint:optimizer \"None\" . "
                    + "?m wdt:P131 wd:" + areaQid + " . "
                    + "FILTER EXISTS { ?m wdt:P31/wdt:P279* wd:Q15284 . } "
                    + "FILTER NOT EXISTS { ?m wdt:P576 ?end . } ?m wdt:P625 ?coord . "
                    + labels("m") + " OPTIONAL { ?m rdfs:label ?ja . FILTER(LANG(?ja) = \"ja\") } }");
            Map<String, Area> found = new LinkedHashMap<>();
            for (JsonNode row : rows) {
                String qid = qid(row, "m");
                String name = firstText(text(row, "ko"), text(row, "ja"), text(row, "en"));
                if (qid != null && name != null) found.putIfAbsent(qid, new Area(qid, name));
            }
            return found.values().stream()
                    .sorted((left, right) -> left.name().compareTo(right.name()))
                    .collect(Collectors.toUnmodifiableList());
        });
    }

    /**
     * 행정구역(하위 구역 포함)의 장소를 인지도 순으로 먼저 추린 뒤 여행 관련 분류만 남긴다.
     * 분류의 하위 분류 탐색을 전체 지역에 바로 걸면 WDQS 제한 시간(60초)을 넘기므로 순서를 고정한다.
     */
    private List<String> placeQids(String areaQid) {
        String classes = TRAVEL_CLASSES.stream().map(qid -> "wd:" + qid).collect(Collectors.joining(" "));
        JsonNode rows = sparqlClient.select("SELECT ?item ?links WHERE { hint:Query hint:optimizer \"None\" . "
                + "{ SELECT DISTINCT ?item ?links WHERE { ?item wdt:P131* wd:" + areaQid + " . "
                + "?item wdt:P625 ?coord . ?item wikibase:sitelinks ?links . "
                + "FILTER NOT EXISTS { ?item wdt:P576 ?end . } } ORDER BY DESC(?links) LIMIT " + SCAN_LIMIT + " } "
                + "FILTER(EXISTS { ?item wdt:P31/wdt:P279* ?class . VALUES ?class { " + classes + " } } "
                + "|| EXISTS { ?item wdt:P1435 ?heritage . }) } ORDER BY DESC(?links) LIMIT " + RESULT_LIMIT);
        return List.copyOf(new ArrayList<>(new java.util.LinkedHashSet<>(qids(rows, "item"))));
    }

    private String labels(String variable) {
        return "OPTIONAL { ?" + variable + " rdfs:label ?ko . FILTER(LANG(?ko) = \"ko\") } "
                + "OPTIONAL { ?" + variable + " rdfs:label ?en . FILTER(LANG(?en) = \"en\") }";
    }

    private List<String> qids(JsonNode rows, String variable) {
        List<String> qids = new ArrayList<>();
        for (JsonNode row : rows) {
            String qid = qid(row, variable);
            if (qid != null && !qids.contains(qid)) qids.add(qid);
        }
        return qids;
    }

    private String qid(JsonNode row, String variable) {
        String uri = row.path(variable).path("value").asText("");
        String qid = uri.substring(uri.lastIndexOf('/') + 1);
        return QID.matcher(qid).matches() ? qid : null;
    }

    private String label(JsonNode row) {
        return firstText(text(row, "ko"), text(row, "en"));
    }

    private String text(JsonNode row, String variable) {
        String value = row.path(variable).path("value").asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    /** SPARQL 문자열 안에 넣을 수 있는 이름만 쓴다. 따옴표·역슬래시·줄바꿈이 있으면 쓰지 않는다. */
    private String literal(String value) {
        if (value == null || value.isBlank()) return null;
        String stripped = value.strip();
        return stripped.length() > 100 || stripped.chars().anyMatch(ch -> ch == '"' || ch == '\\' || ch < 0x20)
                ? null : stripped;
    }

    private String firstText(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }
}
