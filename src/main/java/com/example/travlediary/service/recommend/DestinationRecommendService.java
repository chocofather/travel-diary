package com.example.travlediary.service.recommend;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.SeasonDestinationDto;
import com.example.travlediary.model.DestinationTranslation;
import com.example.travlediary.repository.recommend.DestinationRecommendMapper;
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
public class DestinationRecommendService {

    /*
      메인 랜드마크 카드가 고르는 카테고리. categories.name 은 UNIQUE 이고 기본(한국어) 이름이라
      번호 대신 이 이름으로 찾는다. 관리자 화면에서 이름을 바꾸면 여기도 같이 바꿔야 한다.
    */
    static final String LANDMARK_CATEGORY_NAME = "랜드마크";
    static final int HOME_LANDMARK_LIMIT = 6;

    private final DestinationRecommendMapper recommendMapper;
    private final DestinationService destinationService;
    private final ReferenceNameLocalizationService referenceNameLocalizationService;

    // 시즌+카테고리별 여행지 N개
    public List<SeasonDestinationDto> findBySeasonAndCategory(
            String season, Long categoryId, int limit, SupportedLanguage requestedLanguage) {
        return localize(recommendMapper.findBySeasonAndCategory(season, categoryId, limit),
                requestedLanguage);
    }

    // 시즌별 여행지 N개 (카테고리 조건 없음)
    public List<SeasonDestinationDto> findBySeason(
            String season, int limit, SupportedLanguage requestedLanguage) {
        return localize(recommendMapper.findBySeason(season, limit), requestedLanguage);
    }

    // 메인 랜드마크 카드 (최대 6곳). 이름·지역 번역은 계절 추천과 같이 한 번에 읽는다.
    public List<SeasonDestinationDto> findHomeLandmarks(SupportedLanguage requestedLanguage) {
        return localize(recommendMapper.findByCategoryName(LANDMARK_CATEGORY_NAME, HOME_LANDMARK_LIMIT),
                requestedLanguage);
    }

    // 카테고리 이름 조회
    public String getCategoryName(Long categoryId) {
        return recommendMapper.findCategoryNameById(categoryId);
    }

    private List<SeasonDestinationDto> localize(
            List<SeasonDestinationDto> destinations,
            SupportedLanguage requestedLanguage) {
        List<SeasonDestinationDto> available = destinations == null ? List.of() : destinations;
        List<Long> destinationIds = available.stream()
                .filter(Objects::nonNull)
                .map(SeasonDestinationDto::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, DestinationTranslation> localizedContent =
                destinationService.resolveLocalizedContentByDestinationIds(
                        destinationIds, requestedLanguage);
        Map<Long, String> baseRegionNames = new LinkedHashMap<>();
        List<Long> categoryIds = available.stream()
                .filter(Objects::nonNull)
                .map(SeasonDestinationDto::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        for (SeasonDestinationDto destination : available) {
            if (destination != null && destination.getRegionId() != null) {
                baseRegionNames.putIfAbsent(
                        destination.getRegionId(), destination.getRegionName());
            }
            // 상위 지역도 같은 country_categories 이름이라 한 번에 번역을 읽는다.
            if (destination != null && destination.getParentRegionId() != null) {
                baseRegionNames.putIfAbsent(
                        destination.getParentRegionId(), destination.getParentRegionName());
            }
        }
        Map<Long, String> localizedRegionNames =
                referenceNameLocalizationService.localizeCountryCategoryNames(
                        baseRegionNames, requestedLanguage);
        Map<Long, String> localizedCategoryNames =
                referenceNameLocalizationService.localizeCategories(
                        categoryIds, requestedLanguage);

        for (SeasonDestinationDto destination : available) {
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
            if (destination.getParentRegionId() != null) {
                destination.setParentRegionName(localizedRegionNames.getOrDefault(
                        destination.getParentRegionId(), destination.getParentRegionName()));
            }
            if (destination.getCategoryId() != null) {
                destination.setCategoryName(localizedCategoryNames.getOrDefault(
                        destination.getCategoryId(), destination.getCategoryName()));
            }
        }
        return available;
    }
}
