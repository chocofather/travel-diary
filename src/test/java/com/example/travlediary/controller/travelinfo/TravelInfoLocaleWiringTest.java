package com.example.travlediary.controller.travelinfo;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.FestivalDetailDto;
import com.example.travlediary.dto.TravelInfoDetailDto;
import com.example.travlediary.dto.TravelInfoListItemDto;
import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.service.category.InfoCategoryService;
import com.example.travlediary.service.category.ReferenceNameLocalizationService;
import com.example.travlediary.service.travelinfo.FestivalDetailService;
import com.example.travlediary.service.travelinfo.TravelInfoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 공개 여행정보 화면이 현재 locale 을 그대로 언어 대체에 넘기는지 본다.
 *
 * <p>목록·상세·축제 상세가 모두 같은 언어를 쓰고, 지원하지 않는 locale 은 한국어로 떨어져야 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TravelInfoLocaleWiringTest {

    @Mock private TravelInfoService travelInfoService;
    @Mock private FestivalDetailService festivalDetailService;
    @Mock private InfoCategoryService infoCategoryService;
    @Mock private ReferenceNameLocalizationService referenceNameLocalizationService;

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void generalListPassesTheRequestedLanguage() {
        LocaleContextHolder.setLocale(SupportedLanguage.ENGLISH.getLocale());
        List<TravelInfoListItemDto> list = List.of(listItem(TravelInfoContentType.GENERAL));
        when(travelInfoService.getPublicList(
                TravelInfoScope.DOMESTIC, TravelInfoContentType.GENERAL,
                List.of(), null, "latest", 0L, 12)).thenReturn(list);

        Model model = new ExtendedModelMap();
        assertThat(list(model, null)).isEqualTo("travel-info/list");

        verify(travelInfoService).localizePublicList(list, SupportedLanguage.ENGLISH);
    }

    @Test
    void festivalListUsesTheSameLocalizationCall() {
        LocaleContextHolder.setLocale(SupportedLanguage.JAPANESE.getLocale());
        List<TravelInfoListItemDto> list = List.of(listItem(TravelInfoContentType.FESTIVAL));
        when(travelInfoService.getPublicList(
                null, TravelInfoContentType.FESTIVAL,
                List.of(), null, "latest", 0L, 12)).thenReturn(list);

        list(new ExtendedModelMap(), "FESTIVAL");

        verify(travelInfoService).localizePublicList(list, SupportedLanguage.JAPANESE);
    }

    @Test
    void simplifiedChineseIsPassedExactly() {
        LocaleContextHolder.setLocale(SupportedLanguage.CHINESE_SIMPLIFIED.getLocale());

        list(new ExtendedModelMap(), null);

        verify(travelInfoService).localizePublicList(
                any(), eq(SupportedLanguage.CHINESE_SIMPLIFIED));
    }

    @Test
    void traditionalChineseIsPassedExactly() {
        LocaleContextHolder.setLocale(SupportedLanguage.CHINESE_TRADITIONAL.getLocale());

        list(new ExtendedModelMap(), null);

        verify(travelInfoService).localizePublicList(
                any(), eq(SupportedLanguage.CHINESE_TRADITIONAL));
    }

    @Test
    void unsupportedLocaleFallsBackToKorean() {
        LocaleContextHolder.setLocale(Locale.FRANCE);

        list(new ExtendedModelMap(), null);

        verify(travelInfoService).localizePublicList(any(), eq(SupportedLanguage.KOREAN));
    }

    @Test
    void generalFilterPillNamesAreLocalizedWithoutTouchingTheCategoryItself() {
        LocaleContextHolder.setLocale(SupportedLanguage.ENGLISH.getLocale());
        InfoCategory seasonal = infoCategory(3L, "계절여행", TravelInfoContentType.GENERAL);
        List<InfoCategory> categories = List.of(seasonal);
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.GENERAL))
                .thenReturn(categories);
        when(referenceNameLocalizationService.localizeInfoCategories(
                categories, SupportedLanguage.ENGLISH))
                .thenReturn(Map.of(3L, "Seasonal travel"));

        Model model = new ExtendedModelMap();
        list(model, null);

        assertThat(model.getAttribute("categoryNames"))
                .isEqualTo(Map.of(3L, "Seasonal travel"));
        // 원본 카테고리는 그대로 모델에 실린다 (관리자 데이터를 건드리지 않는다)
        assertThat(model.getAttribute("categories")).isEqualTo(categories);
        assertThat(seasonal.getName()).isEqualTo("계절여행");
        assertThat(seasonal.getId()).isEqualTo(3L);
        assertThat(seasonal.getContentType()).isEqualTo(TravelInfoContentType.GENERAL);
        assertThat(seasonal.getIsVisible()).isTrue();
    }

    @Test
    void festivalFilterPillNamesUseTheSameLocalizationCall() {
        LocaleContextHolder.setLocale(SupportedLanguage.JAPANESE.getLocale());
        List<InfoCategory> categories =
                List.of(infoCategory(4L, "문화축제", TravelInfoContentType.FESTIVAL));
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.FESTIVAL))
                .thenReturn(categories);
        when(referenceNameLocalizationService.localizeInfoCategories(
                categories, SupportedLanguage.JAPANESE))
                .thenReturn(Map.of(4L, "文化祭り"));

        Model model = new ExtendedModelMap();
        list(model, "FESTIVAL");

        assertThat(model.getAttribute("categoryNames")).isEqualTo(Map.of(4L, "文化祭り"));
        verify(referenceNameLocalizationService)
                .localizeInfoCategories(categories, SupportedLanguage.JAPANESE);
    }

    @Test
    void filterPillsPassTheChineseVariantExactly() {
        LocaleContextHolder.setLocale(SupportedLanguage.CHINESE_TRADITIONAL.getLocale());
        when(infoCategoryService.getVisibleByContentType(TravelInfoContentType.GENERAL))
                .thenReturn(List.of());

        list(new ExtendedModelMap(), null);

        verify(referenceNameLocalizationService).localizeInfoCategories(
                any(), eq(SupportedLanguage.CHINESE_TRADITIONAL));
    }

    @Test
    void generalDetailPassesTheRequestedLanguage() {
        LocaleContextHolder.setLocale(SupportedLanguage.ENGLISH.getLocale());
        TravelInfoDetailDto detail = detail(TravelInfoContentType.GENERAL);
        when(festivalDetailService.isPublicFestival(10L)).thenReturn(false);
        when(travelInfoService.getPublicDetail(10L)).thenReturn(detail);

        Model model = new ExtendedModelMap();
        assertThat(controller().detail(10L, null, null, model)).isEqualTo("travel-info/detail");

        verify(travelInfoService).localizePublicDetail(detail, SupportedLanguage.ENGLISH);
    }

    @Test
    void festivalDetailLocalizesTheSharedTravelInfoPartOnly() {
        LocaleContextHolder.setLocale(SupportedLanguage.JAPANESE.getLocale());
        TravelInfoDetailDto detail = detail(TravelInfoContentType.FESTIVAL);
        FestivalInfo festivalInfo = new FestivalInfo();
        festivalInfo.setInfoId(10L);
        festivalInfo.setEventPlace("경복궁");
        festivalInfo.setAddress("서울특별시 종로구 사직로 161");
        FestivalDetailDto festival = new FestivalDetailDto(detail, festivalInfo, List.of());
        when(festivalDetailService.getPublicDetail(10L)).thenReturn(festival);

        Model model = new ExtendedModelMap();
        assertThat(controller().festivalDetail(10L, null, null, model))
                .isEqualTo("festivals/detail");

        // 제목·본문은 travel_info, 행사 상세정보는 festival_info 쪽 서비스가 맡는다.
        verify(travelInfoService).localizePublicDetail(detail, SupportedLanguage.JAPANESE);
        verify(festivalDetailService).localizePublicDetail(festival, SupportedLanguage.JAPANESE);
    }

    @Test
    void festivalDetailPassesTheChineseVariantExactlyToBothServices() {
        LocaleContextHolder.setLocale(SupportedLanguage.CHINESE_SIMPLIFIED.getLocale());
        TravelInfoDetailDto detail = detail(TravelInfoContentType.FESTIVAL);
        FestivalInfo festivalInfo = new FestivalInfo();
        festivalInfo.setInfoId(10L);
        FestivalDetailDto festival = new FestivalDetailDto(detail, festivalInfo, List.of());
        when(festivalDetailService.getPublicDetail(10L)).thenReturn(festival);

        controller().festivalDetail(10L, null, null, new ExtendedModelMap());

        verify(travelInfoService)
                .localizePublicDetail(detail, SupportedLanguage.CHINESE_SIMPLIFIED);
        verify(festivalDetailService)
                .localizePublicDetail(festival, SupportedLanguage.CHINESE_SIMPLIFIED);
    }

    @Test
    void generalDetailNeverGoesThroughTheFestivalInfoLocalization() {
        LocaleContextHolder.setLocale(SupportedLanguage.ENGLISH.getLocale());
        TravelInfoDetailDto detail = detail(TravelInfoContentType.GENERAL);
        when(festivalDetailService.isPublicFestival(10L)).thenReturn(false);
        when(travelInfoService.getPublicDetail(10L)).thenReturn(detail);

        controller().detail(10L, null, null, new ExtendedModelMap());

        verify(travelInfoService).localizePublicDetail(detail, SupportedLanguage.ENGLISH);
        verify(festivalDetailService, never()).localizePublicDetail(any(), any());
    }

    private String list(Model model, String contentType) {
        return controller().list(null, null, contentType, null, null, 1, 12, null, null, model);
    }

    private TravelInfoController controller() {
        return new TravelInfoController(
                travelInfoService, festivalDetailService, infoCategoryService,
                referenceNameLocalizationService, messageSource());
    }

    /** 화면 제목은 실제 번들과 같은 키를 쓴다. 여기서는 키가 있다는 것만 확인하면 된다. */
    private StaticMessageSource messageSource() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.setUseCodeAsDefaultMessage(true);
        return messages;
    }

    private InfoCategory infoCategory(Long id, String name, TravelInfoContentType contentType) {
        InfoCategory category = new InfoCategory();
        category.setId(id);
        category.setName(name);
        category.setContentType(contentType);
        category.setDisplayOrder(1);
        category.setIsVisible(true);
        return category;
    }

    private TravelInfoListItemDto listItem(TravelInfoContentType type) {
        TravelInfoListItemDto item = new TravelInfoListItemDto();
        item.setId(10L);
        item.setTitle("공개 여행정보");
        item.setScope(TravelInfoScope.DOMESTIC);
        item.setContentType(type);
        item.setCategoryId(3L);
        item.setCategoryName("계절여행");
        item.setViews(7);
        item.setCreatedAt(Timestamp.valueOf("2026-08-10 09:00:00"));
        return item;
    }

    private TravelInfoDetailDto detail(TravelInfoContentType type) {
        TravelInfoDetailDto detail = new TravelInfoDetailDto();
        detail.setId(10L);
        detail.setTitle("공개 여행정보");
        detail.setScope(TravelInfoScope.DOMESTIC);
        detail.setContentType(type);
        detail.setCategoryName("계절여행");
        detail.setContent("<p>본문</p>");
        detail.setViews(7);
        detail.setCreatedAt(Timestamp.valueOf("2026-08-10 09:00:00"));
        return detail;
    }
}
