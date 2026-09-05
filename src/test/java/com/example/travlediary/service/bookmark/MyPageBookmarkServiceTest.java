package com.example.travlediary.service.bookmark;

import com.example.travlediary.dto.MyPageBookmarkPageDto;
import com.example.travlediary.dto.MyPageCommunityBookmarkDto;
import com.example.travlediary.repository.bookmark.MyPageBookmarkMapper;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.category.ReferenceNameLocalizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyPageBookmarkServiceTest {

    @Mock
    private MyPageBookmarkMapper mapper;
    @Mock
    private CountryCategoryService countryCategoryService;
    @Mock
    private ReferenceNameLocalizationService referenceNameLocalizationService;

    @Test
    void invalidParametersFallBackToDestinationAllAndFirstPage() {
        MyPageBookmarkService service = service();
        when(mapper.countDestinationBookmarks(7L, "all", null)).thenReturn(0);

        MyPageBookmarkPageDto result = service.getBookmarks(
                7L, "unknown", "wrong", "tip", -3);

        assertThat(result.getSection()).isEqualTo("destination");
        assertThat(result.getScope()).isEqualTo("all");
        assertThat(result.getType()).isEqualTo("all");
        assertThat(result.getCurrentPage()).isEqualTo(1);
        assertThat(result.getBookmarks()).isEmpty();
        verify(mapper).countDestinationBookmarks(7L, "all", null);
        verify(countryCategoryService, never()).getKoreaRootId();
    }

    @Test
    void destinationScopeUsesTheDynamicKoreaRootForListAndCount() {
        MyPageBookmarkService service = service();
        when(countryCategoryService.getKoreaRootId()).thenReturn(42L);
        when(mapper.countDestinationBookmarks(7L, "domestic", 42L)).thenReturn(12);

        MyPageBookmarkPageDto result = service.getBookmarks(
                7L, "DESTINATION", "DOMESTIC", "all", 2);

        assertThat(result.getSection()).isEqualTo("destination");
        assertThat(result.getScope()).isEqualTo("domestic");
        assertThat(result.getCurrentPage()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(2);
        verify(mapper).findDestinationBookmarks(7L, "domestic", 42L, 10L, 10);
    }

    @Test
    void communityUsesTenItemsAndCanonicalTypeWithoutRegionLookup() {
        MyPageBookmarkService service = service();
        MyPageCommunityBookmarkDto bookmark = new MyPageCommunityBookmarkDto();
        when(mapper.countCommunityBookmarks(7L, "question")).thenReturn(21);
        when(mapper.findCommunityBookmarks(7L, "question", 20L, 10))
                .thenReturn(List.of(bookmark));

        MyPageBookmarkPageDto result = service.getBookmarks(
                7L, "COMMUNITY", "international", "QUESTION", 3);

        assertThat(result.getSection()).isEqualTo("community");
        assertThat(result.getScope()).isEqualTo("all");
        assertThat(result.getType()).isEqualTo("question");
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getBookmarks()).hasSize(1);
        assertThat(result.getBookmarks().get(0)).isSameAs(bookmark);
        verify(countryCategoryService, never()).getKoreaRootId();
    }

    @Test
    void travelInfoConvertsScopeToTheExistingDatabaseValue() {
        MyPageBookmarkService service = service();
        when(mapper.countTravelInfoBookmarks(7L, "INTERNATIONAL")).thenReturn(1);

        MyPageBookmarkPageDto result = service.getBookmarks(
                7L, "travel-info", "international", "course", 1);

        assertThat(result.getScope()).isEqualTo("international");
        assertThat(result.getType()).isEqualTo("all");
        verify(mapper).findTravelInfoBookmarks(7L, "INTERNATIONAL", 0L, 10);
    }

    @Test
    void refusesToMisclassifyRegionsWhenTheKoreaRootIsMissing() {
        MyPageBookmarkService service = service();
        when(countryCategoryService.getKoreaRootId()).thenReturn(null);

        assertThatThrownBy(() -> service.getBookmarks(
                7L, "destination", "international", "all", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("대한민국");

        verify(mapper, never()).countDestinationBookmarks(7L, "international", null);
    }

    @Test
    void rejectsMissingPrincipalBeforeAnyQuery() {
        MyPageBookmarkService service = service();

        assertThatThrownBy(() -> service.getBookmarks(
                null, "destination", "all", "all", 1))
                .isInstanceOf(IllegalArgumentException.class);

        verify(mapper, never()).countDestinationBookmarks(null, "all", null);
    }

    private MyPageBookmarkService service() {
        return new MyPageBookmarkService(mapper, countryCategoryService,
                referenceNameLocalizationService);
    }
}
