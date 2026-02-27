package com.example.travlediary.service.recommend;

import com.example.travlediary.dto.RecommendDestinationDto;
import com.example.travlediary.repository.recommend.PopularRecommendMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PopularRecommendService {

    private final PopularRecommendMapper recommendMapper;

    // 국내 인기 여행지 (상위 N개 랜덤)
    public List<RecommendDestinationDto> findDomesticPopular(int limit) {
        return recommendMapper.findPopularDomesticDestinations(limit);
    }

    // 해외 인기 여행지 (상위 N개 랜덤)
    public List<RecommendDestinationDto> findOverseasPopular(int limit) {
        return recommendMapper.findPopularOverseasDestinations(limit);
    }

    // 테마/카테고리별 인기 여행지 (예: 박물관, 미술관, 수족관, 동물원 등)
    public List<RecommendDestinationDto> findThemePopular(String theme, int limit) {
        List<Long> categoryIds = convertThemeToCategoryIds(theme);
        return recommendMapper.findPopularThemeDestinations(categoryIds, limit);
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
