package com.example.travlediary.controller.event;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.model.Event;
import com.example.travlediary.model.EventType;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.event.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(EventController.class)
@Import(SecurityConfig.class)
class EventPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void defaultPageRendersOngoingCardAndDetailLinkWithoutAllFilter() throws Exception {
        Event event = event();
        event.setDescription(null);
        event.setPosterImg("/uploads/events/posters/main.jpg");
        when(eventService.getEventsByStatus("ongoing", 0L, 9)).thenReturn(List.of(event));

        MvcResult result = mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(view().name("event/event-list"))
                .andExpect(model().attribute("selectedStatus", "ongoing"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("진행 중 이벤트")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/events/10\"")))
                .andReturn();

        assertThat(org.jsoup.Jsoup.parse(result.getResponse().getContentAsString())
                .select(".event-tab a").eachText())
                .containsExactly("진행중", "진행예정", "종료");
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(
                result.getResponse().getContentAsString());
        assertThat(document.selectFirst(".event-card-image").attr("src"))
                .isEqualTo("/uploads/events/posters/main.jpg");
        assertThat(document.select(".event-desc")).isEmpty();
    }

    @Test
    void emptyUpcomingPageKeepsUpcomingStateAndMessage() throws Exception {
        when(eventService.getEventsByStatus("upcoming", 0L, 9)).thenReturn(List.of());

        mockMvc.perform(get("/events").param("status", "upcoming"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedStatus", "upcoming"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("예정된 이벤트가 없습니다.")));
    }

    @Test
    void invalidStatusUsesOngoingQuery() throws Exception {
        when(eventService.getEventsByStatus("ongoing", 0L, 9)).thenReturn(List.of());

        mockMvc.perform(get("/events").param("status", "all"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedStatus", "ongoing"));

        verify(eventService).getEventsByStatus("ongoing", 0L, 9);
    }

    @Test
    void paginationKeepsTheSelectedStatusTab() throws Exception {
        when(eventService.countEventsByStatus("ended")).thenReturn(10L);
        when(eventService.getEventsByStatus("ended", 0L, 9)).thenReturn(List.of(event()));

        MvcResult result = mockMvc.perform(get("/events").param("status", "ended"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalPages", 2))
                .andReturn();

        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(
                result.getResponse().getContentAsString());
        assertThat(document.select(".pagination .page-number").eachText())
                .containsExactly("1", "2");
        assertThat(document.selectFirst(".pagination .page-number.is-current").text()).isEqualTo("1");
        assertThat(document.select(".pagination a").attr("href"))
                .contains("status=ended");
    }

    @Test
    void standardListCardUsesMainImageThumbnail() throws Exception {
        Event standard = event();
        standard.setEventType(EventType.STANDARD);
        standard.setEventImg("/uploads/events/main.jpg");
        standard.setPosterImg("/uploads/events/posters/unused.jpg");
        when(eventService.getEventsByStatus("ongoing", 0L, 9)).thenReturn(List.of(standard));

        org.jsoup.nodes.Document document = renderList(null);

        assertThat(document.selectFirst(".event-card-image").attr("src"))
                .isEqualTo("/uploads/events/main.jpg");
    }

    @Test
    void standardCardWithoutMainImageRendersTextOnlyCardWithoutBrokenImage() throws Exception {
        Event standard = event();
        standard.setEventType(EventType.STANDARD);
        standard.setEventImg(null);
        when(eventService.getEventsByStatus("ongoing", 0L, 9)).thenReturn(List.of(standard));

        org.jsoup.nodes.Document document = renderList(null);

        assertThat(document.select(".event-card-image-wrap")).isEmpty();
        assertThat(document.select(".event-card-image")).isEmpty();
        assertThat(document.selectFirst(".event-card-link").hasClass("is-text-only")).isTrue();
        assertThat(document.selectFirst(".event-title").text()).isEqualTo("진행 중 이벤트");
    }

    @Test
    void detailUsesPosterLayoutOnlyForInfographicEventsWithPoster() throws Exception {
        Event standard = event();
        standard.setEventType(EventType.STANDARD);
        standard.setEventImg("/uploads/events/main.jpg");
        standard.setPosterImg("/uploads/events/posters/kept.jpg");
        when(eventService.getEventDetail(10L)).thenReturn(standard);

        org.jsoup.nodes.Document document = renderDetail();

        // poster 가 남아 있어도 event_type 이 STANDARD 면 포스터 레이아웃을 쓰지 않는다
        assertThat(document.select(".event-detail-media.is-poster")).isEmpty();
        assertThat(document.selectFirst(".event-detail-media.is-hero img").attr("src"))
                .isEqualTo("/uploads/events/main.jpg");
        assertThat(document.selectFirst(".event-detail-body").text())
                .isEqualTo("여행 이벤트 설명");
    }

    @Test
    void standardDetailWithoutMainImageHidesTheImageArea() throws Exception {
        Event standard = event();
        standard.setEventType(EventType.STANDARD);
        standard.setEventImg(null);
        when(eventService.getEventDetail(10L)).thenReturn(standard);

        org.jsoup.nodes.Document document = renderDetail();

        // 빈 이미지 영역이나 placeholder 없이 텍스트만으로 완성되어야 한다
        assertThat(document.select(".event-detail-media")).isEmpty();
        assertThat(document.selectFirst(".event-detail").hasClass("is-text-only")).isTrue();
        assertThat(document.selectFirst(".event-detail-title").text()).isEqualTo("진행 중 이벤트");
        assertThat(document.selectFirst(".event-detail-body").text())
                .isEqualTo("여행 이벤트 설명");
    }

    @Test
    void infographicDetailRendersPosterWithoutSeparateContentArea() throws Exception {
        Event infographic = event();
        infographic.setEventType(EventType.INFOGRAPHIC);
        infographic.setPosterImg("/uploads/events/posters/main.jpg");
        when(eventService.getEventDetail(10L)).thenReturn(infographic);

        org.jsoup.nodes.Document document = renderDetail();

        assertThat(document.selectFirst(".event-detail-media.is-poster img").attr("src"))
                .isEqualTo("/uploads/events/posters/main.jpg");
        assertThat(document.select(".event-detail-media.is-hero")).isEmpty();
        assertThat(document.select(".event-detail-body")).isEmpty();
        assertThat(document.selectFirst(".event-detail-title").text()).isEqualTo("진행 중 이벤트");
    }

    @Test
    void standardDetailUsesEventHeaderAndIntroSection() throws Exception {
        Event standard = event();
        standard.setEventType(EventType.STANDARD);
        standard.setEventImg("/uploads/events/main.jpg");
        when(eventService.getEventDetail(10L)).thenReturn(standard);

        org.jsoup.nodes.Document document = renderDetail();

        assertThat(document.selectFirst(".event-detail").hasClass("is-standard")).isTrue();
        assertThat(document.selectFirst(".event-detail-section-title").text())
                .isEqualTo("이벤트 소개");
        assertThat(document.selectFirst(".event-detail-section .event-detail-body").text())
                .isEqualTo("여행 이벤트 설명");
    }

    @Test
    void standardDetailWithoutImageKeepsHeaderAndIntroSectionOnly() throws Exception {
        Event standard = event();
        standard.setEventType(EventType.STANDARD);
        standard.setEventImg(null);
        when(eventService.getEventDetail(10L)).thenReturn(standard);

        org.jsoup.nodes.Document document = renderDetail();

        assertThat(document.selectFirst(".event-detail").hasClass("is-standard")).isTrue();
        assertThat(document.select(".event-detail-media")).isEmpty();
        assertThat(document.select(".event-detail-section")).hasSize(1);
    }

    @Test
    void infographicDetailKeepsPosterLayoutWithoutStandardStyling() throws Exception {
        Event infographic = event();
        infographic.setEventType(EventType.INFOGRAPHIC);
        infographic.setPosterImg("/uploads/events/posters/main.jpg");
        when(eventService.getEventDetail(10L)).thenReturn(infographic);

        org.jsoup.nodes.Document document = renderDetail();

        assertThat(document.selectFirst(".event-detail").hasClass("is-standard")).isFalse();
        assertThat(document.select(".event-detail-section")).isEmpty();
        assertThat(document.select(".event-detail-media.is-poster")).hasSize(1);
    }

    @Test
    void legacyRowsWithoutEventTypeStillRenderAsInfographic() throws Exception {
        Event legacy = event();
        legacy.setPosterImg("/uploads/events/posters/legacy.jpg");
        when(eventService.getEventDetail(10L)).thenReturn(legacy);

        org.jsoup.nodes.Document document = renderDetail();

        assertThat(document.selectFirst(".event-detail-media.is-poster img").attr("src"))
                .isEqualTo("/uploads/events/posters/legacy.jpg");
    }

    @Test
    void detailHeaderShowsPeriodStatusAndDurationFromExistingDatesOnly() throws Exception {
        LocalDate today = LocalDate.now();
        Event ongoing = event();
        ongoing.setEventType(EventType.STANDARD);
        ongoing.setStartDate(today.minusDays(2));
        ongoing.setEndDate(today.plusDays(3));
        when(eventService.getEventDetail(10L)).thenReturn(ongoing);

        org.jsoup.nodes.Document document = renderDetail();

        assertThat(document.selectFirst(".event-detail-status").text()).isEqualTo("진행중");
        assertThat(document.selectFirst(".event-detail-status").hasClass("is-ongoing")).isTrue();
        assertThat(document.selectFirst(".event-detail-period").text())
                .isEqualTo(today.minusDays(2).toString().replace('-', '.')
                        + " ~ " + today.plusDays(3).toString().replace('-', '.'));
        assertThat(document.select(".event-detail-meta li").eachText())
                .contains("총 6일간", "종료까지 D-3");
    }

    @Test
    void upcomingAndEndedDetailHeadersUseTheirOwnStatusLabels() throws Exception {
        LocalDate today = LocalDate.now();
        Event upcoming = event();
        upcoming.setStartDate(today.plusDays(4));
        upcoming.setEndDate(today.plusDays(10));
        upcoming.setPosterImg("/uploads/events/posters/main.jpg");
        when(eventService.getEventDetail(10L)).thenReturn(upcoming);

        org.jsoup.nodes.Document upcomingDocument = renderDetail();
        assertThat(upcomingDocument.selectFirst(".event-detail-status").text()).isEqualTo("진행예정");
        assertThat(upcomingDocument.select(".event-detail-meta li").eachText())
                .contains("시작까지 D-4");

        Event ended = event();
        ended.setStartDate(today.minusDays(10));
        ended.setEndDate(today.minusDays(4));
        ended.setPosterImg("/uploads/events/posters/main.jpg");
        when(eventService.getEventDetail(11L)).thenReturn(ended);

        org.jsoup.nodes.Document endedDocument = org.jsoup.Jsoup.parse(
                mockMvc.perform(get("/events/11")).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        assertThat(endedDocument.selectFirst(".event-detail-status").text()).isEqualTo("종료");
        assertThat(endedDocument.select(".event-detail-meta li").eachText())
                .noneMatch(text -> text.contains("D-"));
    }

    private org.jsoup.nodes.Document renderDetail() throws Exception {
        MvcResult result = mockMvc.perform(get("/events/10"))
                .andExpect(status().isOk())
                .andReturn();
        return org.jsoup.Jsoup.parse(result.getResponse().getContentAsString());
    }

    private org.jsoup.nodes.Document renderList(String status) throws Exception {
        MvcResult result = mockMvc.perform(status == null
                        ? get("/events")
                        : get("/events").param("status", status))
                .andExpect(status().isOk())
                .andReturn();
        return org.jsoup.Jsoup.parse(result.getResponse().getContentAsString());
    }

    @Test
    void infographicWithoutPosterStillRendersUsableDetailLayout() throws Exception {
        Event infographic = event();
        infographic.setEventType(EventType.INFOGRAPHIC);
        infographic.setPosterImg(null);
        when(eventService.getEventDetail(10L)).thenReturn(infographic);

        org.jsoup.nodes.Document document = renderDetail();

        assertThat(document.select(".event-detail-media")).isEmpty();
        assertThat(document.selectFirst(".event-detail-title").text()).isEqualTo("진행 중 이벤트");
        assertThat(document.selectFirst(".event-detail-body").text()).isEqualTo("여행 이벤트 설명");
    }

    private Event event() {
        Event event = new Event();
        event.setId(10L);
        event.setTitle("진행 중 이벤트");
        event.setDescription("여행 이벤트 설명");
        event.setStartDate(LocalDate.of(2026, 8, 1));
        event.setEndDate(LocalDate.of(2026, 8, 31));
        return event;
    }
}
