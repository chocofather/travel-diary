package com.example.travlediary.service.bookmark;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.MyPageTravelInfoBookmarkDto;
import com.example.travlediary.model.InfoCategoryTranslation;
import com.example.travlediary.repository.bookmark.MyPageBookmarkMapper;
import com.example.travlediary.repository.category.CategoryMapper;
import com.example.travlediary.repository.category.CountryCategoryMapper;
import com.example.travlediary.repository.category.InfoCategoryMapper;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.category.LocalizedReferenceNameResolver;
import com.example.travlediary.service.category.ReferenceNameLocalizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 마이페이지 여행정보 북마크의 카테고리 이름만 요청 언어로 바뀌는지 본다.
 *
 * <p>제목·범위·썸네일과 여행지/커뮤니티 북마크는 그대로 남아야 한다.
 */
@ExtendWith(MockitoExtension.class)
class MyPageTravelInfoBookmarkLocalizationTest {

    @Mock private MyPageBookmarkMapper myPageBookmarkMapper;
    @Mock private CountryCategoryService countryCategoryService;
    @Mock private CountryCategoryMapper countryCategoryMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private InfoCategoryMapper infoCategoryMapper;

    private MyPageBookmarkService service;

    @BeforeEach
    void setUp() {
        service = new MyPageBookmarkService(myPageBookmarkMapper, countryCategoryService,
                new ReferenceNameLocalizationService(countryCategoryMapper, categoryMapper,
                        infoCategoryMapper, new LocalizedReferenceNameResolver()));
    }

    @Test
    void travelInfoBookmarkCategoryNamesUseTheRequestedLanguage() {
        MyPageTravelInfoBookmarkDto bookmark = bookmark(10L, 3L, "계절여행");
        when(infoCategoryMapper.findTranslationsByCategoryIds(List.of(3L))).thenReturn(List.of(
                categoryTranslation(1L, 3L, "ko", "계절여행"),
                categoryTranslation(2L, 3L, "en", "Seasonal travel")));

        service.localizeTravelInfoBookmarks(List.of(bookmark), SupportedLanguage.ENGLISH);

        assertThat(bookmark.getCategoryName()).isEqualTo("Seasonal travel");
        // 나머지 값은 그대로다
        assertThat(bookmark.getTitle()).isEqualTo("봄 여행 준비물");
        assertThat(bookmark.getScope()).isEqualTo("DOMESTIC");
        assertThat(bookmark.getCategoryId()).isEqualTo(3L);
    }

    @Test
    void travelInfoBookmarkCategoryNamesFallBackToKorean() {
        MyPageTravelInfoBookmarkDto bookmark = bookmark(10L, 3L, "계절여행");
        when(infoCategoryMapper.findTranslationsByCategoryIds(List.of(3L))).thenReturn(List.of(
                categoryTranslation(1L, 3L, "ko", "계절여행")));

        service.localizeTravelInfoBookmarks(List.of(bookmark), SupportedLanguage.JAPANESE);

        assertThat(bookmark.getCategoryName()).isEqualTo("계절여행");
    }

    @Test
    void chineseVariantsAreNotSwappedForEachOther() {
        MyPageTravelInfoBookmarkDto bookmark = bookmark(10L, 3L, "계절여행");
        when(infoCategoryMapper.findTranslationsByCategoryIds(List.of(3L))).thenReturn(List.of(
                categoryTranslation(4L, 3L, "zh-TW", "季節旅行"),
                categoryTranslation(1L, 3L, "ko", "계절여행")));

        service.localizeTravelInfoBookmarks(
                List.of(bookmark), SupportedLanguage.CHINESE_SIMPLIFIED);

        assertThat(bookmark.getCategoryName()).isEqualTo("계절여행");
    }

    @Test
    void manyBookmarksShareASingleCategoryTranslationQuery() {
        MyPageTravelInfoBookmarkDto general = bookmark(10L, 3L, "계절여행");
        MyPageTravelInfoBookmarkDto festival = bookmark(11L, 4L, "문화축제");
        MyPageTravelInfoBookmarkDto sameCategory = bookmark(12L, 3L, "계절여행");
        when(infoCategoryMapper.findTranslationsByCategoryIds(List.of(3L, 4L))).thenReturn(List.of(
                categoryTranslation(2L, 3L, "en", "Seasonal travel"),
                categoryTranslation(3L, 4L, "en", "Culture festival")));

        service.localizeTravelInfoBookmarks(
                List.of(general, festival, sameCategory), SupportedLanguage.ENGLISH);

        assertThat(List.of(general, festival, sameCategory))
                .extracting(MyPageTravelInfoBookmarkDto::getCategoryName)
                .containsExactly("Seasonal travel", "Culture festival", "Seasonal travel");
        verify(infoCategoryMapper, times(1)).findTranslationsByCategoryIds(List.of(3L, 4L));
        verify(infoCategoryMapper, never()).findTranslationsByCategoryId(anyLong());
    }

    @Test
    void emptyBookmarkListNeverReadsTranslations() {
        service.localizeTravelInfoBookmarks(List.of(), SupportedLanguage.ENGLISH);
        service.localizeTravelInfoBookmarks(null, SupportedLanguage.ENGLISH);

        verify(infoCategoryMapper, never()).findTranslationsByCategoryIds(anyList());
        verifyNoInteractions(myPageBookmarkMapper);
    }

    @Test
    void otherBookmarkKindsAreLeftAloneSoDestinationCategoriesDoNotClash() {
        // 여행지·커뮤니티 북마크는 카테고리 축이 달라 이 경로에서 건드리지 않는다.
        service.localizeTravelInfoBookmarks(
                List.of("destination-bookmark", 42), SupportedLanguage.ENGLISH);

        verify(infoCategoryMapper, never()).findTranslationsByCategoryIds(anyList());
        verifyNoInteractions(categoryMapper, countryCategoryMapper);
    }

    private MyPageTravelInfoBookmarkDto bookmark(Long targetId, Long categoryId,
                                                 String categoryName) {
        MyPageTravelInfoBookmarkDto bookmark = new MyPageTravelInfoBookmarkDto();
        bookmark.setTargetId(targetId);
        bookmark.setTitle("봄 여행 준비물");
        bookmark.setScope("DOMESTIC");
        bookmark.setContentType("GENERAL");
        bookmark.setCategoryId(categoryId);
        bookmark.setCategoryName(categoryName);
        return bookmark;
    }

    private InfoCategoryTranslation categoryTranslation(Long id, Long infoCategoryId,
                                                        String languageCode, String name) {
        InfoCategoryTranslation translation = new InfoCategoryTranslation();
        translation.setId(id);
        translation.setInfoCategoryId(infoCategoryId);
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        return translation;
    }
}
