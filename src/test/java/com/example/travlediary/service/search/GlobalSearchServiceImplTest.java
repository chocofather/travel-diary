package com.example.travlediary.service.search;

import com.example.travlediary.dto.GlobalSearchPage;
import com.example.travlediary.dto.GlobalSearchResultDto;
import com.example.travlediary.repository.search.GlobalSearchMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalSearchServiceImplTest {

    @Mock
    private GlobalSearchMapper globalSearchMapper;

    @InjectMocks
    private GlobalSearchServiceImpl globalSearchService;

    @Test
    void blankQueryReturnsEmptyPageWithoutDatabaseSearch() {
        GlobalSearchPage result = globalSearchService.search("   ", "destination", 1);

        assertThat(result.hasQuery()).isFalse();
        assertThat(result.results()).isEmpty();
        assertThat(result.totalCount()).isZero();
        assertThat(result.type()).isEqualTo("destination");
        verifyNoInteractions(globalSearchMapper);
    }

    @Test
    void invalidTypeFallsBackToAllAndEscapesLikeWildcards() {
        when(globalSearchMapper.count("제주!%!_!!", "all")).thenReturn(0L);

        GlobalSearchPage result = globalSearchService.search("  제주%_!  ", "unknown", 1);

        assertThat(result.query()).isEqualTo("제주%_!");
        assertThat(result.type()).isEqualTo("all");
        verify(globalSearchMapper).count("제주!%!_!!", "all");
        verify(globalSearchMapper, never()).search(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void selectedTypeAndPaginationArePassedToMapperAndSummaryBecomesPlainText() {
        GlobalSearchResultDto item = new GlobalSearchResultDto();
        item.setType("community");
        item.setSummary("<p>제주 <strong>여행</strong></p><script>alert(1)</script>");
        when(globalSearchMapper.count("제주", "community")).thenReturn(25L);
        when(globalSearchMapper.search("제주", "community", 20L, 10)).thenReturn(List.of(item));

        GlobalSearchPage result = globalSearchService.search("제주", "community", 99);

        assertThat(result.currentPage()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.results().get(0).getSummary()).isEqualTo("제주 여행");
        verify(globalSearchMapper).search("제주", "community", 20L, 10);
    }
}
