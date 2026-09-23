package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoFestivalSearchItemResponse;
import com.example.travlediary.dto.kto.KtoFestivalSearchResponse;
import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.repository.travelinfo.FestivalInfoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminKtoFestivalSearchServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 9, 30);

    @Mock
    private KtoFestivalService ktoFestivalService;
    @Mock
    private FestivalInfoMapper festivalInfoMapper;
    @InjectMocks
    private AdminKtoFestivalSearchService service;

    @Test
    void marksOnlyTheRegisteredOccurrenceOfTheSameTourApiContent() {
        when(ktoFestivalService.search(START, END, 1, 20)).thenReturn(new KtoFestivalSearchResponse(
                1, 20, 3, List.of(candidate("same", 2026), candidate("same", 2027),
                        candidate("other", 2026))));
        when(festivalInfoMapper.findOccurrencesByContentIds("KTO_TOURAPI", List.of("same", "other")))
                .thenReturn(List.of(occurrence("same", 2026, 42L)));

        KtoFestivalSearchResponse response = service.search(START, END, 1, 20, false);

        assertThat(response.items()).extracting(KtoFestivalSearchItemResponse::registrationStatus)
                .containsExactly("REGISTERED", "UNREGISTERED", "UNREGISTERED");
        assertThat(response.items()).extracting(KtoFestivalSearchItemResponse::registeredFestivalId)
                .containsExactly(42L, null, null);
        verify(festivalInfoMapper).findOccurrencesByContentIds("KTO_TOURAPI", List.of("same", "other"));
    }

    @Test
    void unfilteredLastPageKeepsTheRequestedPageSizeForPagination() {
        when(ktoFestivalService.search(START, END, 12, 20))
                .thenReturn(new KtoFestivalSearchResponse(12, 10, 230,
                        List.of(candidate("last", 2026))));
        when(festivalInfoMapper.findOccurrencesByContentIds("KTO_TOURAPI", List.of("last")))
                .thenReturn(List.of());

        KtoFestivalSearchResponse response = service.search(START, END, 12, 20, false);

        assertThat(response.pageNo()).isEqualTo(12);
        assertThat(response.numOfRows()).isEqualTo(20);
        assertThat(response.totalCount()).isEqualTo(230);
        assertThat(response.items()).extracting(KtoFestivalSearchItemResponse::contentId)
                .containsExactly("last");
    }

    @Test
    void unregisteredFilterPagesAcrossTheEntireTourApiResult() {
        List<KtoFestivalSearchItemResponse> firstPage = new ArrayList<>();
        for (int id = 1; id <= 20; id++) firstPage.add(candidate("id-" + id, 2026));
        List<KtoFestivalSearchItemResponse> secondPage = List.of(
                candidate("id-21", 2026), candidate("id-22", 2026), candidate("id-23", 2026));
        when(ktoFestivalService.search(START, END, 1, 20))
                .thenReturn(new KtoFestivalSearchResponse(1, 20, 23, firstPage));
        when(ktoFestivalService.search(START, END, 2, 20))
                .thenReturn(new KtoFestivalSearchResponse(2, 20, 23, secondPage));
        when(festivalInfoMapper.findOccurrencesByContentIds(eq("KTO_TOURAPI"), anyList()))
                .thenAnswer(invocation -> {
                    List<String> ids = invocation.getArgument(1);
                    return ids.contains("id-1")
                            ? List.of(occurrence("id-1", 2026, 1L), occurrence("id-2", 2026, 2L))
                            : List.of();
                });

        KtoFestivalSearchResponse first = service.search(START, END, 1, 20, true);
        KtoFestivalSearchResponse second = service.search(START, END, 2, 20, true);

        assertThat(first.totalCount()).isEqualTo(21);
        assertThat(first.items()).hasSize(20);
        assertThat(first.items().get(0).contentId()).isEqualTo("id-3");
        assertThat(first.items().get(19).contentId()).isEqualTo("id-22");
        assertThat(second.totalCount()).isEqualTo(21);
        assertThat(second.pageNo()).isEqualTo(2);
        assertThat(second.items()).extracting(KtoFestivalSearchItemResponse::contentId)
                .containsExactly("id-23");
        verify(festivalInfoMapper, times(4)).findOccurrencesByContentIds(eq("KTO_TOURAPI"), anyList());
    }

    @Test
    void unregisteredFilterAcceptsActualRowCountOnTheLastTourApiPage() {
        List<KtoFestivalSearchItemResponse> firstPage = new ArrayList<>();
        for (int id = 1; id <= 20; id++) firstPage.add(candidate("id-" + id, 2026));
        List<KtoFestivalSearchItemResponse> lastPage = List.of(
                candidate("id-21", 2026), candidate("id-22", 2026), candidate("id-23", 2026));
        when(ktoFestivalService.search(START, END, 1, 20))
                .thenReturn(new KtoFestivalSearchResponse(1, 20, 23, firstPage));
        when(ktoFestivalService.search(START, END, 2, 20))
                .thenReturn(new KtoFestivalSearchResponse(2, 3, 23, lastPage));
        when(festivalInfoMapper.findOccurrencesByContentIds(eq("KTO_TOURAPI"), anyList()))
                .thenReturn(List.of());

        KtoFestivalSearchResponse response = service.search(START, END, 2, 20, true);

        assertThat(response.totalCount()).isEqualTo(23);
        assertThat(response.numOfRows()).isEqualTo(20);
        assertThat(response.items()).extracting(KtoFestivalSearchItemResponse::contentId)
                .containsExactly("id-21", "id-22", "id-23");
        verify(festivalInfoMapper, times(2)).findOccurrencesByContentIds(eq("KTO_TOURAPI"), anyList());
    }

    @Test
    void keywordSearchUsesTheSameRegistrationFilter() {
        when(ktoFestivalService.searchByKeyword("가을", 1, 20)).thenReturn(new KtoFestivalSearchResponse(
                1, 20, 2, List.of(candidate("old", 2026), candidate("new", 2026))));
        when(festivalInfoMapper.findOccurrencesByContentIds("KTO_TOURAPI", List.of("old", "new")))
                .thenReturn(List.of(occurrence("old", 2026, 9L)));

        KtoFestivalSearchResponse response = service.searchByKeyword("가을", 1, 20, true);

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.items()).extracting(KtoFestivalSearchItemResponse::contentId)
                .containsExactly("new");
    }

    @Test
    void upstreamFailureDuringFilteredScanDoesNotReturnPartialUnregisteredResults() {
        when(ktoFestivalService.search(START, END, 1, 20)).thenReturn(new KtoFestivalSearchResponse(
                1, 20, 21, List.of(candidate("first", 2026))));
        when(ktoFestivalService.search(START, END, 2, 20)).thenThrow(KtoTourApiException.upstreamFailure());
        when(festivalInfoMapper.findOccurrencesByContentIds("KTO_TOURAPI", List.of("first")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.search(START, END, 1, 20, true))
                .isInstanceOf(KtoTourApiException.class);
    }

    @Test
    void missingFinalTourApiPageIsAnErrorInsteadOfAnIncompleteFilteredResult() {
        List<KtoFestivalSearchItemResponse> firstPage = new ArrayList<>();
        for (int id = 1; id <= 20; id++) firstPage.add(candidate("id-" + id, 2026));
        when(ktoFestivalService.search(START, END, 1, 20))
                .thenReturn(new KtoFestivalSearchResponse(1, 20, 21, firstPage));
        when(ktoFestivalService.search(START, END, 2, 20))
                .thenReturn(new KtoFestivalSearchResponse(2, 20, 21, List.of()));
        when(festivalInfoMapper.findOccurrencesByContentIds(eq("KTO_TOURAPI"), anyList()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.search(START, END, 1, 20, true))
                .isInstanceOf(KtoTourApiException.class);
    }

    @Test
    void shortNonFinalTourApiPageIsAnErrorInsteadOfSkippingCandidates() {
        when(ktoFestivalService.search(START, END, 1, 20))
                .thenReturn(new KtoFestivalSearchResponse(1, 10, 40,
                        List.of(candidate("first", 2026))));

        assertThatThrownBy(() -> service.search(START, END, 1, 20, true))
                .isInstanceOf(KtoTourApiException.class);
    }

    @Test
    void emptyTourApiResultRemainsAnEmptyFilteredResult() {
        when(ktoFestivalService.search(START, END, 1, 20))
                .thenReturn(new KtoFestivalSearchResponse(1, 10, 0, List.of()));

        KtoFestivalSearchResponse response = service.search(START, END, 1, 20, true);

        assertThat(response.totalCount()).isZero();
        assertThat(response.items()).isEmpty();
        verifyNoInteractions(festivalInfoMapper);
    }

    private KtoFestivalSearchItemResponse candidate(String contentId, int year) {
        return new KtoFestivalSearchItemResponse(contentId, "축제 " + contentId,
                LocalDate.of(year, 9, 1), LocalDate.of(year, 9, 3),
                null, null, "서울", "EV", "EV01", "EV010100", "축제");
    }

    private FestivalInfo occurrence(String contentId, int year, Long infoId) {
        FestivalInfo info = new FestivalInfo();
        info.setInfoId(infoId);
        info.setSourceType("KTO_TOURAPI");
        info.setExternalContentId(contentId);
        info.setEventYear(year);
        return info;
    }
}
