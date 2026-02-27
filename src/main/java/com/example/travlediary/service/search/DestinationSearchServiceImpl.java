package com.example.travlediary.service.search;

import com.example.travlediary.dto.DestinationSearchResultDto;
import com.example.travlediary.repository.search.DestinationSearchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DestinationSearchServiceImpl implements DestinationSearchService {

    private final DestinationSearchMapper destinationSearchMapper;

    @Override
    public List<DestinationSearchResultDto> search(String keyword, int limit) {
        return destinationSearchMapper.searchDestinations(keyword, limit);
    }
}
