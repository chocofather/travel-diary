package com.example.travlediary.controller.travelinfo;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.config.i18n.I18nConfig;
import com.example.travlediary.config.i18n.TravelDiaryLocaleResolver;
import com.example.travlediary.dto.FestivalDetailDto;
import com.example.travlediary.dto.TravelInfoDetailDto;
import com.example.travlediary.dto.TravelInfoPeriodDto;
import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.model.InfoImage;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.category.InfoCategoryService;
import com.example.travlediary.service.category.ReferenceNameLocalizationService;
import com.example.travlediary.service.travelinfo.FestivalDetailService;
import com.example.travlediary.service.travelinfo.TravelInfoService;
import jakarta.servlet.http.Cookie;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공개 상세 두 화면이 언어별로 고정 문구를 그려 내는지 본다.
 *
 * <p>제목·본문·카테고리·행사 상세정보는 이미 DB 에서 언어별로 오는 값이라 그대로 나와야 한다.
 */
@WebMvcTest(TravelInfoController.class)
@Import({SecurityConfig.class, I18nConfig.class})
class TravelInfoDetailLocaleRenderingTest {

    @MockitoBean private TravelInfoService travelInfoService;
    @MockitoBean private FestivalDetailService festivalDetailService;
    @MockitoBean private InfoCategoryService infoCategoryService;
    @MockitoBean private ReferenceNameLocalizationService referenceNameLocalizationService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource({
            "ko, 여행정보, 작성일, 조회수, 목록으로",
            "en, Travel Info, Published, Views, Back to list",
            "ja, 旅行情報, 公開日, 閲覧数, 一覧に戻る",
            "zh-CN, 旅游资讯, 发布日期, 浏览量, 返回列表",
            "zh-TW, 旅遊資訊, 發布日期, 瀏覽量, 返回列表"
    })
    void theGeneralDetailRendersItsLabelsInTheRequestedLanguage(
            String tag, String kicker, String createdAt, String views, String back)
            throws Exception {
        when(festivalDetailService.isPublicFestival(10L)).thenReturn(false);
        when(travelInfoService.getPublicDetail(10L)).thenReturn(generalDetail());

        Document document = render("/travel-info/10", tag);

        assertThat(document.select(".travel-info-detail-kicker").text()).isEqualTo(kicker);
        assertThat(document.select(".travel-info-detail-meta dt").eachText())
                .startsWith(createdAt, views);
        assertThat(document.select(".travel-info-detail-back").text()).isEqualTo(back);
        // 제목·본문·카테고리는 DB 번역 결과 그대로다.
        assertThat(document.select("#travel-info-detail-title").text())
                .isEqualTo("Spring packing list");
        assertThat(document.select(".travel-info-detail-category").text())
                .isEqualTo("Seasonal travel");
        assertThat(document.select(".travel-info-detail-content").text())
                .isEqualTo("English body");
    }

    @ParameterizedTest
    @CsvSource({
            "ko, 행사 소개, 행사 정보, 주최, 주관, 이용요금",
            "en, About the event, Event details, Host, Organizer, Admission",
            "ja, イベント紹介, イベント情報, 主催, 主管, 料金",
            "zh-CN, 活动介绍, 活动信息, 主办, 承办, 费用",
            "zh-TW, 活動介紹, 活動資訊, 主辦, 承辦, 費用"
    })
    void theFestivalDetailRendersItsLabelsInTheRequestedLanguage(
            String tag, String introduction, String information,
            String host, String organizer, String admission) throws Exception {
        when(festivalDetailService.getPublicDetail(41L)).thenReturn(festivalDetail());

        Document document = render("/festivals/41", tag);

        assertThat(document.select("#festival-introduction-title").text())
                .isEqualTo(introduction);
        assertThat(document.select("#festival-information-title").text()).isEqualTo(information);
        assertThat(document.select(".festival-detail-info-row dt").eachText())
                .contains(host, organizer, admission);
    }

    @ParameterizedTest
    @CsvSource({"ko", "en", "ja", "zh-CN", "zh-TW"})
    void festivalValuesFromTheDatabaseAreNeverReplacedByLabels(String tag) throws Exception {
        when(festivalDetailService.getPublicDetail(41L)).thenReturn(festivalDetail());

        Document document = render("/festivals/41", tag);
        List<String> values = document.select(".festival-detail-info-row dd").eachText();

        // localization 이 이미 끝난 동적 값과 번역하지 않는 값이 그대로 있다.
        assertThat(values).anyMatch(value -> value.contains("Gyeongbokgung Palace"));
        assertThat(values).anyMatch(value -> value.contains("161 Sajik-ro, Jongno-gu, Seoul"));
        assertThat(values).anyMatch(value -> value.contains("KRW 60,000 per person"));
        assertThat(values).anyMatch(value -> value.contains("02-1234-5678"));
        assertThat(values).anyMatch(value -> value.contains("2026.09.02"));
        assertThat(document.select(".festival-detail-homepage").attr("href"))
                .isEqualTo("https://www.example.com/festival");
    }

    @ParameterizedTest
    @CsvSource({
            "ko, 사진 출처:, 공공누리 제3유형, 이전 이미지, 이미지 닫기",
            "en, Photo credit:, KOGL Type 3 (Korea Open Government Licence), "
                    + "Previous photo, Close photo",
            "ja, 写真提供:, 公共ヌリ 第3類型（韓国公共著作物ライセンス）, 前の写真, 写真を閉じる",
            "zh-CN, 图片来源：, KOGL 第3类型（韩国公共著作物许可）, 上一张图片, 关闭图片",
            "zh-TW, 圖片來源：, KOGL 第3類型（韓國公共著作物授權）, 上一張圖片, 關閉圖片"
    })
    void theGalleryAttributionAndModalAreTranslated(
            String tag, String credit, String license, String previous, String close)
            throws Exception {
        when(festivalDetailService.getPublicDetail(41L)).thenReturn(festivalDetail());

        Document document = render("/festivals/41", tag);

        assertThat(document.select(".festival-detail-attribution span").first().text())
                .isEqualTo(credit);
        assertThat(document.select("[data-festival-gallery-license]").text()).isEqualTo(license);
        // 슬라이드가 들고 있는 값도 같은 언어다 (스크립트가 이 값을 그대로 보여 준다).
        assertThat(document.select("[data-festival-gallery-slide]").first()
                .attr("data-license-label")).isEqualTo(license);
        assertThat(document.select("[data-festival-modal-prev]").attr("aria-label"))
                .isEqualTo(previous);
        assertThat(document.select("[data-festival-modal-close]").attr("aria-label"))
                .isEqualTo(close);
        // 사진 제공처 이름은 데이터 값이라 그대로다.
        assertThat(document.select("[data-festival-gallery-source]").text())
                .isEqualTo("한국관광공사");
    }

    @Test
    void festivalImageAltUsesMessageParametersRatherThanStringJoining() throws Exception {
        when(festivalDetailService.getPublicDetail(41L)).thenReturn(festivalDetail());

        Document english = render("/festivals/41", "en");
        Document korean = render("/festivals/41", "ko");

        assertThat(english.select(".festival-detail-gallery-slide img").first().attr("alt"))
                .isEqualTo("Starlight Night Tour photo 1");
        assertThat(english.select("[data-festival-gallery-open]").first().attr("aria-label"))
                .isEqualTo("View photo 1 of Starlight Night Tour larger");
        assertThat(korean.select(".festival-detail-gallery-slide img").first().attr("alt"))
                .isEqualTo("Starlight Night Tour 이미지 1");
    }

    @ParameterizedTest
    @CsvSource({
            "ko, Starlight Night Tour | 여행정보, Starlight Night Tour | 축제·행사",
            "en, Starlight Night Tour | Travel Info, Starlight Night Tour | Festivals & Events",
            "ja, Starlight Night Tour | 旅行情報, Starlight Night Tour | 祭り・イベント",
            "zh-CN, Starlight Night Tour | 旅游资讯, Starlight Night Tour | 庆典·活动",
            "zh-TW, Starlight Night Tour | 旅遊資訊, Starlight Night Tour | 慶典·活動"
    })
    void theBrowserTabTitleIsBuiltFromMessagesRatherThanKoreanConcatenation(
            String tag, String generalTitle, String festivalTitle) throws Exception {
        TravelInfoDetailDto general = generalDetail();
        general.setTitle("Starlight Night Tour");
        when(festivalDetailService.isPublicFestival(10L)).thenReturn(false);
        when(travelInfoService.getPublicDetail(10L)).thenReturn(general);
        when(festivalDetailService.getPublicDetail(41L)).thenReturn(festivalDetail());

        assertThat(render("/travel-info/10", tag).title()).isEqualTo(generalTitle);
        assertThat(render("/festivals/41", tag).title()).isEqualTo(festivalTitle);
    }

    @Test
    void bothDetailScreensHandTheBookmarkScriptTranslatedWording() throws Exception {
        when(festivalDetailService.isPublicFestival(10L)).thenReturn(false);
        when(travelInfoService.getPublicDetail(10L)).thenReturn(generalDetail());
        when(festivalDetailService.getPublicDetail(41L)).thenReturn(festivalDetail());

        for (Document document : List.of(
                render("/travel-info/10", "en"), render("/festivals/41", "en"))) {
            var button = document.select("[data-travel-info-bookmark]").first();
            assertThat(button.attr("aria-label")).isEqualTo("Save this travel info");
            assertThat(button.attr("data-label-save")).isEqualTo("Save");
            assertThat(button.attr("data-label-saved")).isEqualTo("Saved");
            assertThat(button.attr("data-aria-remove")).isEqualTo("Remove from saved");
            assertThat(button.attr("data-failed-message"))
                    .isEqualTo("We could not update your saved items.");
        }
    }

    private Document render(String path, String languageTag) throws Exception {
        String html = mockMvc.perform(get(path)
                        .cookie(new Cookie(TravelDiaryLocaleResolver.COOKIE_NAME, languageTag)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Jsoup.parse(html);
    }

    private TravelInfoDetailDto generalDetail() {
        TravelInfoDetailDto detail = new TravelInfoDetailDto();
        detail.setId(10L);
        detail.setTitle("Spring packing list");
        detail.setScope(TravelInfoScope.DOMESTIC);
        detail.setContentType(TravelInfoContentType.GENERAL);
        detail.setCategoryId(3L);
        detail.setCategoryName("Seasonal travel");
        detail.setContent("<p>English body</p>");
        detail.setViews(7);
        detail.setCreatedAt(Timestamp.valueOf("2026-08-10 09:00:00"));
        return detail;
    }

    private FestivalDetailDto festivalDetail() {
        TravelInfoDetailDto detail = new TravelInfoDetailDto();
        detail.setId(41L);
        detail.setTitle("Starlight Night Tour");
        detail.setScope(TravelInfoScope.DOMESTIC);
        detail.setContentType(TravelInfoContentType.FESTIVAL);
        detail.setCategoryId(4L);
        detail.setCategoryName("Culture festival");
        detail.setContent("<p>English body</p>");
        detail.setViews(7);
        detail.setCreatedAt(Timestamp.valueOf("2026-08-01 09:00:00"));
        detail.setPeriods(List.of(new TravelInfoPeriodDto(
                LocalDate.parse("2026-09-02"), LocalDate.parse("2026-10-24"))));

        FestivalInfo festivalInfo = new FestivalInfo();
        festivalInfo.setInfoId(41L);
        festivalInfo.setEventPlace("Gyeongbokgung Palace");
        festivalInfo.setAddress("161 Sajik-ro, Jongno-gu, Seoul");
        festivalInfo.setPlayTime("Part 1 18:20-20:10");
        festivalInfo.setUseTime("KRW 60,000 per person");
        festivalInfo.setSponsor1("Korea Heritage Service");
        festivalInfo.setSponsor1Tel("02-1234-5678");
        festivalInfo.setSponsor2("Korea Heritage Agency");
        festivalInfo.setContactTel("1522-2295");
        festivalInfo.setHomepageUrl("https://www.example.com/festival");

        InfoImage image = new InfoImage();
        image.setImageUrl("/uploads/travel-info/festivals/local.jpg");
        image.setSourceType("KTO_TOURAPI");
        image.setSourceName("한국관광공사");
        image.setSourceTitle("경복궁 별빛야행");
        image.setLicenseType("KOGL_TYPE_3");
        image.setIsMain(true);
        image.setOrderIndex(1);
        image.setInfoId(41L);
        return new FestivalDetailDto(detail, festivalInfo, List.of(image));
    }
}
