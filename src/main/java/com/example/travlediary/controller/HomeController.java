package com.example.travlediary.controller;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.RecommendDestinationDto;
import com.example.travlediary.dto.SeasonDestinationDto;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.seo.SeoStructuredData;
import com.example.travlediary.service.travelinfo.FestivalDetailService;
import com.example.travlediary.service.destination.DestinationViewClock;
import com.example.travlediary.service.file.DestinationCardThumbnailService;
import com.example.travlediary.service.recommend.DestinationRecommendService;
import com.example.travlediary.service.recommend.PopularRecommendService;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class HomeController {

    // 메인 인기 여행지: 대표 1곳 + 보조 4곳
    private static final int HOME_POPULAR_LIMIT = 5;

    private final FestivalDetailService festivalDetailService;
    private final DestinationRecommendService recommendService;
    private final PopularRecommendService popularRecommendService;
    private final DestinationCardThumbnailService cardThumbnailService;
    private final DestinationViewClock viewClock;

    public HomeController(FestivalDetailService festivalDetailService,
                          DestinationRecommendService recommendService,
                          PopularRecommendService popularRecommendService,
                          DestinationCardThumbnailService cardThumbnailService,
                          DestinationViewClock viewClock) {
     this.festivalDetailService = festivalDetailService;
     this.recommendService = recommendService;
     this.popularRecommendService = popularRecommendService;
     this.cardThumbnailService = cardThumbnailService;
     this.viewClock = viewClock;
    }

    @GetMapping("/")
    public String home(Model model, Authentication auth) {
        /*
          예전에는 여기서 회원 한 줄을 통째로 읽어 "user" 로 넘겼는데 home.html 이 쓰지 않는다.
          로그인 여부와 헤더 프로필 사진은 GlobalModelAttributes 가 이미 모든 화면에 넣어 준다.
        */
        SupportedLanguage language = SupportedLanguage.fromLocale(LocaleContextHolder.getLocale())
                .orElse(SupportedLanguage.KOREAN);
        model.addAttribute("isLoggedIn", authenticatedUser(auth) != null);
        model.addAttribute("homeFestivals", festivalDetailService.getHomeFestivals(viewClock.today(), language));
        model.addAttribute("homeLandmarks", homeLandmarks(language));
        /*
          인기 여행지 편집 영역의 데이터는 둘 중 하나만 쓴다. (섞어서 5곳을 채우지 않는다)
            A. 최근 7일(KST) 조회 기록이 있는 여행지가 5곳 → '지금 뜨는 여행지'
            B. 그보다 적으면 → 기존 '인기 여행지'
        */
        List<SeasonDestinationDto> trending = trendingDestinations(language);
        boolean popularTrending = trending.size() == HOME_POPULAR_LIMIT;
        model.addAttribute("popularTrending", popularTrending);
        model.addAttribute("popularDestinations",
                popularTrending ? trending : popularDestinations(language));
        SeoStructuredData.website(model);

        return "home";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    /*
      메인 랜드마크 아치 카드. 계절 추천 카드와 같이 원본 대신 카드 크기 썸네일을 쓴다.
      아치 사진 칸은 3:4 이다(home-converge-prototype.css 의 aspect-ratio).
    */
    private List<SeasonDestinationDto> homeLandmarks(SupportedLanguage language) {
        List<SeasonDestinationDto> landmarks = recommendService.findHomeLandmarks(language);
        cardThumbnailService.applyCardImages(landmarks, SeasonDestinationDto::getImageUrl,
                (destination, image) -> {
                    destination.setCardImageUrl(image.src());
                    destination.setCardImageSrcset(image.srcset());
                    destination.setCardImageCoverScale(image.coverScale(3, 4));
                });
        return landmarks == null ? List.of() : landmarks;
    }

    /*
      메인 '지금 뜨는 여행지': 오늘(KST) 포함 최근 7일 조회 합계 상위 5곳 (국내 + 해외).
      지역은 랜드마크 카드처럼 상위 지역 + 지역으로 보여 주고, 카드 크기 썸네일을 쓴다.
    */
    private List<SeasonDestinationDto> trendingDestinations(SupportedLanguage language) {
        List<SeasonDestinationDto> destinations = recommendService.findTrendingDestinations(
                viewClock.today(), HOME_POPULAR_LIMIT, language);
        if (destinations == null || destinations.size() != HOME_POPULAR_LIMIT) {
            return destinations == null ? List.of() : destinations;
        }
        cardThumbnailService.applyCardImages(destinations, SeasonDestinationDto::getImageUrl,
                (destination, image) -> {
                    destination.setCardImageUrl(image.src());
                    destination.setCardImageSrcset(image.srcset());
                });
        return destinations;
    }

    /*
      메인 인기 여행지 큐레이션(fallback). 기존 국내 인기 여행지 조회(/api/popular-destinations/domestic 과 같은 기준)를
      그대로 쓰고, 공개 목록 카드와 같이 원본 대신 카드 크기 썸네일을 쓴다.
      편집 영역은 지금 뜨는 여행지와 같은 카드 모양(SeasonDestinationDto)으로 그린다. 이 조회에는 상위 지역이 없다.
    */
    private List<SeasonDestinationDto> popularDestinations(SupportedLanguage language) {
        List<RecommendDestinationDto> destinations =
                popularRecommendService.findDomesticPopular(HOME_POPULAR_LIMIT, language);
        cardThumbnailService.applyCardImages(destinations, RecommendDestinationDto::getImageUrl,
                (destination, image) -> {
                    destination.setCardImageUrl(image.src());
                    destination.setCardImageSrcset(image.srcset());
                });
        return destinations == null ? List.of()
                : destinations.stream().map(HomeController::editorialCard).toList();
    }

    private static SeasonDestinationDto editorialCard(RecommendDestinationDto popular) {
        SeasonDestinationDto card = new SeasonDestinationDto();
        card.setId(popular.getId());
        card.setName(popular.getName());
        card.setImageUrl(popular.getImageUrl());
        card.setRegionId(popular.getRegionId());
        card.setRegionName(popular.getRegionName());
        card.setCardImageUrl(popular.getCardImageUrl());
        card.setCardImageSrcset(popular.getCardImageSrcset());
        return card;
    }

    private CustomUserDetails authenticatedUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return null;
        }
        return userDetails;
    }

}
