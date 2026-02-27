package com.example.travlediary.controller.recommend;

import com.example.travlediary.dto.RandomDestinationDto;
import com.example.travlediary.service.recommend.RandomRecommendService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            @RequestParam(defaultValue = "5", name = "size") int limit) { // 쿼리 파라미터도 size로 맞춰줌
        return randomRecommendService.getRandomDestinationsByRegion(regionId, limit);
    }
}
