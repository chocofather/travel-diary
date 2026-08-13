package com.example.travlediary.controller.search;

import com.example.travlediary.dto.DestinationSearchResultDto;
import com.example.travlediary.service.search.DestinationSearchService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DestinationSearchControllerTest {

    @Test
    void searchWithoutCountryKeepsExistingUnfilteredBehavior() {
        DestinationSearchService service = mock(DestinationSearchService.class);
        DestinationSearchController controller = new DestinationSearchController(service);
        List<DestinationSearchResultDto> results = List.of(new DestinationSearchResultDto());
        when(service.search("서울", 20, null)).thenReturn(results);

        assertThat(controller.searchDestinations("서울", 20, null)).isSameAs(results);
        verify(service).search("서울", 20, null);
    }

    @Test
    void searchPassesSelectedCountryToServerFilter() {
        DestinationSearchService service = mock(DestinationSearchService.class);
        DestinationSearchController controller = new DestinationSearchController(service);
        List<DestinationSearchResultDto> results = List.of(new DestinationSearchResultDto());
        when(service.search("도쿄", 20, 8L)).thenReturn(results);

        assertThat(controller.searchDestinations("도쿄", 20, 8L)).isSameAs(results);
        verify(service).search("도쿄", 20, 8L);
    }
}
