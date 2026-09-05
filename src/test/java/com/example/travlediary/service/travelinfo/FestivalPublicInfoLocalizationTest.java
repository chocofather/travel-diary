package com.example.travlediary.service.travelinfo;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.FestivalDetailDto;
import com.example.travlediary.dto.TravelInfoDetailDto;
import com.example.travlediary.dto.TravelInfoPeriodDto;
import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.model.FestivalInfoTranslation;
import com.example.travlediary.model.InfoImage;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.repository.travelinfo.FestivalInfoMapper;
import com.example.travlediary.repository.travelinfo.TravelInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 공개 축제 상세의 행사 정보가 요청 언어로 바뀌는지 본다.
 *
 * <p>연락처·홈페이지·기간·이미지와 TourAPI 식별자는 언어와 무관하게 그대로 남아야 한다.
 */
@ExtendWith(MockitoExtension.class)
class FestivalPublicInfoLocalizationTest {

    @Mock private TravelInfoService travelInfoService;
    @Mock private TravelInfoMapper travelInfoMapper;
    @Mock private FestivalInfoMapper festivalInfoMapper;

    private FestivalDetailService service;

    @BeforeEach
    void setUp() {
        service = new FestivalDetailService(travelInfoService, travelInfoMapper, festivalInfoMapper,
                new FestivalInfoLocalizationService(festivalInfoMapper));
    }

    @Test
    void requestedLanguageValuesAreShown() {
        FestivalDetailDto festival = festival();
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                korean(1L), english(2L)));

        service.localizePublicDetail(festival, SupportedLanguage.ENGLISH);

        assertThat(festival.getEventPlace()).isEqualTo("Gyeongbokgung Palace");
        assertThat(festival.getAddress()).isEqualTo("161 Sajik-ro, Jongno-gu, Seoul");
        assertThat(festival.getPlayTime()).isEqualTo("Part 1 18:20-20:10");
        assertThat(festival.getUseTime()).isEqualTo("KRW 60,000 per person");
        assertThat(festival.getSponsor1()).isEqualTo("Korea Heritage Service");
        assertThat(festival.getSponsor2()).isEqualTo("Korea Heritage Agency");
    }

    @Test
    void everyFieldFallsBackIndependently() {
        FestivalDetailDto festival = festival();
        FestivalInfoTranslation partialEnglish = english(2L);
        partialEnglish.setUseTime("   ");
        partialEnglish.setSponsor2(null);
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                korean(1L), partialEnglish));

        service.localizePublicDetail(festival, SupportedLanguage.ENGLISH);

        // 영어 줄에 있는 값만 영어로 나오고, 빈 칸은 한국어로 떨어진다.
        assertThat(festival.getEventPlace()).isEqualTo("Gyeongbokgung Palace");
        assertThat(festival.getAddress()).isEqualTo("161 Sajik-ro, Jongno-gu, Seoul");
        assertThat(festival.getUseTime()).isEqualTo("1인 60,000원");
        assertThat(festival.getSponsor2()).isEqualTo("국가유산진흥원");
    }

    @Test
    void koreanIsUsedWhenTheRequestedLanguageRowIsMissing() {
        FestivalDetailDto festival = festival();
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(korean(1L)));

        service.localizePublicDetail(festival, SupportedLanguage.JAPANESE);

        assertThat(festival.getEventPlace()).isEqualTo("경복궁");
        assertThat(festival.getUseTime()).isEqualTo("1인 60,000원");
    }

    @Test
    void deterministicRemainingLanguageIsUsedWhenKoreanIsMissingToo() {
        FestivalDetailDto festival = festival();
        FestivalInfoTranslation japanese = new FestivalInfoTranslation();
        japanese.setId(9L);
        japanese.setInfoId(41L);
        japanese.setLanguageCode("ja");
        japanese.setEventPlace("景福宮");
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                japanese, english(2L)));

        service.localizePublicDetail(festival, SupportedLanguage.CHINESE_SIMPLIFIED);

        // language_code ASC 로 en 이 ja 보다 앞이다.
        assertThat(festival.getEventPlace()).isEqualTo("Gyeongbokgung Palace");
    }

    @Test
    void baseValuesStayWhenThereIsNoTranslationAtAll() {
        FestivalDetailDto festival = festival();
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of());

        service.localizePublicDetail(festival, SupportedLanguage.ENGLISH);

        assertThat(festival.getEventPlace()).isEqualTo("경복궁");
        assertThat(festival.getAddress()).isEqualTo("서울특별시 종로구 사직로 161");
        assertThat(festival.getPlayTime()).isEqualTo("1부 18:20~20:10");
        assertThat(festival.getUseTime()).isEqualTo("1인 60,000원");
        assertThat(festival.getSponsor1()).isEqualTo("국가유산청");
        assertThat(festival.getSponsor2()).isEqualTo("국가유산진흥원");
    }

    @Test
    void traditionalChineseDoesNotPreferSimplifiedChineseOverKorean() {
        FestivalDetailDto festival = festival();
        FestivalInfoTranslation simplified = new FestivalInfoTranslation();
        simplified.setId(4L);
        simplified.setInfoId(41L);
        simplified.setLanguageCode("zh-CN");
        simplified.setEventPlace("景福宫");
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                simplified, korean(1L)));

        service.localizePublicDetail(festival, SupportedLanguage.CHINESE_TRADITIONAL);

        assertThat(festival.getEventPlace()).isEqualTo("경복궁");
    }

    @Test
    void simplifiedChineseDoesNotPreferTraditionalChineseOverKorean() {
        FestivalDetailDto festival = festival();
        FestivalInfoTranslation traditional = new FestivalInfoTranslation();
        traditional.setId(5L);
        traditional.setInfoId(41L);
        traditional.setLanguageCode("zh-TW");
        traditional.setEventPlace("景福宮");
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                traditional, korean(1L)));

        service.localizePublicDetail(festival, SupportedLanguage.CHINESE_SIMPLIFIED);

        assertThat(festival.getEventPlace()).isEqualTo("경복궁");
    }

    /** 행사 정보 섹션이 번역 때문에 사라지면 안 된다. */
    @Test
    void emptyTranslationRowKeepsTheEventInfoSectionVisible() {
        FestivalDetailDto festival = festival();
        FestivalInfoTranslation blankEnglish = new FestivalInfoTranslation();
        blankEnglish.setId(2L);
        blankEnglish.setInfoId(41L);
        blankEnglish.setLanguageCode("en");
        blankEnglish.setEventPlace("   ");
        when(festivalInfoMapper.findTranslationsByInfoId(41L))
                .thenReturn(List.of(blankEnglish));

        assertThat(festival.isEventInfoPresent()).isTrue();
        service.localizePublicDetail(festival, SupportedLanguage.ENGLISH);

        assertThat(festival.isEventInfoPresent()).isTrue();
        assertThat(festival.getEventPlace()).isEqualTo("경복궁");
    }

    @Test
    void nonTranslatedValuesAreNeverTouched() {
        FestivalDetailDto festival = festival();
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(english(2L)));

        service.localizePublicDetail(festival, SupportedLanguage.ENGLISH);

        assertThat(festival.getSponsor1Tel()).isEqualTo("02-1234-5678");
        assertThat(festival.getSponsor2Tel()).isEqualTo("02-9876-5432");
        assertThat(festival.getContactTel()).isEqualTo("1522-2295");
        assertThat(festival.getHomepageUrl()).isEqualTo("https://www.example.com/festival");
        assertThat(festival.getFestivalInfo().getSourceType()).isEqualTo("KTO_TOURAPI");
        assertThat(festival.getFestivalInfo().getExternalContentId()).isEqualTo("2648460");
        // 기간·이미지·출처도 그대로다
        assertThat(festival.getPrimaryPeriod().getStartDate())
                .isEqualTo(LocalDate.parse("2026-09-02"));
        assertThat(festival.getImageUrl()).isEqualTo("/uploads/travel-info/festivals/local.jpg");
        assertThat(festival.getImageSourceName()).isEqualTo("한국관광공사");
        assertThat(festival.getLicenseCode()).isEqualTo("KOGL_TYPE_3");
    }

    @Test
    void travelInfoTitleAndCategoryLocalizationIsLeftToItsOwnService() {
        FestivalDetailDto festival = festival();
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(english(2L)));

        service.localizePublicDetail(festival, SupportedLanguage.ENGLISH);

        // 제목·본문·카테고리는 travel_info 쪽 서비스가 따로 맡는다.
        assertThat(festival.getTravelInfo().getTitle()).isEqualTo("경복궁 별빛야행");
        assertThat(festival.getTravelInfo().getCategoryName()).isEqualTo("문화축제");
        verify(travelInfoService, never()).localizePublicDetail(any(), any());
    }

    @Test
    void detailReadsFestivalTranslationsOnce() {
        FestivalDetailDto festival = festival();
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(english(2L)));

        service.localizePublicDetail(festival, SupportedLanguage.ENGLISH);

        verify(festivalInfoMapper, times(1)).findTranslationsByInfoId(41L);
        verify(festivalInfoMapper, never()).findTranslationsByInfoIds(anyList());
    }

    @Test
    void festivalWithoutStoredDetailInfoIsLeftAlone() {
        FestivalDetailDto festival = new FestivalDetailDto(travelInfo(), null, List.of());

        service.localizePublicDetail(festival, SupportedLanguage.ENGLISH);
        service.localizePublicDetail(null, SupportedLanguage.ENGLISH);

        assertThat(festival.getEventPlace()).isNull();
        assertThat(festival.getSponsor1()).isNull();
        // 기간은 travel_info 쪽 값이라 행사 정보 섹션은 그대로 남는다.
        assertThat(festival.isEventInfoPresent()).isTrue();
        verify(festivalInfoMapper, never()).findTranslationsByInfoId(any());
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }

    private FestivalDetailDto festival() {
        return new FestivalDetailDto(travelInfo(), festivalInfo(), List.of(mainImage()));
    }

    private TravelInfoDetailDto travelInfo() {
        TravelInfoDetailDto detail = new TravelInfoDetailDto();
        detail.setId(41L);
        detail.setTitle("경복궁 별빛야행");
        detail.setScope(TravelInfoScope.DOMESTIC);
        detail.setContentType(TravelInfoContentType.FESTIVAL);
        detail.setCategoryId(4L);
        detail.setCategoryName("문화축제");
        detail.setContent("<p>행사 소개</p>");
        detail.setViews(7);
        detail.setCreatedAt(Timestamp.valueOf("2026-08-01 09:00:00"));
        detail.setPeriods(List.of(new TravelInfoPeriodDto(
                LocalDate.parse("2026-09-02"), LocalDate.parse("2026-10-24"))));
        return detail;
    }

    private FestivalInfo festivalInfo() {
        FestivalInfo info = new FestivalInfo();
        info.setInfoId(41L);
        info.setEventPlace("경복궁");
        info.setAddress("서울특별시 종로구 사직로 161");
        info.setPlayTime("1부 18:20~20:10");
        info.setUseTime("1인 60,000원");
        info.setSponsor1("국가유산청");
        info.setSponsor2("국가유산진흥원");
        info.setSponsor1Tel("02-1234-5678");
        info.setSponsor2Tel("02-9876-5432");
        info.setContactTel("1522-2295");
        info.setHomepageUrl("https://www.example.com/festival");
        info.setSourceType("KTO_TOURAPI");
        info.setExternalContentId("2648460");
        return info;
    }

    private InfoImage mainImage() {
        InfoImage image = new InfoImage();
        image.setImageUrl("/uploads/travel-info/festivals/local.jpg");
        image.setSourceType("KTO_TOURAPI");
        image.setSourceName("한국관광공사");
        image.setSourceTitle("경복궁 별빛야행");
        image.setLicenseType("KOGL_TYPE_3");
        image.setIsMain(true);
        image.setOrderIndex(1);
        image.setInfoId(41L);
        return image;
    }

    private FestivalInfoTranslation korean(Long id) {
        FestivalInfoTranslation translation = new FestivalInfoTranslation();
        translation.setId(id);
        translation.setInfoId(41L);
        translation.setLanguageCode("ko");
        translation.setEventPlace("경복궁");
        translation.setAddress("서울특별시 종로구 사직로 161");
        translation.setPlayTime("1부 18:20~20:10");
        translation.setUseTime("1인 60,000원");
        translation.setSponsor1("국가유산청");
        translation.setSponsor2("국가유산진흥원");
        return translation;
    }

    private FestivalInfoTranslation english(Long id) {
        FestivalInfoTranslation translation = new FestivalInfoTranslation();
        translation.setId(id);
        translation.setInfoId(41L);
        translation.setLanguageCode("en");
        translation.setEventPlace("Gyeongbokgung Palace");
        translation.setAddress("161 Sajik-ro, Jongno-gu, Seoul");
        translation.setPlayTime("Part 1 18:20-20:10");
        translation.setUseTime("KRW 60,000 per person");
        translation.setSponsor1("Korea Heritage Service");
        translation.setSponsor2("Korea Heritage Agency");
        return translation;
    }
}
