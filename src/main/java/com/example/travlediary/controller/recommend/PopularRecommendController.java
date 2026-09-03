package com.example.travlediary.controller.recommend;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.RecommendDestinationDto;
import com.example.travlediary.service.recommend.PopularRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/popular-destinations")
@RequiredArgsConstructor
public class PopularRecommendController {

    private final PopularRecommendService popularRecommendService;

    // 국내 인기
    @GetMapping("/domestic")
    public List<RecommendDestinationDto> getDomesticPopular(
            @RequestParam(defaultValue = "5") int limit,
            Locale locale
    ) {
        return popularRecommendService.findDomesticPopular(limit, supportedLanguage(locale));
    }

    // 해외 인기
    @GetMapping("/overseas")
    public List<RecommendDestinationDto> getOverseasPopular(
            @RequestParam(defaultValue = "5") int limit,
            Locale locale
    ) {
        return popularRecommendService.findOverseasPopular(limit, supportedLanguage(locale));
    }

    // 역사 여행
    @GetMapping("/history")
    public List<RecommendDestinationDto> getHistoryPopular(
            @RequestParam(defaultValue = "5") int limit,
            Locale locale
    ) {
        return popularRecommendService.findThemePopular(
                "history", limit, supportedLanguage(locale));
    }

    // 인생샷 여행
    @GetMapping("/photo")
    public List<RecommendDestinationDto> getPhotoPopular(
            @RequestParam(defaultValue = "5") int limit,
            Locale locale
    ) {
        return popularRecommendService.findThemePopular(
                "photo", limit, supportedLanguage(locale));
    }

    // 박물관·미술관
    @GetMapping("/artmuseum")
    public List<RecommendDestinationDto> getArtMuseumPopular(
            @RequestParam(defaultValue = "5") int limit,
            Locale locale
    ) {
        return popularRecommendService.findThemePopular(
                "artmuseum", limit, supportedLanguage(locale));
    }

    // 수족관·동물원
    @GetMapping("/zoo")
    public List<RecommendDestinationDto> getZooAquariumPopular(
            @RequestParam(defaultValue = "5") int limit,
            Locale locale
    ) {
        return popularRecommendService.findThemePopular(
                "zooaquarium", limit, supportedLanguage(locale));
    }

    private SupportedLanguage supportedLanguage(Locale locale) {
        return SupportedLanguage.fromLocale(locale).orElse(SupportedLanguage.KOREAN);
    }
}
