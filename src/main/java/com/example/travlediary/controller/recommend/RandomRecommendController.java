package com.example.travlediary.controller.recommend;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.RandomDestinationDto;
import com.example.travlediary.dto.RandomTravelResultDto;
import com.example.travlediary.service.recommend.RandomRecommendService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/random-recommend")
public class RandomRecommendController {

    private final RandomRecommendService randomRecommendService;

    public RandomRecommendController(RandomRecommendService randomRecommendService) {
        this.randomRecommendService = randomRecommendService;
    }

    // 특정 지역(regionId) 기준 랜덤 여행지 카드 리스트 반환
    @GetMapping("/{regionId}")
    public List<RandomDestinationDto> getRandomDestinationsByRegion(
            @PathVariable Long regionId,
            @RequestParam(defaultValue = "5", name = "size") int limit,
            Locale locale) { // 쿼리 파라미터도 size로 맞춰줌
        return randomRecommendService.getRandomDestinationsByRegion(
                regionId, limit, supportedLanguage(locale));
    }

    @GetMapping
    public ResponseEntity<RandomTravelResultDto> getRandomTravelByScope(
            @RequestParam String scope,
            @RequestParam(required = false) Long excludeRegionId,
            Locale locale) {
        try {
            return randomRecommendService.getRandomTravelByScope(
                            scope, excludeRegionId, supportedLanguage(locale))
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.noContent().build());
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    private SupportedLanguage supportedLanguage(Locale locale) {
        return SupportedLanguage.fromLocale(locale).orElse(SupportedLanguage.KOREAN);
    }
}
