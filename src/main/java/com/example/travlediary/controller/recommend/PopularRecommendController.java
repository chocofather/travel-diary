package com.example.travlediary.controller.recommend;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.RecommendDestinationDto;
import com.example.travlediary.service.destination.DestinationImageService;
import com.example.travlediary.service.file.DestinationCardThumbnailService;
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
    private final DestinationCardThumbnailService cardThumbnailService;
    private final DestinationImageService destinationImageService;

    // 국내 인기
    @GetMapping("/domestic")
    public List<RecommendDestinationDto> getDomesticPopular(
            @RequestParam(defaultValue = "5") int limit,
            Locale locale
    ) {
        return withCardImages(popularRecommendService.findDomesticPopular(limit, supportedLanguage(locale)));
    }

    // 해외 인기
    @GetMapping("/overseas")
    public List<RecommendDestinationDto> getOverseasPopular(
            @RequestParam(defaultValue = "5") int limit,
            Locale locale
    ) {
        return withCardImages(popularRecommendService.findOverseasPopular(limit, supportedLanguage(locale)));
    }

    // 역사 여행
    @GetMapping("/history")
    public List<RecommendDestinationDto> getHistoryPopular(
            @RequestParam(defaultValue = "5") int limit,
            Locale locale
    ) {
        return withCardImages(popularRecommendService.findThemePopular(
                "history", limit, supportedLanguage(locale)));
    }

    // 인생샷 여행
    @GetMapping("/photo")
    public List<RecommendDestinationDto> getPhotoPopular(
            @RequestParam(defaultValue = "5") int limit,
            Locale locale
    ) {
        return withCardImages(popularRecommendService.findThemePopular(
                "photo", limit, supportedLanguage(locale)));
    }

    // 박물관·미술관
    @GetMapping("/artmuseum")
    public List<RecommendDestinationDto> getArtMuseumPopular(
            @RequestParam(defaultValue = "5") int limit,
            Locale locale
    ) {
        return withCardImages(popularRecommendService.findThemePopular(
                "artmuseum", limit, supportedLanguage(locale)));
    }

    // 수족관·동물원
    @GetMapping("/zoo")
    public List<RecommendDestinationDto> getZooAquariumPopular(
            @RequestParam(defaultValue = "5") int limit,
            Locale locale
    ) {
        return withCardImages(popularRecommendService.findThemePopular(
                "zooaquarium", limit, supportedLanguage(locale)));
    }

    private SupportedLanguage supportedLanguage(Locale locale) {
        return SupportedLanguage.fromLocale(locale).orElse(SupportedLanguage.KOREAN);
    }

    /**
     * 카드는 원본 대신 카드 크기 썸네일을 쓴다. (공개 여행지 목록 카드와 같은 규칙)
     * 공공누리 제3유형(변경금지)은 줄이고 잘라 만든 썸네일 대신 원본을 쓴다.
     */
    private List<RecommendDestinationDto> withCardImages(List<RecommendDestinationDto> destinations) {
        destinationImageService.markNoDerivatives(destinations, RecommendDestinationDto::getImageUrl,
                RecommendDestinationDto::setImageNoDerivatives);
        return cardThumbnailService.applyCardImages(destinations,
                destination -> destination.isImageNoDerivatives() ? null : destination.getImageUrl(),
                (destination, image) -> {
                    destination.setCardImageUrl(image.src());
                    destination.setCardImageSrcset(image.srcset());
                });
    }
}
