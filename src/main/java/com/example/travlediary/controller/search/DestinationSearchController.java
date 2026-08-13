package com.example.travlediary.controller.search;

import com.example.travlediary.dto.DestinationSearchResultDto;
import com.example.travlediary.service.search.DestinationSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class DestinationSearchController {
    private final DestinationSearchService destinationSearchService;

    @GetMapping
    public List<DestinationSearchResultDto> searchDestinations(
            @RequestParam("q") String keyword,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long countryId) {
        // size는 검색결과 개수 제한 (기본 20)
        return destinationSearchService.search(keyword, size, countryId);
    }
}
