package com.example.travlediary.controller;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.SeasonDestinationDto;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.seo.SeoStructuredData;
import com.example.travlediary.service.course.CourseService;
import com.example.travlediary.service.file.DestinationCardThumbnailService;
import com.example.travlediary.service.recommend.DestinationRecommendService;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class HomeController {

    private final CourseService courseService;
    private final DestinationRecommendService recommendService;
    private final DestinationCardThumbnailService cardThumbnailService;

    public HomeController(CourseService courseService,
                          DestinationRecommendService recommendService,
                          DestinationCardThumbnailService cardThumbnailService) {
     this.courseService = courseService;
     this.recommendService = recommendService;
     this.cardThumbnailService = cardThumbnailService;
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
        model.addAttribute("popularCourses", courseService.getPopularCoursesForHome(language));
        model.addAttribute("homeLandmarks", homeLandmarks(language));
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

    private CustomUserDetails authenticatedUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return null;
        }
        return userDetails;
    }

}
