package com.example.travlediary.controller.admin;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.EventForm;
import com.example.travlediary.model.Event;
import com.example.travlediary.model.EventType;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.event.EventService;
import com.example.travlediary.service.event.EventValidationException;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminEventController.class)
@Import(SecurityConfig.class)
class AdminEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;
    @MockitoBean
    private FileUploadService fileUploadService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void adminCanOpenCreateAndEditUsingTheSameFormView() throws Exception {
        Event existing = existingEvent();
        when(eventService.getAdminEvent(10L)).thenReturn(existing);

        mockMvc.perform(get("/admin/event/new").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/event/event-form"))
                .andExpect(model().attribute("editMode", false))
                .andExpect(model().attribute("formAction", "/admin/event"));

        mockMvc.perform(get("/admin/event/10/edit").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/event/event-form"))
                .andExpect(model().attribute("editMode", true))
                .andExpect(model().attribute("formAction", "/admin/event/10/edit"))
                .andExpect(model().attribute("currentEventImage", "/uploads/events/old.jpg"))
                .andExpect(model().attribute("currentPosterImage", "/uploads/events/posters/old.jpg"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("기존 이벤트")));
    }

    @Test
    void adminCanCreateStandardEventWithoutDescriptionOrAnyImage() throws Exception {
        mockMvc.perform(multipart("/admin/event")
                        .with(user(admin())).with(csrf())
                        .param("title", "새 이벤트")
                        .param("eventType", "STANDARD")
                        .param("startYear", "2026")
                        .param("startMonth", "08")
                        .param("startDay", "01")
                        .param("endYear", "2026")
                        .param("endMonth", "08")
                        .param("endDay", "31"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/event/list"));

        org.mockito.ArgumentCaptor<EventForm> captor =
                org.mockito.ArgumentCaptor.forClass(EventForm.class);
        verify(eventService).create(captor.capture(), eq(7L));
        assertThat(captor.getValue().getDescription()).isNull();
        assertThat(captor.getValue().getEventType()).isEqualTo(EventType.STANDARD);
        assertThat(captor.getValue().getImageFile()).isNull();
        assertThat(captor.getValue().getPosterFile()).isNull();
        assertThat(captor.getValue().getStartYear()).isEqualTo("2026");
        assertThat(captor.getValue().getStartMonth()).isEqualTo("08");
        assertThat(captor.getValue().getStartDay()).isEqualTo("01");
    }

    @Test
    void adminCanUpdateWithoutUploadingAnImageAgain() throws Exception {
        mockMvc.perform(multipart("/admin/event/10/edit")
                        .with(user(admin())).with(csrf())
                        .param("title", "수정 이벤트")
                        .param("description", "수정 설명")
                        .param("startYear", "2026")
                        .param("startMonth", "08")
                        .param("startDay", "01")
                        .param("endYear", "2026")
                        .param("endMonth", "08")
                        .param("endDay", "31"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/event/list"));

        verify(eventService).update(eq(10L), any(EventForm.class));
    }

    @Test
    void invalidUpdateReturnsSharedFormWithFieldError() throws Exception {
        Event existing = existingEvent();
        when(eventService.getAdminEvent(10L)).thenReturn(existing);
        doThrow(new EventValidationException("endDate", "종료일은 시작일보다 빠를 수 없습니다."))
                .when(eventService).update(eq(10L), any(EventForm.class));

        MvcResult result = mockMvc.perform(multipart("/admin/event/10/edit")
                        .with(user(admin())).with(csrf())
                        .param("title", "수정 이벤트")
                        .param("description", "수정 설명")
                        .param("startYear", "2026")
                        .param("startMonth", "02")
                        .param("startDay", "31")
                        .param("endYear", "2026")
                        .param("endMonth", "08")
                        .param("endDay", "01"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/event/event-form"))
                .andExpect(model().attributeHasFieldErrors("eventForm", "endDate"))
                .andExpect(model().attribute("formAction", "/admin/event/10/edit"))
                .andReturn();

        assertThat(org.jsoup.Jsoup.parse(result.getResponse().getContentAsString())
                .selectFirst("#start-day").val()).isEqualTo("31");
    }

    @Test
    void editFormRendersExistingDateAsFourTwoTwoParts() throws Exception {
        when(eventService.getAdminEvent(10L)).thenReturn(existingEvent());

        MvcResult result = mockMvc.perform(get("/admin/event/10/edit").with(user(admin())))
                .andExpect(status().isOk())
                .andReturn();

        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(
                result.getResponse().getContentAsString());
        assertThat(document.selectFirst("#start-year").val()).isEqualTo("2026");
        assertThat(document.selectFirst("#start-month").val()).isEqualTo("08");
        assertThat(document.selectFirst("#start-day").val()).isEqualTo("01");
        assertThat(document.selectFirst("#end-year").val()).isEqualTo("2026");
        assertThat(document.selectFirst("#end-month").val()).isEqualTo("08");
        assertThat(document.selectFirst("#end-day").val()).isEqualTo("31");
        assertThat(document.selectFirst("input[name=eventType][value=STANDARD]")
                .hasAttr("checked")).isTrue();
    }

    @Test
    void standardEditFormShowsMainImageAndContentButHidesInfographicUpload() throws Exception {
        when(eventService.getAdminEvent(10L)).thenReturn(existingEvent());

        org.jsoup.nodes.Document document = renderEditForm(10L);

        assertThat(document.selectFirst("[data-event-panel=description]").hasAttr("hidden")).isFalse();
        assertThat(document.selectFirst("[data-event-panel=mainImage]").hasAttr("hidden")).isFalse();
        assertThat(document.selectFirst("[data-event-panel=poster]").hasAttr("hidden")).isTrue();
        assertThat(document.selectFirst("#event-image-preview").attr("src"))
                .isEqualTo("/uploads/events/old.jpg");
        assertThat(document.selectFirst("#event-image").attr("data-has-existing")).isEqualTo("true");
    }

    @Test
    void infographicEditFormShowsPosterUploadAndHidesContentAndMainImage() throws Exception {
        Event infographic = existingEvent();
        infographic.setEventType(EventType.INFOGRAPHIC);
        infographic.setEventImg(null);
        when(eventService.getAdminEvent(10L)).thenReturn(infographic);

        org.jsoup.nodes.Document document = renderEditForm(10L);

        assertThat(document.selectFirst("input[name=eventType][value=INFOGRAPHIC]")
                .hasAttr("checked")).isTrue();
        assertThat(document.selectFirst("[data-event-panel=poster]").hasAttr("hidden")).isFalse();
        assertThat(document.selectFirst("[data-event-panel=description]").hasAttr("hidden")).isTrue();
        assertThat(document.selectFirst("[data-event-panel=mainImage]").hasAttr("hidden")).isTrue();
        assertThat(document.selectFirst("#event-poster-preview").attr("src"))
                .isEqualTo("/uploads/events/posters/old.jpg");
        assertThat(document.selectFirst("#event-poster").attr("data-has-existing")).isEqualTo("true");
    }

    @Test
    void newFormNeverMarksImageUploadsAsRequiredInHtml() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/event/new").with(user(admin())))
                .andExpect(status().isOk())
                .andReturn();

        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(
                result.getResponse().getContentAsString());
        assertThat(document.selectFirst("#event-image").hasAttr("required")).isFalse();
        assertThat(document.selectFirst("#event-poster").hasAttr("required")).isFalse();
        assertThat(document.selectFirst("#event-description").hasAttr("required")).isFalse();
        assertThat(document.selectFirst("#event-poster").attr("data-has-existing")).isEqualTo("false");
        assertThat(document.selectFirst("#event-image").attr("data-has-existing")).isEqualTo("false");
    }

    private org.jsoup.nodes.Document renderEditForm(long id) throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/event/" + id + "/edit").with(user(admin())))
                .andExpect(status().isOk())
                .andReturn();
        return org.jsoup.Jsoup.parse(result.getResponse().getContentAsString());
    }

    @Test
    void missingEditEventReturnsNotFound() throws Exception {
        when(eventService.getAdminEvent(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/admin/event/99/edit").with(user(admin())))
                .andExpect(status().isNotFound());
    }

    @Test
    void regularUserCannotAccessEventEditing() throws Exception {
        mockMvc.perform(get("/admin/event/10/edit")
                        .with(user("member").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void existingDeletePostStillCallsDeleteService() throws Exception {
        mockMvc.perform(post("/admin/event/10/delete")
                        .with(user(admin())).with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(eventService).deleteEventById(10L);
    }

    @Test
    void adminListRendersDateStatusSlideStateAndImageFallback() throws Exception {
        LocalDate today = LocalDate.now();
        Event ongoing = existingEvent();
        ongoing.setStartDate(today.minusDays(1));
        ongoing.setEndDate(today.plusDays(1));
        ongoing.setSlide(true);

        Event upcoming = existingEvent();
        upcoming.setId(11L);
        upcoming.setTitle("예정 이벤트");
        upcoming.setStartDate(today.plusDays(2));
        upcoming.setEndDate(today.plusDays(3));
        upcoming.setSlide(false);
        upcoming.setEventType(EventType.INFOGRAPHIC);
        upcoming.setEventImg(null);
        upcoming.setPosterImg("/uploads/events/posters/upcoming.jpg");

        Event ended = existingEvent();
        ended.setId(12L);
        ended.setTitle("종료 이벤트");
        ended.setStartDate(today.minusDays(3));
        ended.setEndDate(today.minusDays(2));
        when(eventService.selectAllEvents()).thenReturn(List.of(ongoing, upcoming, ended));

        mockMvc.perform(get("/admin/event/list").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("진행중")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("진행예정")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("종료")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("노출")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("미노출")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/uploads/events/posters/upcoming.jpg")));
    }

    private CustomUserDetails admin() {
        User user = new User();
        user.setId(7L);
        user.setUsername("admin");
        user.setUserPassword("password");
        user.setUserRole(UserRole.ADMIN);
        return new CustomUserDetails(user);
    }

    private Event existingEvent() {
        Event event = new Event();
        event.setId(10L);
        event.setTitle("기존 이벤트");
        event.setDescription("기존 설명");
        event.setEventImg("/uploads/events/old.jpg");
        event.setPosterImg("/uploads/events/posters/old.jpg");
        event.setSlide(false);
        event.setEventType(EventType.STANDARD);
        event.setStartDate(LocalDate.of(2026, 8, 1));
        event.setEndDate(LocalDate.of(2026, 8, 31));
        return event;
    }
}
