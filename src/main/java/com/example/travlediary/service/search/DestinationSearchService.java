package com.example.travlediary.service.search;

import com.example.travlediary.dto.DestinationSearchResultDto;

import java.util.List;

public interface DestinationSearchService {
    List<DestinationSearchResultDto> search(String keyword, int limit, Long countryId);

}
