package com.example.travlediary.controller.recommend;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.config.i18n.TravelDiaryLocaleResolver;
import com.example.travlediary.dto.RecommendDestinationDto;
import com.example.travlediary.dto.SeasonDestinationDto;
import com.example.travlediary.service.recommend.DestinationRecommendService;
import com.example.travlediary.service.recommend.PopularRecommendService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HomeDestinationApiLocaleTest {

    @Mock private PopularRecommendService popularRecommendService;
    @Mock private DestinationRecommendService destinationRecommendService;

    @ParameterizedTest
    @EnumSource(SupportedLanguage.class)
    void homeDestinationApisUseTheLocaleCookieForTheirLocalizedJson(
            SupportedLanguage language) throws Exception {
        PopularRecommendController popularController =
                new PopularRecommendController(popularRecommendService);
        DestinationRecommendController seasonController =
                new DestinationRecommendController(destinationRecommendService);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(popularController, seasonController)
                .setLocaleResolver(new TravelDiaryLocaleResolver())
                .build();
        RecommendDestinationDto popular = new RecommendDestinationDto();
        popular.setId(15L);
        popular.setName(language.getLanguageTag() + " popular");
        SeasonDestinationDto seasonal = new SeasonDestinationDto();
        seasonal.setId(15L);
        seasonal.setName(language.getLanguageTag() + " seasonal");
        when(popularRecommendService.findDomesticPopular(5, language))
                .thenReturn(List.of(popular));
        when(destinationRecommendService.findBySeasonAndCategory(
                "SPRING", 7L, 5, language)).thenReturn(List.of(seasonal));

        Cookie localeCookie = new Cookie(
                TravelDiaryLocaleResolver.COOKIE_NAME, language.getLanguageTag());
        mockMvc.perform(get("/api/popular-destinations/domestic")
                        .param("limit", "5")
                        .cookie(localeCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name")
                        .value(language.getLanguageTag() + " popular"));
        mockMvc.perform(get("/api/season-destinations")
                        .param("season", "SPRING")
                        .param("categoryId", "7")
                        .param("limit", "5")
                        .cookie(localeCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name")
                        .value(language.getLanguageTag() + " seasonal"));

        verify(popularRecommendService).findDomesticPopular(5, language);
        verify(destinationRecommendService).findBySeasonAndCategory(
                "SPRING", 7L, 5, language);
    }
}
