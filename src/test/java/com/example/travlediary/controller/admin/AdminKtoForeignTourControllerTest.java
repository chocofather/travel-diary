package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.kto.KtoForeignTourCandidateResponse;
import com.example.travlediary.dto.kto.KtoForeignTourDetailResponse;
import com.example.travlediary.dto.kto.KtoForeignTourMatchResponse;
import com.example.travlediary.repository.user.UserMapper;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @ParameterizedTest
    @CsvSource({
            "en, ENGLISH", "ja, JAPANESE", "zh-CN, CHINESE_SIMPLIFIED", "zh-TW, CHINESE_TRADITIONAL"
    })
    void eachSupportedLanguageIsMatchedWithItsOwnService(String tag, KtoForeignLanguage language)
            throws Exception {
        KtoForeignTourCandidateResponse candidate = new KtoForeignTourCandidateResponse(
                "foreign-1", "78", "国立現代美術館 ソウル", "126.98", "37.5786", 12.0);
        when(ktoForeignTourService.match(language, "국립현대미술관 서울", "126.98", "37.5786"))
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
        verify(ktoForeignTourService).match(language, "국립현대미술관 서울", "126.98", "37.5786");
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
                        "月曜日", "10:00～18:00", null, null, null, null));

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

        verify(ktoForeignTourService, never()).match(any(), any(), any(), any());
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

        verify(ktoForeignTourService, never()).match(any(), any(), any(), any());
        verify(ktoForeignTourService, never()).getDetail(any(), any(), any());
    }

    /** 한 언어의 실패는 그 언어 응답으로만 드러나고, 다른 언어 요청과 섞이지 않는다. */
    @Test
    void oneLanguageFailingDoesNotChangeAnotherLanguageResponse() throws Exception {
        when(ktoForeignTourService.match(
                KtoForeignLanguage.CHINESE_SIMPLIFIED, "경복궁", "126.991", "37.579"))
                .thenThrow(KtoForeignTourApiException.upstreamFailure(
                        KtoForeignLanguage.CHINESE_SIMPLIFIED));
        when(ktoForeignTourService.match(
                KtoForeignLanguage.JAPANESE, "경복궁", "126.991", "37.579"))
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
                KtoForeignLanguage.JAPANESE, "경복궁", "126.991", "37.579"))
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
                KtoForeignLanguage.CHINESE_TRADITIONAL, "가고파식당", "126.991", "37.579"))
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
