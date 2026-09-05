package com.example.travlediary.service.travelinfo;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.AdminTravelInfoListItemDto;
import com.example.travlediary.dto.TravelInfoDetailDto;
import com.example.travlediary.dto.TravelInfoListItemDto;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.InfoCategoryTranslation;
import com.example.travlediary.model.TravelInfo;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.model.TravelInfoTranslation;
import com.example.travlediary.repository.bookmark.BookmarkMapper;
import com.example.travlediary.repository.category.InfoCategoryMapper;
import com.example.travlediary.repository.travelinfo.TravelInfoMapper;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.repository.category.CategoryMapper;
import com.example.travlediary.repository.category.CountryCategoryMapper;
import com.example.travlediary.service.category.LocalizedReferenceNameResolver;
import com.example.travlediary.service.category.ReferenceNameLocalizationService;
import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 공개 목록·상세에 붙인 언어 대체가 실제로 화면 값만 바꾸는지 본다.
 *
 * <p>카테고리 이름과 축제 상세정보는 아직 번역 대상이 아니므로 원문 그대로 남아야 하고,
 * 관리자 조회는 번역을 아예 읽지 않아야 한다.
 */
@ExtendWith(MockitoExtension.class)
class TravelInfoPublicLocalizationTest {

    @Mock private TravelInfoMapper travelInfoMapper;
    @Mock private BookmarkMapper bookmarkMapper;
    @Mock private InfoCategoryMapper infoCategoryMapper;
    @Mock private FileUploadService fileUploadService;

    private TravelInfoService travelInfoService;

    @BeforeEach
    void setUp() {
        travelInfoService = new TravelInfoService(
                travelInfoMapper, bookmarkMapper, infoCategoryMapper,
                new PostContentSanitizer(), fileUploadService,
                new TravelInfoLocalizationService(travelInfoMapper),
                new ReferenceNameLocalizationService(
                        org.mockito.Mockito.mock(CountryCategoryMapper.class),
                        org.mockito.Mockito.mock(CategoryMapper.class),
                        infoCategoryMapper, new LocalizedReferenceNameResolver()));
    }

    @Test
    void listShowsTheRequestedLanguageTitle() {
        List<TravelInfoListItemDto> list = new ArrayList<>(List.of(
                listItem(10L, "봄 여행 준비물", TravelInfoContentType.GENERAL)));
        when(travelInfoMapper.findTranslationsByInfoIds(List.of(10L))).thenReturn(List.of(
                translation(1L, 10L, "ko", "봄 여행 준비물", null),
                translation(2L, 10L, "en", "Spring packing list", null)));

        travelInfoService.localizePublicList(list, SupportedLanguage.ENGLISH);

        assertThat(list.get(0).getTitle()).isEqualTo("Spring packing list");
    }

    @Test
    void listFallsBackToKoreanTitleWhenTheRequestedLanguageRowIsMissing() {
        List<TravelInfoListItemDto> list = new ArrayList<>(List.of(
                listItem(10L, "봄 여행 준비물", TravelInfoContentType.GENERAL)));
        when(travelInfoMapper.findTranslationsByInfoIds(List.of(10L))).thenReturn(List.of(
                translation(1L, 10L, "ko", "봄 여행 준비물", null)));

        travelInfoService.localizePublicList(list, SupportedLanguage.ENGLISH);

        assertThat(list.get(0).getTitle()).isEqualTo("봄 여행 준비물");
    }

    @Test
    void festivalListItemsUseTheSameTranslationTableAndKeepCategoryAndPeriod() {
        TravelInfoListItemDto festival = listItem(11L, "벚꽃 축제", TravelInfoContentType.FESTIVAL);
        List<TravelInfoListItemDto> list = new ArrayList<>(List.of(festival));
        when(travelInfoMapper.findTranslationsByInfoIds(List.of(11L))).thenReturn(List.of(
                translation(3L, 11L, "en", "Cherry Blossom Festival", null)));

        travelInfoService.localizePublicList(list, SupportedLanguage.ENGLISH);

        assertThat(festival.getTitle()).isEqualTo("Cherry Blossom Festival");
        // 카테고리 이름은 info_category_translations 후속 단계다.
        assertThat(festival.getCategoryName()).isEqualTo("계절여행");
        assertThat(festival.getContentType()).isEqualTo(TravelInfoContentType.FESTIVAL);
        assertThat(festival.getThumbnailUrl()).isEqualTo("/uploads/travel-info/thumb.png");
        assertThat(festival.getViews()).isEqualTo(7);
    }

    @Test
    void manyListItemsReadEveryTranslationInASingleQuery() {
        List<TravelInfoListItemDto> list = new ArrayList<>(List.of(
                listItem(10L, "봄 여행 준비물", TravelInfoContentType.GENERAL),
                listItem(11L, "벚꽃 축제", TravelInfoContentType.FESTIVAL),
                listItem(12L, "환전 요령", TravelInfoContentType.GENERAL)));
        when(travelInfoMapper.findTranslationsByInfoIds(List.of(10L, 11L, 12L))).thenReturn(List.of(
                translation(2L, 10L, "en", "Spring packing list", null),
                translation(3L, 11L, "en", "Cherry Blossom Festival", null)));

        travelInfoService.localizePublicList(list, SupportedLanguage.ENGLISH);

        assertThat(list).extracting(TravelInfoListItemDto::getTitle)
                .containsExactly("Spring packing list", "Cherry Blossom Festival", "환전 요령");
        verify(travelInfoMapper, times(1)).findTranslationsByInfoIds(List.of(10L, 11L, 12L));
        verify(travelInfoMapper, never()).findTranslationsByInfoId(anyLong());
    }

    @Test
    void emptyListNeverReadsTranslations() {
        List<TravelInfoListItemDto> list = new ArrayList<>();

        travelInfoService.localizePublicList(list, SupportedLanguage.ENGLISH);

        assertThat(list).isEmpty();
        verify(travelInfoMapper, never()).findTranslationsByInfoIds(anyList());
        verify(travelInfoMapper, never()).findTranslationsByInfoId(anyLong());
    }

    @Test
    void detailShowsTheRequestedLanguageTitleAndContent() {
        TravelInfoDetailDto detail = publicDetail(TravelInfoContentType.GENERAL);
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                translation(1L, 10L, "ko", "봄 여행 준비물", "<p>한국어 본문</p>"),
                translation(2L, 10L, "en", "Spring packing list", "<p>English body</p>")));

        travelInfoService.localizePublicDetail(detail, SupportedLanguage.ENGLISH);

        assertThat(detail.getTitle()).isEqualTo("Spring packing list");
        assertThat(detail.getContent()).isEqualTo("<p>English body</p>");
    }

    @Test
    void detailFallsBackPerFieldSoTitleAndContentCanComeFromDifferentLanguages() {
        TravelInfoDetailDto detail = publicDetail(TravelInfoContentType.GENERAL);
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                translation(1L, 10L, "ko", "봄 여행 준비물", "<p>한국어 본문</p>"),
                // 영어 줄에 제목만 있고 본문은 빈 Quill HTML 이다.
                translation(2L, 10L, "en", "Spring packing list", "<p><br></p>")));

        travelInfoService.localizePublicDetail(detail, SupportedLanguage.ENGLISH);

        assertThat(detail.getTitle()).isEqualTo("Spring packing list");
        assertThat(detail.getContent()).isEqualTo("<p>한국어 본문</p>");
    }

    @Test
    void detailKeepsBaseValuesWhenNoTranslationExists() {
        TravelInfoDetailDto detail = publicDetail(TravelInfoContentType.GENERAL);
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of());

        travelInfoService.localizePublicDetail(detail, SupportedLanguage.JAPANESE);

        assertThat(detail.getTitle()).isEqualTo("봄 여행 준비물");
        assertThat(detail.getContent()).isEqualTo("<p>원문 본문</p>");
        assertThat(detail.getCategoryName()).isEqualTo("계절여행");
    }

    @Test
    void translatedContentGoesThroughTheSameSanitizer() {
        TravelInfoDetailDto detail = publicDetail(TravelInfoContentType.GENERAL);
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                translation(2L, 10L, "en", "Spring packing list",
                        "<p onclick=\"alert(1)\">English body</p><script>alert(1)</script>")));

        travelInfoService.localizePublicDetail(detail, SupportedLanguage.ENGLISH);

        assertThat(detail.getContent()).isEqualTo("<p>English body</p>");
    }

    @Test
    void festivalDetailLocalizesOnlyTitleAndContent() {
        TravelInfoDetailDto detail = publicDetail(TravelInfoContentType.FESTIVAL);
        detail.setTitle("벚꽃 축제");
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                translation(3L, 10L, "en", "Cherry Blossom Festival", "<p>English body</p>")));

        travelInfoService.localizePublicDetail(detail, SupportedLanguage.ENGLISH);

        assertThat(detail.getTitle()).isEqualTo("Cherry Blossom Festival");
        assertThat(detail.getContent()).isEqualTo("<p>English body</p>");
        // 카테고리 이름과 기간은 이번 단계 대상이 아니다.
        assertThat(detail.getCategoryName()).isEqualTo("계절여행");
        assertThat(detail.getContentType()).isEqualTo(TravelInfoContentType.FESTIVAL);
    }

    // ─── 카테고리 이름 (GENERAL / FESTIVAL 공용) ───

    @Test
    void listCardCategoryNamesUseTheRequestedLanguage() {
        List<TravelInfoListItemDto> list = new ArrayList<>(List.of(
                listItem(10L, "봄 여행 준비물", TravelInfoContentType.GENERAL),
                listItem(11L, "벚꽃 축제", TravelInfoContentType.FESTIVAL)));
        when(travelInfoMapper.findTranslationsByInfoIds(List.of(10L, 11L))).thenReturn(List.of());
        when(infoCategoryMapper.findTranslationsByCategoryIds(List.of(3L))).thenReturn(List.of(
                categoryTranslation(1L, 3L, "ko", "계절여행"),
                categoryTranslation(2L, 3L, "en", "Seasonal travel")));

        travelInfoService.localizePublicList(list, SupportedLanguage.ENGLISH);

        assertThat(list).extracting(TravelInfoListItemDto::getCategoryName)
                .containsExactly("Seasonal travel", "Seasonal travel");
    }

    @Test
    void listCardCategoryNamesFallBackToKoreanWhenTheLanguageIsMissing() {
        List<TravelInfoListItemDto> list = new ArrayList<>(List.of(
                listItem(10L, "봄 여행 준비물", TravelInfoContentType.GENERAL)));
        when(travelInfoMapper.findTranslationsByInfoIds(List.of(10L))).thenReturn(List.of());
        when(infoCategoryMapper.findTranslationsByCategoryIds(List.of(3L))).thenReturn(List.of(
                categoryTranslation(1L, 3L, "ko", "계절여행")));

        travelInfoService.localizePublicList(list, SupportedLanguage.JAPANESE);

        assertThat(list.get(0).getCategoryName()).isEqualTo("계절여행");
    }

    @Test
    void manyCardsShareASingleCategoryTranslationQuery() {
        TravelInfoListItemDto festival = listItem(11L, "벚꽃 축제", TravelInfoContentType.FESTIVAL);
        festival.setCategoryId(4L);
        festival.setCategoryName("문화축제");
        List<TravelInfoListItemDto> list = new ArrayList<>(List.of(
                listItem(10L, "봄 여행 준비물", TravelInfoContentType.GENERAL),
                festival,
                listItem(12L, "환전 요령", TravelInfoContentType.GENERAL)));
        when(travelInfoMapper.findTranslationsByInfoIds(List.of(10L, 11L, 12L)))
                .thenReturn(List.of());
        when(infoCategoryMapper.findTranslationsByCategoryIds(List.of(3L, 4L))).thenReturn(List.of(
                categoryTranslation(2L, 3L, "en", "Seasonal travel"),
                categoryTranslation(3L, 4L, "en", "Culture festival")));

        travelInfoService.localizePublicList(list, SupportedLanguage.ENGLISH);

        assertThat(list).extracting(TravelInfoListItemDto::getCategoryName)
                .containsExactly("Seasonal travel", "Culture festival", "Seasonal travel");
        // 카드가 몇 장이든 카테고리 번역 조회는 한 번이다.
        verify(infoCategoryMapper, times(1)).findTranslationsByCategoryIds(List.of(3L, 4L));
        verify(infoCategoryMapper, never()).findTranslationsByCategoryId(anyLong());
    }

    @Test
    void emptyListNeverReadsCategoryTranslations() {
        travelInfoService.localizePublicList(new ArrayList<>(), SupportedLanguage.ENGLISH);

        verify(infoCategoryMapper, never()).findTranslationsByCategoryIds(anyList());
        verify(infoCategoryMapper, never()).findTranslationsByCategoryId(anyLong());
    }

    @Test
    void detailCategoryNameUsesTheRequestedLanguage() {
        TravelInfoDetailDto detail = publicDetail(TravelInfoContentType.GENERAL);
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of());
        when(infoCategoryMapper.findTranslationsByCategoryIds(List.of(3L))).thenReturn(List.of(
                categoryTranslation(2L, 3L, "en", "Seasonal travel")));

        travelInfoService.localizePublicDetail(detail, SupportedLanguage.ENGLISH);

        assertThat(detail.getCategoryName()).isEqualTo("Seasonal travel");
        // 제목·본문 언어 대체는 그대로 동작한다.
        assertThat(detail.getTitle()).isEqualTo("봄 여행 준비물");
        assertThat(detail.getContent()).isEqualTo("<p>원문 본문</p>");
    }

    @Test
    void festivalDetailCategoryNameUsesTheSameCategoryTranslations() {
        TravelInfoDetailDto detail = publicDetail(TravelInfoContentType.FESTIVAL);
        detail.setCategoryId(4L);
        detail.setCategoryName("문화축제");
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of());
        when(infoCategoryMapper.findTranslationsByCategoryIds(List.of(4L))).thenReturn(List.of(
                categoryTranslation(3L, 4L, "ja", "文化祭り")));

        travelInfoService.localizePublicDetail(detail, SupportedLanguage.JAPANESE);

        assertThat(detail.getCategoryName()).isEqualTo("文化祭り");
    }

    @Test
    void chineseVariantsAreNotSwappedForEachOtherOnCategoryNames() {
        TravelInfoDetailDto detail = publicDetail(TravelInfoContentType.GENERAL);
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of());
        when(infoCategoryMapper.findTranslationsByCategoryIds(List.of(3L))).thenReturn(List.of(
                categoryTranslation(4L, 3L, "zh-CN", "季节旅行"),
                categoryTranslation(1L, 3L, "ko", "계절여행")));

        travelInfoService.localizePublicDetail(detail, SupportedLanguage.CHINESE_TRADITIONAL);

        assertThat(detail.getCategoryName()).isEqualTo("계절여행");
    }

    @Test
    void detailWithoutACategoryIdKeepsTheBaseCategoryName() {
        TravelInfoDetailDto detail = publicDetail(TravelInfoContentType.GENERAL);
        detail.setCategoryId(null);
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of());

        travelInfoService.localizePublicDetail(detail, SupportedLanguage.ENGLISH);

        assertThat(detail.getCategoryName()).isEqualTo("계절여행");
        verify(infoCategoryMapper, never()).findTranslationsByCategoryIds(anyList());
    }

    @Test
    void adminListAndDetailNeverReadTranslations() {
        AdminTravelInfoListItemDto adminItem = new AdminTravelInfoListItemDto();
        adminItem.setId(10L);
        adminItem.setTitle("봄 여행 준비물");
        when(travelInfoMapper.findAdminList(null, null, null)).thenReturn(List.of(adminItem));

        TravelInfo base = new TravelInfo();
        base.setId(10L);
        base.setTitle("봄 여행 준비물");
        base.setContent("<p>원문 본문</p>");
        base.setContentType(TravelInfoContentType.GENERAL);
        base.setCategoryId(3L);
        InfoCategory category = new InfoCategory();
        category.setId(3L);
        category.setName("계절여행");
        when(travelInfoMapper.findById(10L)).thenReturn(base);
        when(infoCategoryMapper.findById(3L)).thenReturn(category);

        assertThat(travelInfoService.getAdminList(null, null, null))
                .extracting(AdminTravelInfoListItemDto::getTitle)
                .containsExactly("봄 여행 준비물");
        assertThat(travelInfoService.getAdminDetail(10L).getTitle()).isEqualTo("봄 여행 준비물");

        verify(travelInfoMapper, never()).findTranslationsByInfoId(anyLong());
        verify(travelInfoMapper, never()).findTranslationsByInfoIds(anyList());
    }

    private TravelInfoListItemDto listItem(Long id, String title, TravelInfoContentType type) {
        TravelInfoListItemDto item = new TravelInfoListItemDto();
        item.setId(id);
        item.setTitle(title);
        item.setScope(TravelInfoScope.DOMESTIC);
        item.setContentType(type);
        item.setCategoryId(3L);
        item.setCategoryName("계절여행");
        item.setThumbnailUrl("/uploads/travel-info/thumb.png");
        item.setViews(7);
        item.setCreatedAt(Timestamp.valueOf("2026-08-10 09:00:00"));
        return item;
    }

    private TravelInfoDetailDto publicDetail(TravelInfoContentType type) {
        TravelInfoDetailDto detail = new TravelInfoDetailDto();
        detail.setId(10L);
        detail.setTitle("봄 여행 준비물");
        detail.setScope(TravelInfoScope.DOMESTIC);
        detail.setContentType(type);
        detail.setCategoryId(3L);
        detail.setCategoryName("계절여행");
        detail.setContent("<p>원문 본문</p>");
        detail.setViews(7);
        detail.setCreatedAt(Timestamp.valueOf("2026-08-10 09:00:00"));
        return detail;
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

    private TravelInfoTranslation translation(Long id, Long travelInfoId, String languageCode,
                                              String title, String content) {
        TravelInfoTranslation translation = new TravelInfoTranslation();
        translation.setId(id);
        translation.setTravelInfoId(travelInfoId);
        translation.setLanguageCode(languageCode);
        translation.setTitle(title);
        translation.setContent(content);
        return translation;
    }
}
