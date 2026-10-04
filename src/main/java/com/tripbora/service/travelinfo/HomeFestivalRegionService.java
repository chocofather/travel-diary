package com.tripbora.service.travelinfo;

import com.tripbora.config.i18n.SupportedLanguage;
import com.tripbora.dto.HomeFestivalDto;
import com.tripbora.dto.kto.KtoTourRegionMatchResponse.RegionPathItem;
import com.tripbora.service.category.ReferenceNameLocalizationService;
import com.tripbora.service.kto.KtoTourRegionMatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 주소에서 임의로 단어를 자르지 않고 기존 지역 트리에 확인된 이름만 표시한다. */
@Service
@RequiredArgsConstructor
public class HomeFestivalRegionService {
    private final KtoTourRegionMatchService regionMatchService;
    private final ReferenceNameLocalizationService nameLocalizationService;

    public Map<Long, String> resolveLocations(List<HomeFestivalDto> festivals, SupportedLanguage language) {
        Map<Long, List<RegionPathItem>> paths = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        for (HomeFestivalDto festival : festivals) {
            var info = festival.getFestivalInfo();
            var match = regionMatchService.match(info == null ? null : info.getAddress());
            if (!match.matched()) {
                continue;
            }
            // 대한민국 루트를 제외하고 시·도와 시·군·구까지만 사용한다.
            var path = match.path().stream().skip(1).limit(2).toList();
            paths.put(festival.getId(), path);
            path.forEach(region -> names.putIfAbsent(region.id(), region.regionName()));
        }
        var localized = nameLocalizationService.localizeCountryCategoryNames(names, language);
        Map<Long, String> locations = new LinkedHashMap<>();
        paths.forEach((id, path) -> locations.put(id, path.stream()
                .map(region -> localized.getOrDefault(region.id(), region.regionName()))
                .collect(Collectors.joining(" "))));
        return locations;
    }
}
