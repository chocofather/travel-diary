package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.kto.KtoForeignTourCandidateResponse;
import com.example.travlediary.dto.kto.KtoForeignTourDetailResponse;
import com.example.travlediary.dto.kto.KtoForeignTourMatchResponse;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.kto.KtoFestivalCoordinates;
import com.example.travlediary.service.kto.KtoFestivalService;
import com.example.travlediary.service.kto.KtoForeignLanguage;
import com.example.travlediary.service.kto.KtoForeignTourApiException;
import com.example.travlediary.service.kto.KtoForeignTourService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 외국어 자동입력 API.
 *
 * <p>언어마다 경로를 두지 않고 language 파라미터로 고르며, 화면 번역 탭이 쓰는 네 코드만 받는다.
 */
@WebMvcTest(AdminKtoForeignTourController.class)
@Import(SecurityConfig.class)
class AdminKtoForeignTourControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private KtoForeignTourService ktoForeignTourService;
    @MockitoBean
    private KtoFestivalService ktoFestivalService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    /** 축제·행사 화면만 festival=true 로 부른다. 그 외에는 예전 그대로 꺼진 채 나간다. */
    @ParameterizedTest
    @CsvSource({"true, true", "TRUE, true", "false, false", "yes, false", ", false"})
    void festivalLookupIsOnlyRequestedWhenTheScreenAsksForIt(String parameter, boolean expected)
            throws Exception {
        KtoForeignTourCandidateResponse candidate = new KtoForeignTourCandidateResponse(
                "3544280", "85", "100 Years Night Night Market (백년나이트 야시장)",
                "127.0259", "37.6417", 4.0);
        when(ktoForeignTourService.match(KtoForeignLanguage.ENGLISH, "백년나이트 야시장",
                "127.0259", "37.6417", expected))
                .thenReturn(KtoForeignTourMatchResponse.matched(candidate));

        var request = get("/admin/api/kto/tour/foreign-match")
                .param("language", "en")
                .param("title", "백년나이트 야시장")
                .param("mapX", "127.0259").param("mapY", "37.6417")
                .with(user("admin").roles("ADMIN"));
        if (parameter != null) {
            request = request.param("festival", parameter);
        }

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched.contentTypeId").value("85"));
        verify(ktoForeignTourService).match(KtoForeignLanguage.ENGLISH, "백년나이트 야시장",
                "127.0259", "37.6417", expected);
    }

    // ─── 축제 좌표 복구 (festival_info 에 좌표를 두지 않는 대신 국문 contentId 로 그때그때 구한다) ───

    @Test
    void directCoordinatesAreUsedAsIsWithoutAskingTheKoreanApi() throws Exception {
        when(ktoForeignTourService.match(KtoForeignLanguage.ENGLISH, "백년나이트 야시장",
                "127.0259", "37.6417", true))
                .thenReturn(KtoForeignTourMatchResponse.noMatch());

        mockMvc.perform(get("/admin/api/kto/tour/foreign-match")
                        .param("language", "en").param("title", "백년나이트 야시장")
                        .param("mapX", "127.0259").param("mapY", "37.6417")
                        .param("festival", "true")
                        .param("koreanContentId", "3544280")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(ktoForeignTourService).match(KtoForeignLanguage.ENGLISH, "백년나이트 야시장",
                "127.0259", "37.6417", true);
        // 좌표가 이미 있으면 국문 조회를 하지 않는다.
        verify(ktoFestivalService, never()).getCoordinates(any());
    }

    /** 신규 등록은 국문 검색에서 고른 contentId, 수정 화면은 external_content_id 를 같은 칸에 넣는다. */
    @ParameterizedTest
    @CsvSource({"3544280", "2648460"})
    void koreanContentIdRecoversTheCoordinatesForFestivalMatching(String koreanContentId)
            throws Exception {
        when(ktoFestivalService.getCoordinates(koreanContentId))
                .thenReturn(Optional.of(new KtoFestivalCoordinates("127.0259140093",
                        "37.6417208880")));
        KtoForeignTourCandidateResponse candidate = new KtoForeignTourCandidateResponse(
                "eng-85", "85", "100 Years Night Night Market (백년나이트 야시장)",
                "127.0259140093", "37.6417208880", 0.0);
        when(ktoForeignTourService.match(KtoForeignLanguage.ENGLISH, "백년나이트 야시장",
                "127.0259140093", "37.6417208880", true))
                .thenReturn(KtoForeignTourMatchResponse.matched(candidate));

        mockMvc.perform(get("/admin/api/kto/tour/foreign-match")
                        .param("language", "en").param("title", "백년나이트 야시장")
                        .param("festival", "true")
                        .param("koreanContentId", koreanContentId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                // 외국어 contentId 는 매칭 결과가 알려 준다. 국문 contentId 를 그대로 쓰지 않는다.
                .andExpect(jsonPath("$.matched.contentId").value("eng-85"))
                .andExpect(jsonPath("$.matched.contentTypeId").value("85"));

        verify(ktoFestivalService).getCoordinates(koreanContentId);
        verify(ktoForeignTourService).match(KtoForeignLanguage.ENGLISH, "백년나이트 야시장",
                "127.0259140093", "37.6417208880", true);
    }

    @Test
    void theKoreanContentIdNeverReachesTheForeignDetailCall() throws Exception {
        when(ktoForeignTourService.getDetail(KtoForeignLanguage.ENGLISH, "eng-85", "85"))
                .thenReturn(new KtoForeignTourDetailResponse(
                        "Festival", "Overview", null, null, null, null, null, null,
                        "Place", "Address", null, null, null, null));

        mockMvc.perform(get("/admin/api/kto/tour/foreign-detail")
                        .param("language", "en")
                        .param("contentId", "eng-85")
                        .param("contentTypeId", "85")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        // 상세는 외국어 contentId 로만 부른다. 국문 contentId(3544280)는 절대 넘어가지 않는다.
        verify(ktoForeignTourService).getDetail(KtoForeignLanguage.ENGLISH, "eng-85", "85");
        verify(ktoForeignTourService, never())
                .getDetail(any(), org.mockito.ArgumentMatchers.eq("3544280"), any());
        verifyNoInteractions(ktoFestivalService);
    }

    @ParameterizedTest
    @CsvSource({"FAILED_LOOKUP", "NO_COORDINATES"})
    void festivalsWhoseCoordinatesCannotBeRecoveredAnswerNoMatch(String kind) throws Exception {
        // 국문 조회 실패도, 좌표 없는 응답도 모두 빈 Optional 로 돌아온다.
        when(ktoFestivalService.getCoordinates("3544280")).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/api/kto/tour/foreign-match")
                        .param("language", "en").param("title", "백년나이트 야시장")
                        .param("festival", "true")
                        .param("koreanContentId", "3544280")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_MATCH"))
                .andExpect(jsonPath("$.matched").doesNotExist());

        verify(ktoForeignTourService, never()).match(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void manuallyRegisteredFestivalsWithoutAnyKtoIdAnswerNoMatchInsteadOfFailing()
            throws Exception {
        // source_type=ADMIN 축제는 좌표도 국문 contentId 도 없다. 화면을 깨지 않는다.
        mockMvc.perform(get("/admin/api/kto/tour/foreign-match")
                        .param("language", "en").param("title", "마을 봄꽃 축제")
                        .param("festival", "true")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_MATCH"));

        verifyNoInteractions(ktoFestivalService);
        verify(ktoForeignTourService, never()).match(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void destinationsStillGetABadRequestWhenTheirCoordinatesAreMissingOrWrong()
            throws Exception {
        for (String[] coordinates : new String[][]{
                {"", ""}, {"abc", "37.579"}, {"126.991", "999"}}) {
            mockMvc.perform(get("/admin/api/kto/tour/foreign-match")
                            .param("language", "en").param("title", "경복궁")
                            .param("mapX", coordinates[0]).param("mapY", coordinates[1])
                            // 축제가 아니면 국문 contentId 가 있어도 좌표를 복구하지 않는다.
                            .param("koreanContentId", "3544280")
                            .with(user("admin").roles("ADMIN")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("좌표가 올바르지 않습니다."));
        }
        verifyNoInteractions(ktoFestivalService);
        verify(ktoForeignTourService, never()).match(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void festivalDetailCarriesTheEventFieldsAndLeavesDestinationFieldsEmpty() throws Exception {
        when(ktoForeignTourService.getDetail(KtoForeignLanguage.ENGLISH, "3544280", "85"))
                .thenReturn(new KtoForeignTourDetailResponse(
                        "100 Years Night Night Market",
                        "A traditional market turns into a night market.",
                        null, null, null, null, null, null,
                        "Gangbuk-gu 100 Years Market",
                        "16 Hancheon-ro 144-gil, Gangbuk-gu, Seoul",
                        "16:00-21:00", "Free",
                        "Ministry of SMEs and Startups",
                        "100 Years Market Promotion Organization"));

        mockMvc.perform(get("/admin/api/kto/tour/foreign-detail")
                        .param("language", "en")
                        .param("contentId", "3544280")
                        .param("contentTypeId", "85")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("100 Years Night Night Market"))
                .andExpect(jsonPath("$.eventPlace").value("Gangbuk-gu 100 Years Market"))
                .andExpect(jsonPath("$.address")
                        .value("16 Hancheon-ro 144-gil, Gangbuk-gu, Seoul"))
                .andExpect(jsonPath("$.playTime").value("16:00-21:00"))
                .andExpect(jsonPath("$.useTime").value("Free"))
                .andExpect(jsonPath("$.sponsor1").value("Ministry of SMEs and Startups"))
                .andExpect(jsonPath("$.sponsor2")
                        .value("100 Years Market Promotion Organization"))
                .andExpect(jsonPath("$.closedDays").doesNotExist())
                .andExpect(jsonPath("$.mainMenu").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({
            "en, ENGLISH", "ja, JAPANESE", "zh-CN, CHINESE_SIMPLIFIED", "zh-TW, CHINESE_TRADITIONAL"
    })
    void eachSupportedLanguageIsMatchedWithItsOwnService(String tag, KtoForeignLanguage language)
            throws Exception {
        KtoForeignTourCandidateResponse candidate = new KtoForeignTourCandidateResponse(
                "foreign-1", "78", "国立現代美術館 ソウル", "126.98", "37.5786", 12.0);
        when(ktoForeignTourService.match(language, "국립현대미술관 서울", "126.98", "37.5786", false))
                .thenReturn(KtoForeignTourMatchResponse.matched(candidate));

        mockMvc.perform(get("/admin/api/kto/tour/foreign-match")
                        .param("language", tag)
                        .param("title", "국립현대미술관 서울")
                        .param("mapX", "126.98").param("mapY", "37.5786")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.matched.contentId").value("foreign-1"))
                // 매칭 결과의 외국어 유형 코드를 그대로 돌려준다
                .andExpect(jsonPath("$.matched.contentTypeId").value("78"));
        verify(ktoForeignTourService).match(language, "국립현대미술관 서울", "126.98", "37.5786", false);
    }

    @ParameterizedTest
    @CsvSource({
            "en, ENGLISH", "ja, JAPANESE", "zh-CN, CHINESE_SIMPLIFIED", "zh-TW, CHINESE_TRADITIONAL"
    })
    void detailPassesTheMatchedContentTypeIdThrough(String tag, KtoForeignLanguage language)
            throws Exception {
        when(ktoForeignTourService.getDetail(language, "foreign-1", "78"))
                .thenReturn(new KtoForeignTourDetailResponse(
                        "国立現代美術館 ソウル", "美術館です。",
                        "月曜日", "10:00～18:00", null, null, null, null,
                        null, null, null, null, null, null));

        mockMvc.perform(get("/admin/api/kto/tour/foreign-detail")
                        .param("language", tag)
                        .param("contentId", "foreign-1")
                        .param("contentTypeId", "78")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("国立現代美術館 ソウル"))
                .andExpect(jsonPath("$.overview").value("美術館です。"));
        verify(ktoForeignTourService).getDetail(language, "foreign-1", "78");
    }

    /** 지원 언어가 아니면 다른 언어로 대신하지 않고 거부한다. */
    @ParameterizedTest
    @ValueSource(strings = {"ko", "zh", "en-US", "EN", "  ", "japanese"})
    void unsupportedLanguagesAreRejectedWithoutCallingTheApi(String tag) throws Exception {
        mockMvc.perform(get("/admin/api/kto/tour/foreign-match")
                        .param("language", tag)
                        .param("title", "경복궁")
                        .param("mapX", "126.991").param("mapY", "37.579")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지원하지 않는 언어입니다."));
        mockMvc.perform(get("/admin/api/kto/tour/foreign-detail")
                        .param("language", tag)
                        .param("contentId", "foreign-1")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지원하지 않는 언어입니다."));

        verify(ktoForeignTourService, never()).match(any(), any(), any(), any(), anyBoolean());
        verify(ktoForeignTourService, never()).getDetail(any(), any(), any());
    }

    @Test
    void missingLanguageOrBadRequestValuesNeverReachTheApi() throws Exception {
        mockMvc.perform(get("/admin/api/kto/tour/foreign-match")
                        .param("title", "경복궁")
                        .param("mapX", "126.991").param("mapY", "37.579")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지원하지 않는 언어입니다."));
        mockMvc.perform(get("/admin/api/kto/tour/foreign-match")
                        .param("language", "ja")
                        .param("title", "경복궁")
                        .param("mapX", "999").param("mapY", "37.579")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("좌표가 올바르지 않습니다."));
        mockMvc.perform(get("/admin/api/kto/tour/foreign-detail")
                        .param("language", "ja")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("외국어 관광정보 식별값이 올바르지 않습니다."));

        verify(ktoForeignTourService, never()).match(any(), any(), any(), any(), anyBoolean());
        verify(ktoForeignTourService, never()).getDetail(any(), any(), any());
    }

    /** 한 언어의 실패는 그 언어 응답으로만 드러나고, 다른 언어 요청과 섞이지 않는다. */
    @Test
    void oneLanguageFailingDoesNotChangeAnotherLanguageResponse() throws Exception {
        when(ktoForeignTourService.match(
                KtoForeignLanguage.CHINESE_SIMPLIFIED, "경복궁", "126.991", "37.579", false))
                .thenThrow(KtoForeignTourApiException.upstreamFailure(
                        KtoForeignLanguage.CHINESE_SIMPLIFIED));
        when(ktoForeignTourService.match(
                KtoForeignLanguage.JAPANESE, "경복궁", "126.991", "37.579", false))
                .thenReturn(KtoForeignTourMatchResponse.matched(new KtoForeignTourCandidateResponse(
                        "jpn-1", "76", "景福宮", "126.991", "37.579", 3.0)));

        mockMvc.perform(get("/admin/api/kto/tour/foreign-match")
                        .param("language", "zh-CN").param("title", "경복궁")
                        .param("mapX", "126.991").param("mapY", "37.579")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("중문 간체 관광정보를 불러오지 못했습니다."));

        mockMvc.perform(get("/admin/api/kto/tour/foreign-match")
                        .param("language", "ja").param("title", "경복궁")
                        .param("mapX", "126.991").param("mapY", "37.579")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched.contentId").value("jpn-1"));
    }

    @Test
    void configurationFailuresAreReportedWithoutLeakingInternals() throws Exception {
        when(ktoForeignTourService.match(
                KtoForeignLanguage.JAPANESE, "경복궁", "126.991", "37.579", false))
                .thenThrow(KtoForeignTourApiException.missingApiKey(KtoForeignLanguage.JAPANESE));

        String body = mockMvc.perform(get("/admin/api/kto/tour/foreign-match")
                        .param("language", "ja").param("title", "경복궁")
                        .param("mapX", "126.991").param("mapY", "37.579")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("일문 TourAPI 인증키가 설정되지 않았습니다."))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(
                "org.springframework", "com.example.travlediary", "stackTrace", "serviceKey");
    }

    @Test
    void noMatchIsAnOrdinaryAnswerRatherThanAnError() throws Exception {
        when(ktoForeignTourService.match(
                KtoForeignLanguage.CHINESE_TRADITIONAL, "가고파식당", "126.991", "37.579", false))
                .thenReturn(KtoForeignTourMatchResponse.noMatch());

        mockMvc.perform(get("/admin/api/kto/tour/foreign-match")
                        .param("language", "zh-TW").param("title", "가고파식당")
                        .param("mapX", "126.991").param("mapY", "37.579")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_MATCH"))
                .andExpect(jsonPath("$.matched").doesNotExist())
                .andExpect(jsonPath("$.candidates").isEmpty());
    }
}
