package com.tripbora.service.kto;

import com.tripbora.dto.kto.KtoTourRegionMatchResponse;
import com.tripbora.model.CountryCategory;
import com.tripbora.service.category.CountryCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KtoTourRegionMatchService {

    private static final Map<String, String> PROVINCE_ALIASES = Map.ofEntries(
            Map.entry("서울특별시", "서울"),
            Map.entry("부산광역시", "부산"),
            Map.entry("대구광역시", "대구"),
            Map.entry("인천광역시", "인천"),
            Map.entry("광주광역시", "광주"),
            Map.entry("대전광역시", "대전"),
            Map.entry("울산광역시", "울산"),
            Map.entry("세종특별자치시", "세종"),
            Map.entry("경기도", "경기"),
            Map.entry("강원특별자치도", "강원"),
            Map.entry("충청북도", "충북"),
            Map.entry("충청남도", "충남"),
            Map.entry("전북특별자치도", "전북"),
            Map.entry("전라북도", "전북"),
            Map.entry("전라남도", "전남"),
            Map.entry("경상북도", "경북"),
            Map.entry("경상남도", "경남"),
            Map.entry("제주특별자치도", "제주")
    );

    private final CountryCategoryService countryCategoryService;

    /**
     * 시·도 정식 명칭("서울특별시")을 지역 데이터의 이름("서울")으로 바꾼다. 별칭이 아니면 그대로 돌려준다.
     * 주소 매칭과 JSON 일괄등록의 국내 지역 매핑이 같은 별칭 표를 쓴다.
     */
    public static String provinceName(String name) {
        return name == null ? null : PROVINCE_ALIASES.getOrDefault(name, name);
    }

    public KtoTourRegionMatchResponse match(String address) {
        List<String> tokens = addressTokens(address);
        if (tokens.isEmpty()) {
            return KtoTourRegionMatchResponse.unmatched();
        }

        Long koreaRootId = countryCategoryService.getKoreaRootId();
        if (koreaRootId == null) {
            return KtoTourRegionMatchResponse.unmatched();
        }
        CountryCategory current = countryCategoryService.getById(koreaRootId);
        if (current == null) {
            return KtoTourRegionMatchResponse.unmatched();
        }

        List<KtoTourRegionMatchResponse.RegionPathItem> path = new ArrayList<>();
        path.add(pathItem(current));

        int tokenIndex = 0;
        while (true) {
            List<CountryCategory> children = safeChildren(current.getId());
            if (children.isEmpty()) {
                return KtoTourRegionMatchResponse.matched(path);
            }
            if (tokenIndex >= tokens.size()) {
                return KtoTourRegionMatchResponse.unmatched();
            }

            String addressRegionName = tokenIndex == 0
                    ? PROVINCE_ALIASES.getOrDefault(tokens.get(tokenIndex), tokens.get(tokenIndex))
                    : tokens.get(tokenIndex);
            List<CountryCategory> matches = children.stream()
                    .filter(child -> addressRegionName.equals(normalize(child.getRegionName())))
                    .toList();
            if (matches.size() != 1) {
                return KtoTourRegionMatchResponse.unmatched();
            }

            current = matches.get(0);
            path.add(pathItem(current));
            tokenIndex++;
        }
    }

    private List<String> addressTokens(String address) {
        if (address == null || address.isBlank()) {
            return List.of();
        }
        return Arrays.stream(address.strip().split("\\s+"))
                .map(this::normalize)
                .filter(token -> !token.isEmpty())
                .toList();
    }

    private List<CountryCategory> safeChildren(Long parentId) {
        List<CountryCategory> children = countryCategoryService.getRegionsByParentId(parentId);
        return children == null ? List.of() : children;
    }

    private KtoTourRegionMatchResponse.RegionPathItem pathItem(CountryCategory region) {
        return new KtoTourRegionMatchResponse.RegionPathItem(region.getId(), region.getRegionName());
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
