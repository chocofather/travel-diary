package com.example.travlediary.controller.recommend;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.SeasonDestinationDto;
import com.example.travlediary.service.file.DestinationCardThumbnailService;
import com.example.travlediary.service.recommend.DestinationRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/season-destinations")
@RequiredArgsConstructor
public class DestinationRecommendController {

    private final DestinationRecommendService recommendService;
    private final DestinationCardThumbnailService cardThumbnailService;

    // 1) 시즌+카테고리별 여행지 추천
    @GetMapping
    public List<SeasonDestinationDto> getSeasonDestinations(
            @RequestParam String season,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "5") int limit,
            Locale locale
    ) {
        SupportedLanguage requestedLanguage = SupportedLanguage.fromLocale(locale)
                .orElse(SupportedLanguage.KOREAN);
        List<SeasonDestinationDto> destinations = categoryId != null
                ? recommendService.findBySeasonAndCategory(season, categoryId, limit, requestedLanguage)
                : recommendService.findBySeason(season, limit, requestedLanguage);
        // 카드는 원본 대신 카드 크기 썸네일을 쓴다. (공개 여행지 목록 카드와 같은 규칙)
        return cardThumbnailService.applyCardImages(destinations, SeasonDestinationDto::getImageUrl,
                (destination, image) -> {
                    destination.setCardImageUrl(image.src());
                    destination.setCardImageSrcset(image.srcset());
                });
    }
}
