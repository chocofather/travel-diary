package com.example.travlediary.controller.recommend;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.SeasonDestinationDto;
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
        if (categoryId != null) {
            return recommendService.findBySeasonAndCategory(
                    season, categoryId, limit, requestedLanguage);
        } else {
            return recommendService.findBySeason(season, limit, requestedLanguage);
        }
    }
}
