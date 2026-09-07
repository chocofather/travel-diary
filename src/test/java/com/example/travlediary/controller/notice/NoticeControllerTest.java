package com.example.travlediary.controller.notice;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.config.i18n.TravelDiaryLocaleResolver;
import com.example.travlediary.dto.NoticeDetailDto;
import com.example.travlediary.dto.NoticeListItemDto;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.notice.NoticeService;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(NoticeController.class)
@Import(SecurityConfig.class)
class NoticeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoticeService noticeService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void guestCanOpenNoticeListWithPinnedRowsAndPagination() throws Exception {
        NoticeListItemDto notice = listItem(10L, true);
        NoticeListItemDto regularNotice = listItem(11L, false);
        regularNotice.setTitle("일반 업데이트 안내");
        when(noticeService.countPublicList()).thenReturn(21L);
        when(noticeService.getPublicList(10L, 10)).thenReturn(List.of(notice, regularNotice));

        mockMvc.perform(get("/support/notices").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("support/notices/list"))
                .andExpect(model().attribute("currentPage", 2))
                .andExpect(model().attribute("pageSize", 10))
                .andExpect(model().attribute("totalPages", 3))
                .andExpect(model().attribute("totalCount", 21L))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("상단 고정 공지")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">공지</span>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/support/notices/10")))
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    var rows = document.select(".support-notice-item");
                    assertThat(rows).hasSize(2);
                    assertThat(rows.get(0).select(".support-notice-pin")).hasSize(1);
                    assertThat(rows.get(0).select("img.support-notice-pin-icon[src=/images/pin.svg]")).hasSize(1);
                    assertThat(rows.get(1).select(".support-notice-pin")).hasSize(1);
                    assertThat(rows.get(1).select("img.support-notice-pin-icon")).isEmpty();
                });

        verify(noticeService).getPublicList(10L, 10);
    }

    @Test
    void invalidOrOutOfRangePageIsNormalizedWithoutServerError() throws Exception {
        when(noticeService.countPublicList()).thenReturn(11L);
        when(noticeService.getPublicList(0L, 10)).thenReturn(List.of());
        when(noticeService.getPublicList(10L, 10)).thenReturn(List.of());

        mockMvc.perform(get("/support/notices").param("page", "not-a-number"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentPage", 1));
        mockMvc.perform(get("/support/notices").param("page", "999999"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentPage", 2));
    }

    @Test
    void guestCanOpenNumericDetailWithSanitizedRichText() throws Exception {
        NoticeDetailDto detail = detail();
        when(noticeService.getPublicDetail(10L)).thenReturn(detail);

        mockMvc.perform(get("/support/notices/10"))
                .andExpect(status().isOk())
                .andExpect(view().name("support/notices/detail"))
                .andExpect(model().attribute("notice", detail))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("서비스 점검 안내")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"support-notice-content rich-text-content\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<strong>점검 본문</strong>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("목록으로")));
    }

    /** 고정 UI 문구는 요청 언어를 따르고, 공지 제목·본문은 서비스가 준 값 그대로다. */
    @Test
    void staticLabelsFollowTheLocaleWhileNoticeContentComesFromTheService() throws Exception {
        when(noticeService.getPublicDetail(10L)).thenReturn(detail());

        var korean = Jsoup.parse(mockMvc.perform(get("/support/notices/10"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(korean.select(".support-notice-detail-actions a").text()).isEqualTo("목록으로");

        var english = Jsoup.parse(mockMvc.perform(get("/support/notices/10")
                        .cookie(localeCookie("en")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(english.select(".support-notice-detail-actions a").text())
                .isEqualTo("Back to list");
        assertThat(english.select(".support-navigation-title").text()).isEqualTo("Support");
        // 공지 제목은 DB 번역(또는 원문)을 그대로 쓴다
        assertThat(english.select("#support-notice-detail-title").text())
                .isEqualTo("서비스 점검 안내");
    }

    /** 공개 화면은 쿠키 locale 로 정한 요청 언어를 서비스에 그대로 넘긴다. (관리자 경로는 대상 아님) */
    @Test
    void requestedLanguageFromTheLocaleCookieReachesTheService() throws Exception {
        NoticeDetailDto detail = detail();
        when(noticeService.getPublicDetail(10L)).thenReturn(detail);
        when(noticeService.getPublicList(0L, 10)).thenReturn(List.of(listItem(10L, true)));
        when(noticeService.countPublicList()).thenReturn(1L);

        mockMvc.perform(get("/support/notices/10").cookie(localeCookie("en")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/support/notices").cookie(localeCookie("ja")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/support/notices/10"))
                .andExpect(status().isOk());

        verify(noticeService).localizePublicDetail(detail, SupportedLanguage.ENGLISH);
        verify(noticeService).localizePublicList(
                org.mockito.ArgumentMatchers.anyList(), eq(SupportedLanguage.JAPANESE));
        // locale 쿠키가 없으면 한국어로 본다
        verify(noticeService).localizePublicDetail(detail, SupportedLanguage.KOREAN);
    }

    @Test
    void missingNumericDetailReturnsApplicationNotFound() throws Exception {
        when(noticeService.getPublicDetail(999L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/support/notices/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonNumericDetailPathIsNotPublic() throws Exception {
        mockMvc.perform(get("/support/notices/abc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?redirect=/support/notices/abc"));
    }

    private jakarta.servlet.http.Cookie localeCookie(String languageTag) {
        return new jakarta.servlet.http.Cookie(
                TravelDiaryLocaleResolver.COOKIE_NAME, languageTag);
    }

    private NoticeListItemDto listItem(Long id, boolean pinned) {
        NoticeListItemDto item = new NoticeListItemDto();
        item.setId(id);
        item.setTitle("상단 고정 공지");
        item.setPinned(pinned);
        item.setViews(12);
        item.setCreatedAt(Timestamp.valueOf("2026-08-12 10:00:00"));
        item.setUpdatedAt(Timestamp.valueOf("2026-08-12 10:00:00"));
        return item;
    }

    private NoticeDetailDto detail() {
        NoticeDetailDto detail = new NoticeDetailDto();
        detail.setId(10L);
        detail.setTitle("서비스 점검 안내");
        detail.setContent("<p><strong>점검 본문</strong></p>");
        detail.setViews(8);
        detail.setCreatedAt(Timestamp.valueOf("2026-08-12 10:00:00"));
        return detail;
    }
}
