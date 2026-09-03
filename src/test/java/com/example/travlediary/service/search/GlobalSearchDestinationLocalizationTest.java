package com.example.travlediary.service.search;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.GlobalSearchPage;
import com.example.travlediary.dto.GlobalSearchResultDto;
import com.example.travlediary.model.DestinationTranslation;
import com.example.travlediary.repository.search.GlobalSearchMapper;
import com.example.travlediary.service.destination.DestinationLocalizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 통합검색에서 여행지 결과만 현재 언어로 바뀐다.
 *
 * <p>번역 조회는 이번 페이지의 여행지 번호를 모아 한 번만 한다.
 * 사용자가 쓴 글(커뮤니티·코스 등)은 제목도 요약도 그대로다.
 */
@ExtendWith(MockitoExtension.class)
class GlobalSearchDestinationLocalizationTest {

    @Mock private GlobalSearchMapper globalSearchMapper;
    @Mock private DestinationLocalizationService destinationLocalizationService;
    @Mock private MessageSource messageSource;

    @InjectMocks
    private GlobalSearchServiceImpl globalSearchService;

    @ParameterizedTest
    @CsvSource({
            "KOREAN, 경복궁, ko",
            "ENGLISH, Gyeongbokgung, en",
            "ENGLISH, Seoul, en",
            "ENGLISH, Landmark, en",
            "JAPANESE, 景福宮, ja",
            "CHINESE_SIMPLIFIED, 景福宫, zh-CN",
            "CHINESE_TRADITIONAL, 景福宮, zh-TW"
    })
    void theCanonicalLanguageCodeReachesBothTheCountAndTheSearch(SupportedLanguage language,
                                                                 String keyword,
                                                                 String expectedLanguageCode) {
        when(globalSearchMapper.count(keyword, "all", expectedLanguageCode)).thenReturn(1L);
        when(globalSearchMapper.search(keyword, "all", expectedLanguageCode, 0L, 10))
                .thenReturn(List.of(destinationResult(15L, "경복궁", "조선의 궁궐")));
        when(destinationLocalizationService.resolveLocalizedContentByDestinationIds(
                List.of(15L), language)).thenReturn(Map.of());

        globalSearchService.search(keyword, "all", 1, language);

        verify(globalSearchMapper).count(keyword, "all", expectedLanguageCode);
        verify(globalSearchMapper).search(keyword, "all", expectedLanguageCode, 0L, 10);
    }

    @Test
    void destinationResultsShowTheTranslatedNameAndSummary() {
        GlobalSearchResultDto palace = destinationResult(15L, "경복궁", "조선의 궁궐");
        GlobalSearchResultDto village = destinationResult(16L, "북촌한옥마을", "한옥 골목");
        when(globalSearchMapper.count("Gyeongbokgung", "destination", "en")).thenReturn(2L);
        when(globalSearchMapper.search("Gyeongbokgung", "destination", "en", 0L, 10))
                .thenReturn(List.of(palace, village));
        when(destinationLocalizationService.resolveLocalizedContentByDestinationIds(
                List.of(15L, 16L), SupportedLanguage.ENGLISH))
                .thenReturn(Map.of(
                        15L, translation(15L, "Gyeongbokgung Palace", "A palace of Joseon"),
                        16L, translation(16L, "Bukchon Hanok Village", null)));

        GlobalSearchPage page = globalSearchService.search(
                "Gyeongbokgung", "destination", 1, SupportedLanguage.ENGLISH);

        assertThat(page.results()).extracting(GlobalSearchResultDto::getTitle)
                .containsExactly("Gyeongbokgung Palace", "Bukchon Hanok Village");
        assertThat(page.results()).extracting(GlobalSearchResultDto::getSummary)
                .containsExactly("A palace of Joseon", "한옥 골목");
        // 여행지 번호를 모아 한 번만 읽는다.
        verify(destinationLocalizationService, times(1))
                .resolveLocalizedContentByDestinationIds(List.of(15L, 16L), SupportedLanguage.ENGLISH);
    }

    @Test
    void destinationWithoutATranslationKeepsTheKoreanValues() {
        when(globalSearchMapper.count("경복궁", "destination", "en")).thenReturn(1L);
        when(globalSearchMapper.search("경복궁", "destination", "en", 0L, 10))
                .thenReturn(List.of(destinationResult(15L, "경복궁", "조선의 궁궐")));
        when(destinationLocalizationService.resolveLocalizedContentByDestinationIds(
                List.of(15L), SupportedLanguage.ENGLISH)).thenReturn(Map.of());

        GlobalSearchPage page = globalSearchService.search(
                "경복궁", "destination", 1, SupportedLanguage.ENGLISH);

        assertThat(page.results().get(0).getTitle()).isEqualTo("경복궁");
        assertThat(page.results().get(0).getSummary()).isEqualTo("조선의 궁궐");
    }

    @Test
    void userWrittenResultsAreNeverTranslated() {
        GlobalSearchResultDto post = new GlobalSearchResultDto();
        post.setType("community");
        post.setId(7L);
        post.setTitle("여행 코스 테스트");
        post.setSummary("<p>여행 코스 입니다</p>");
        GlobalSearchResultDto course = new GlobalSearchResultDto();
        course.setType("course");
        course.setId(9L);
        course.setTitle("서울 하루 고궁 산책");
        course.setSummary("<p>코스 소개</p>");
        when(globalSearchMapper.count("여행", "all", "en")).thenReturn(2L);
        when(globalSearchMapper.search("여행", "all", "en", 0L, 10))
                .thenReturn(List.of(post, course));

        GlobalSearchPage page = globalSearchService.search(
                "여행", "all", 1, SupportedLanguage.ENGLISH);

        assertThat(page.results()).extracting(GlobalSearchResultDto::getTitle)
                .containsExactly("여행 코스 테스트", "서울 하루 고궁 산책");
        assertThat(page.results()).extracting(GlobalSearchResultDto::getSummary)
                .containsExactly("여행 코스 입니다", "코스 소개");
        // 여행지가 없으면 번역을 읽지 않는다.
        verify(destinationLocalizationService, never())
                .resolveLocalizedContentByDestinationIds(any(), any());
    }

    @Test
    void missingPreviewTextComesFromTheMessagesInTheRequestedLanguage() {
        GlobalSearchResultDto notice = new GlobalSearchResultDto();
        notice.setType("notice");
        notice.setId(3L);
        notice.setTitle("공지");
        notice.setSummary("   ");
        when(globalSearchMapper.count("공지", "notice", "ja")).thenReturn(1L);
        when(globalSearchMapper.search("공지", "notice", "ja", 0L, 10)).thenReturn(List.of(notice));
        when(messageSource.getMessage(eq("search.result.noPreview"), any(),
                eq(SupportedLanguage.JAPANESE.getLocale())))
                .thenReturn("本文のプレビューはありません。");

        GlobalSearchPage page = globalSearchService.search(
                "공지", "notice", 1, SupportedLanguage.JAPANESE);

        assertThat(page.results().get(0).getSummary()).isEqualTo("本文のプレビューはありません。");
    }

    private GlobalSearchResultDto destinationResult(Long id, String title, String summary) {
        GlobalSearchResultDto result = new GlobalSearchResultDto();
        result.setType("destination");
        result.setId(id);
        result.setTitle(title);
        result.setSummary(summary);
        result.setDetailUrl("/destinations/" + id);
        return result;
    }

    private DestinationTranslation translation(Long destinationId, String name,
                                               String shortDescription) {
        DestinationTranslation translation = new DestinationTranslation();
        translation.setDestinationId(destinationId);
        translation.setName(name);
        translation.setShortDescription(shortDescription);
        return translation;
    }
}
