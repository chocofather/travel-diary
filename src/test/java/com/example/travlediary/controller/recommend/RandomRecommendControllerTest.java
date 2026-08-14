package com.example.travlediary.controller.recommend;

import com.example.travlediary.dto.RandomDestinationDto;
import com.example.travlediary.dto.RandomTravelResultDto;
import com.example.travlediary.service.recommend.RandomRecommendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RandomRecommendControllerTest {

    private RandomRecommendService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(RandomRecommendService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RandomRecommendController(service)).build();
    }

    @Test
    void existingRegionEndpointKeepsItsArrayResponseAndSizeParameter() throws Exception {
        RandomDestinationDto destination = destination(41L, "경복궁");
        when(service.getRandomDestinationsByRegion(909L, 5))
                .thenReturn(List.of(destination));

        mockMvc.perform(get("/api/random-recommend/909").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].destinationId").value(41))
                .andExpect(jsonPath("$[0].destinationName").value("경복궁"));

        verify(service).getRandomDestinationsByRegion(909L, 5);
    }

    @Test
    void scopeEndpointReturnsTheSelectedRegionAndDestinationCards() throws Exception {
        RandomTravelResultDto result = result("domestic", 909L, "대한민국", 910L, "서울",
                List.of(destination(41L, "경복궁"), destination(42L, "서울숲")));
        when(service.getRandomTravelByScope("domestic", 777L))
                .thenReturn(Optional.of(result));

        mockMvc.perform(get("/api/random-recommend")
                        .param("scope", "domestic")
                        .param("excludeRegionId", "777"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("domestic"))
                .andExpect(jsonPath("$.countryId").value(909))
                .andExpect(jsonPath("$.countryName").value("대한민국"))
                .andExpect(jsonPath("$.regionId").value(910))
                .andExpect(jsonPath("$.regionName").value("서울"))
                .andExpect(jsonPath("$.recommendedDestinations.length()").value(2))
                .andExpect(jsonPath("$.recommendedDestinations[0].destinationId").value(41))
                .andExpect(jsonPath("$.recommendedDestinations[0].detailUrl")
                        .value("/destinations/41"));

        verify(service).getRandomTravelByScope("domestic", 777L);
    }

    @Test
    void scopeEndpointReturnsNoContentWhenNoRegionExists() throws Exception {
        when(service.getRandomTravelByScope("overseas", null))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/random-recommend").param("scope", "overseas"))
                .andExpect(status().isNoContent());
    }

    @Test
    void scopeEndpointRejectsUnsupportedScope() throws Exception {
        when(service.getRandomTravelByScope("all", null))
                .thenThrow(new IllegalArgumentException("지원하지 않는 여행 범위입니다."));

        mockMvc.perform(get("/api/random-recommend").param("scope", "all"))
                .andExpect(status().isBadRequest());
    }

    private RandomTravelResultDto result(
            String scope,
            Long countryId,
            String countryName,
            Long regionId,
            String regionName,
            List<RandomDestinationDto> destinations) {
        RandomTravelResultDto result = new RandomTravelResultDto();
        result.setScope(scope);
        result.setCountryId(countryId);
        result.setCountryName(countryName);
        result.setRegionId(regionId);
        result.setRegionName(regionName);
        result.setRecommendedDestinations(destinations);
        return result;
    }

    private RandomDestinationDto destination(Long id, String name) {
        RandomDestinationDto destination = new RandomDestinationDto();
        destination.setDestinationId(id);
        destination.setDestinationName(name);
        destination.setShortDescription(name + " 설명");
        destination.setImageUrl("/uploads/" + id + ".jpg");
        destination.setCountryName("대한민국");
        destination.setRegionName("서울");
        return destination;
    }
}
