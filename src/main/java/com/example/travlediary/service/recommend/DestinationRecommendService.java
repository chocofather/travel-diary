package com.example.travlediary.service.recommend;

import com.example.travlediary.dto.SeasonDestinationDto;
import com.example.travlediary.repository.recommend.DestinationRecommendMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DestinationRecommendService {

    private final DestinationRecommendMapper recommendMapper;

    // 시즌+카테고리별 여행지 N개
    public List<SeasonDestinationDto> findBySeasonAndCategory(String season, Long categoryId, int limit) {
        return recommendMapper.findBySeasonAndCategory(season, categoryId, limit);
    }

    // 시즌별 여행지 N개 (카테고리 조건 없음)
    public List<SeasonDestinationDto> findBySeason(String season, int limit) {
        return recommendMapper.findBySeason(season, limit);
    }

    // 카테고리 이름 조회
    public String getCategoryName(Long categoryId) {
        return recommendMapper.findCategoryNameById(categoryId);
    }
}
