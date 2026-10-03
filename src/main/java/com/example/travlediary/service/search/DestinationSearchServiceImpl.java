package com.example.travlediary.service.search;

import com.example.travlediary.dto.DestinationSearchResultDto;
import com.example.travlediary.repository.search.DestinationSearchMapper;
import com.example.travlediary.service.destination.DestinationImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DestinationSearchServiceImpl implements DestinationSearchService {

    private final DestinationSearchMapper destinationSearchMapper;
    private final DestinationImageService destinationImageService;

    @Override
    public List<DestinationSearchResultDto> search(String keyword, int limit, Long countryId) {
        List<DestinationSearchResultDto> results = destinationSearchMapper.searchDestinations(keyword, limit, countryId);
        // 대표 이미지가 공공누리 제3유형(변경금지)이면 잘리지 않게 그린다.
        destinationImageService.markNoDerivatives(results, DestinationSearchResultDto::getThumbnailUrl,
                DestinationSearchResultDto::setImageNoDerivatives);
        return results;
    }
}
