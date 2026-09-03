package com.example.travlediary.service.recommend;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.RecommendDestinationDto;
import com.example.travlediary.model.DestinationTranslation;
import com.example.travlediary.repository.recommend.PopularRecommendMapper;
import com.example.travlediary.service.category.ReferenceNameLocalizationService;
import com.example.travlediary.service.destination.DestinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PopularRecommendService {

    private final PopularRecommendMapper recommendMapper;
    private final DestinationService destinationService;
    private final ReferenceNameLocalizationService referenceNameLocalizationService;

    // 국내 인기 여행지 (상위 N개 랜덤)
    public List<RecommendDestinationDto> findDomesticPopular(
            int limit, SupportedLanguage requestedLanguage) {
        return localize(recommendMapper.findPopularDomesticDestinations(limit), requestedLanguage);
    }

    // 해외 인기 여행지 (상위 N개 랜덤)
    public List<RecommendDestinationDto> findOverseasPopular(
            int limit, SupportedLanguage requestedLanguage) {
        return localize(recommendMapper.findPopularOverseasDestinations(limit), requestedLanguage);
    }

    // 테마/카테고리별 인기 여행지 (예: 박물관, 미술관, 수족관, 동물원 등)
    public List<RecommendDestinationDto> findThemePopular(
            String theme, int limit, SupportedLanguage requestedLanguage) {
        List<Long> categoryIds = convertThemeToCategoryIds(theme);
        return localize(recommendMapper.findPopularThemeDestinations(categoryIds, limit),
                requestedLanguage);
    }

    private List<RecommendDestinationDto> localize(
            List<RecommendDestinationDto> destinations,
            SupportedLanguage requestedLanguage) {
        List<RecommendDestinationDto> available = destinations == null ? List.of() : destinations;
        List<Long> destinationIds = available.stream()
                .filter(Objects::nonNull)
                .map(RecommendDestinationDto::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, DestinationTranslation> localizedContent =
                destinationService.resolveLocalizedContentByDestinationIds(
                        destinationIds, requestedLanguage);
        Map<Long, String> baseRegionNames = new LinkedHashMap<>();
        for (RecommendDestinationDto destination : available) {
            if (destination != null && destination.getRegionId() != null) {
                baseRegionNames.putIfAbsent(
                        destination.getRegionId(), destination.getRegionName());
            }
        }
        Map<Long, String> localizedRegionNames =
                referenceNameLocalizationService.localizeCountryCategoryNames(
                        baseRegionNames, requestedLanguage);

        for (RecommendDestinationDto destination : available) {
            if (destination == null) {
                continue;
            }
            DestinationTranslation content = localizedContent.get(destination.getId());
            if (content != null && content.getName() != null && !content.getName().isBlank()) {
                destination.setName(content.getName());
            }
            if (destination.getRegionId() != null) {
                destination.setRegionName(localizedRegionNames.getOrDefault(
                        destination.getRegionId(), destination.getRegionName()));
            }
        }
        return available;
    }

    private List<Long> convertThemeToCategoryIds(String theme) {
        switch (theme) {
            case "history":
                // 역사 여행: 3, 4, 38, 39, 84
                return List.of(3L, 4L, 38L, 39L, 84L);
            case "photo":
                // 인생샷 여행: 32, 33
                return List.of(32L, 33L);
            case "artmuseum":
                // 박물관·미술관: 2, 19, 20
                return List.of(2L, 19L, 20L);
            case "zooaquarium":
                // 수족관·동물원: 16, 22
                return List.of(16L, 22L);
            default:
                throw new IllegalArgumentException("지원하지 않는 테마: " + theme);
        }
    }
}
