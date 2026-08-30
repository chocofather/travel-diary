package com.example.travlediary.controller.diary;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.DiaryCalendarDto;
import com.example.travlediary.dto.DiaryListItemDto;
import com.example.travlediary.dto.DiaryListPageDto;
import com.example.travlediary.model.Diary;
import com.example.travlediary.model.DiaryElement;
import com.example.travlediary.model.DiaryPage;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.dto.DiarySort;
import com.example.travlediary.model.DiaryCover;
import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverDesignElement;
import com.example.travlediary.model.DiaryCoverElement;
import com.example.travlediary.service.diary.DiaryCoverDesignElementService;
import com.example.travlediary.service.diary.DiaryCoverDesignService;
import com.example.travlediary.service.diary.DiaryCoverService;
import com.example.travlediary.service.diary.DiaryElementService;
import com.example.travlediary.service.diary.DiaryLabelFontCatalog;
import com.example.travlediary.service.diary.DiaryPageService;
import com.example.travlediary.service.diary.DiaryService;
import com.example.travlediary.service.diary.DiaryNoteCatalog;
import com.example.travlediary.service.diary.DiaryStickerCatalog;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.holiday.HolidayService;
import com.example.travlediary.service.holiday.SpecialDay;
import com.example.travlediary.service.holiday.SpecialDays;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(DiaryController.class)
@Import({SecurityConfig.class, DiaryStickerCatalog.class, DiaryNoteCatalog.class,
        DiaryLabelFontCatalog.class})
class DiaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiaryService diaryService;
    @MockitoBean
    private DiaryPageService diaryPageService;
    @MockitoBean
    private DiaryElementService diaryElementService;
    @MockitoBean
    private FileUploadService fileUploadService;
    @MockitoBean
    private DiaryCoverService diaryCoverService;
    @MockitoBean
    private DiaryCoverDesignService diaryCoverDesignService;
    @MockitoBean
    private DiaryCoverDesignElementService diaryCoverDesignElementService;
    @MockitoBean
    private HolidayService holidayService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;
    @MockitoBean
    private CustomUserDetails userDetails;

    @Test
    void guestIsSentToLogin() throws Exception {
        mockMvc.perform(get("/diaries"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void listShowsOnlyTheCurrentUsersDiaries() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryPage(7L, null, DiarySort.UPDATED_DESC, 1)).thenReturn(listPage(List.of(item())));

        mockMvc.perform(get("/diaries")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/list"))
                .andExpect(content().string(containsString("나의 여행일기")))
                .andExpect(content().string(containsString("여름 제주 여행")))
                .andExpect(content().string(containsString("3장")));

        verify(diaryService).getMyDiaryPage(7L, null, DiarySort.UPDATED_DESC, 1);
    }

    @Test
    void searchKeywordAndPageComeFromTheQueryString() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryPage(7L, "제주", DiarySort.UPDATED_DESC, 2))
                .thenReturn(new DiaryListPageDto(List.of(item()), "제주", DiarySort.UPDATED_DESC, 2, 3, 30, 12));

        String body = mockMvc.perform(get("/diaries").param("q", "제주").param("page", "2")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        verify(diaryService).getMyDiaryPage(7L, "제주", DiarySort.UPDATED_DESC, 2);
        // 검색어는 입력창에 그대로 남는다
        assertThat(body).contains("value=\"제주\"");
        // 결과만 갈아 끼울 수 있도록 조각 주소를 폼에 실어 둔다
        assertThat(body).contains("data-fragment-url=\"/diaries/fragment\"");
        assertThat(body).contains("id=\"diary-results\"");
        assertThat(body).contains("/js/diary-search.js");
        // 쪽 링크는 검색어를 계속 달고 다닌다
        // 기본 정렬이라 sort 는 빈 값으로만 붙는다 (서버는 빈 값을 기본 정렬로 본다)
        assertThat(body).contains("/diaries?q=%EC%A0%9C%EC%A3%BC&amp;sort=&amp;page=1");
        assertThat(body).contains("/diaries?q=%EC%A0%9C%EC%A3%BC&amp;sort=&amp;page=3");
        assertThat(body).contains("page-number is-current");
    }

    @Test
    void calendarShowsTripsOfTheRequestedMonth() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryCalendar(7L, YearMonth.of(2026, 8)))
                .thenReturn(calendar(YearMonth.of(2026, 8), diary()));

        String body = mockMvc.perform(get("/diaries/calendar").param("month", "2026-08")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/calendar"))
                .andReturn().getResponse().getContentAsString();

        verify(diaryService).getMyDiaryCalendar(7L, YearMonth.of(2026, 8));
        assertThat(body).contains("여름 제주 여행");
        // 기록을 누르면 기존 READ 로 간다
        assertThat(body).contains("href=\"/diaries/10\"");
        // 달 이동은 주소의 month 로만 오간다
        assertThat(body).contains("/diaries/calendar?month=2026-07");
        assertThat(body).contains("/diaries/calendar?month=2026-09");
        // 연/월은 직접 고를 수 있고 지금 보고 있는 값이 선택돼 있다 (커스텀 팝오버)
        assertThat(body).contains("id=\"diary-calendar-year\"");
        assertThat(body).contains("id=\"diary-calendar-month\"");
        assertThat(body).contains("id=\"diary-calendar-year-menu\"");
        assertThat(body).contains("id=\"diary-calendar-month-menu\"");
        assertThat(body).containsPattern("is-current[^>]*data-year=\"2026\"");
        assertThat(body).containsPattern("is-current[^>]*data-month=\"8\"");
        // 시스템 드롭다운(native select)은 더 이상 쓰지 않는다
        assertThat(body).doesNotContain("<select");
        // 이번 달로 돌아오는 버튼은 month 없는 주소를 쓴다
        assertThat(body).contains("diary-calendar-today");
        assertThat(body).contains("href=\"/diaries/calendar\"");
        assertThat(body).contains("/js/diary-calendar.js");
        // 달만 갈아 끼울 수 있도록 조각 주소와 지금 보고 있는 달을 실어 둔다
        assertThat(body).contains("id=\"diary-calendar-board\"");
        assertThat(body).contains("data-fragment-url=\"/diaries/calendar/fragment\"");
        assertThat(body).contains("data-month=\"2026-08\"");
        // 책장 보기로 돌아갈 수 있다
        assertThat(body).contains("diary-view-switch");
    }

    /** 달 이동용 조각. 달력 자리만 돌려주고 페이지 껍데기는 다시 그리지 않는다. */
    @Test
    void calendarFragmentReturnsOnlyTheCalendarBoard() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryCalendar(7L, YearMonth.of(2026, 9)))
                .thenReturn(calendar(YearMonth.of(2026, 9), diary()));
        when(holidayService.findSpecialDays(YearMonth.of(2026, 9)))
                .thenReturn(Map.of(LocalDate.of(2026, 9, 1), new SpecialDays(List.of(
                        new SpecialDay("추석", SpecialDay.Kind.HOLIDAY)))));

        String body = mockMvc.perform(get("/diaries/calendar/fragment").param("month", "2026-09")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/calendar :: board"))
                .andReturn().getResponse().getContentAsString();

        // 달력 조회/공휴일 조회는 전체 페이지와 같은 서비스 호출을 그대로 쓴다
        verify(diaryService).getMyDiaryCalendar(7L, YearMonth.of(2026, 9));
        verify(holidayService).findSpecialDays(YearMonth.of(2026, 9));
        // 월 네비게이션·날짜·공휴일·여행일기 chip 이 모두 조각 안에 들어 있다
        assertThat(body).contains("id=\"diary-calendar-board\"");
        assertThat(body).contains("data-month=\"2026-09\"");
        assertThat(body).contains("id=\"diary-calendar-year\"");
        assertThat(body).contains("/diaries/calendar?month=2026-10");
        assertThat(body).contains("추석");
        assertThat(body).contains("여름 제주 여행");
        // 페이지 껍데기(레이아웃/머리말)는 들어 있지 않다
        assertThat(body).doesNotContain("<html");
        assertThat(body).doesNotContain("MY TRAVEL DIARY");
    }

    /** month 가 없으면 조각도 전체 페이지와 똑같이 이번 달을 본다. ('오늘' 버튼) */
    @Test
    void calendarFragmentWithoutMonthShowsThisMonth() throws Exception {
        YearMonth now = YearMonth.now();
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryCalendar(7L, now)).thenReturn(calendar(now, diary()));

        String body = mockMvc.perform(get("/diaries/calendar/fragment")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        verify(diaryService).getMyDiaryCalendar(7L, now);
        assertThat(body).contains("data-month=\"" + now + "\"");
        // 이번 달이라 '오늘' 은 눌러도 옮길 곳이 없다
        assertThat(body).containsPattern("diary-calendar-today\\s+is-current");
    }

    @Test
    void calendarMarksHolidaysAndKeepsTripChips() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryCalendar(7L, YearMonth.of(2026, 8)))
                .thenReturn(calendar(YearMonth.of(2026, 8), diary()));
        // calendar() 는 그 달 1일에 여행을 둔다
        when(holidayService.findSpecialDays(YearMonth.of(2026, 8)))
                .thenReturn(Map.of(LocalDate.of(2026, 8, 1), new SpecialDays(List.of(
                        new SpecialDay("광복절", SpecialDay.Kind.HOLIDAY),
                        new SpecialDay("임시공휴일", SpecialDay.Kind.HOLIDAY)))));

        String body = mockMvc.perform(get("/diaries/calendar").param("month", "2026-08")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 같은 날짜의 공휴일 이름은 하나도 빠지지 않는다
        assertThat(body).contains("광복절").contains("임시공휴일");
        assertThat(body).contains("is-holiday");
        // 공휴일이 있어도 여행 chip 과 상세 링크는 그대로다
        assertThat(body).contains("여름 제주 여행");
        assertThat(body).contains("href=\"/diaries/10\"");
    }

    /** 24절기·잡절은 이름만 적히고 공휴일처럼 빨간 날이 되지 않는다. */
    @Test
    void calendarShowsSeasonalTermsWithoutMarkingThemAsHolidays() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryCalendar(7L, YearMonth.of(2026, 8)))
                .thenReturn(calendar(YearMonth.of(2026, 8), diary()));
        when(holidayService.findSpecialDays(YearMonth.of(2026, 8)))
                .thenReturn(Map.of(LocalDate.of(2026, 8, 1), new SpecialDays(List.of(
                        new SpecialDay("입추", SpecialDay.Kind.SEASONAL_TERM),
                        new SpecialDay("말복", SpecialDay.Kind.SUNDRY_DAY)))));

        String body = mockMvc.perform(get("/diaries/calendar").param("month", "2026-08")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("입추").contains("말복");
        assertThat(body).contains("diary-calendar-term");
        // 절기·잡절만 있는 날은 빨간 날이 아니다
        assertThat(body).doesNotContain("is-holiday");
        assertThat(body).contains("여름 제주 여행");
    }

    /** 공휴일/절기를 못 불러와도(빈 목록) 달력은 그대로 그려진다. */
    @Test
    void calendarStillRendersWhenHolidaysAreUnavailable() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryCalendar(7L, YearMonth.of(2026, 8)))
                .thenReturn(calendar(YearMonth.of(2026, 8), diary()));
        when(holidayService.findSpecialDays(YearMonth.of(2026, 8))).thenReturn(Map.of());

        String body = mockMvc.perform(get("/diaries/calendar").param("month", "2026-08")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("여름 제주 여행");
        assertThat(body).doesNotContain("is-holiday");
        // 특일 이름이 하나도 없어도 특일 자리는 모든 칸에 남는다 (여행일기 줄 시작 높이 고정)
        assertThat(body).contains("class=\"diary-calendar-special\"");
        assertThat(body).doesNotContain("diary-calendar-holiday");
    }

    /** 특일이 있는 칸과 없는 칸이 섞여도 특일 자리는 42칸 모두에 똑같이 있다. */
    @Test
    void everyDayCellKeepsTheSpecialDayRowSoChipsLineUp() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryCalendar(7L, YearMonth.of(2026, 8)))
                .thenReturn(calendar(YearMonth.of(2026, 8), diary()));
        when(holidayService.findSpecialDays(YearMonth.of(2026, 8)))
                .thenReturn(Map.of(LocalDate.of(2026, 8, 1), new SpecialDays(List.of(
                        new SpecialDay("광복절", SpecialDay.Kind.HOLIDAY)))));

        String body = mockMvc.perform(get("/diaries/calendar").param("month", "2026-08")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 6주 x 7일
        assertThat(body.split("class=\"diary-calendar-special\"", -1)).hasSize(43);
        assertThat(body).contains("광복절");
    }

    /** 주소로 바로 들어온 연도도 연도 목록에 들어간다. */
    @Test
    void calendarYearOptionsAlwaysContainTheShownYear() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryCalendar(7L, YearMonth.of(1998, 3)))
                .thenReturn(calendar(YearMonth.of(1998, 3)));

        String body = mockMvc.perform(get("/diaries/calendar").param("month", "1998-03")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).containsPattern("is-current[^>]*data-year=\"1998\"");
        assertThat(body).contains(String.valueOf(YearMonth.now().getYear()));
    }

    /** month 가 없거나 형식이 다르면 이번 달을 본다. (서버 오류가 나지 않는다) */
    @Test
    void oddMonthParameterFallsBackToThisMonth() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryCalendar(eq(7L), any()))
                .thenReturn(calendar(YearMonth.now()));

        for (String month : new String[]{"", "2026-13", "abc", "2026/08"}) {
            mockMvc.perform(get("/diaries/calendar").param("month", month)
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    userDetails, null, List.of()))))
                    .andExpect(status().isOk());
        }

        verify(diaryService, org.mockito.Mockito.times(4))
                .getMyDiaryCalendar(7L, YearMonth.now());
    }

    @Test
    void calendarOfAGuestIsSentToLogin() throws Exception {
        mockMvc.perform(get("/diaries/calendar"))
                .andExpect(status().is3xxRedirection());
    }

    /** 비동기 검색은 같은 서비스 호출로 결과 조각만 돌려준다. (전체 페이지가 아니다) */
    @Test
    void fragmentReturnsOnlyTheResultsPart() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryPage(7L, "제주", DiarySort.UPDATED_DESC, 2))
                .thenReturn(new DiaryListPageDto(List.of(item()), "제주", DiarySort.UPDATED_DESC, 2, 3, 30, 12));

        String body = mockMvc.perform(get("/diaries/fragment").param("q", "제주").param("page", "2")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/list :: results"))
                .andReturn().getResponse().getContentAsString();

        verify(diaryService).getMyDiaryPage(7L, "제주", DiarySort.UPDATED_DESC, 2);
        // 카드/쪽 이동은 그대로 들어 있다
        assertThat(body).contains("id=\"diary-results\"");
        assertThat(body).contains("여름 제주 여행");
        assertThat(body).contains("diary-pagination");
        assertThat(body).contains("diary-book-menu-button");
        // 머리말/검색창 같은 페이지 껍데기는 없다
        assertThat(body).doesNotContain("diary-search-input");
        assertThat(body).doesNotContain("<html");
    }

    /** 정렬은 주소의 sort 로 오간다. 고른 정렬은 검색어/쪽과 함께 그대로 남는다. */
    @Test
    void sortComesFromTheQueryStringAndStaysInTheLinks() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryPage(7L, "제주", DiarySort.TRIP_ASC, 2))
                .thenReturn(new DiaryListPageDto(List.of(item()), "제주", DiarySort.TRIP_ASC,
                        2, 3, 30, 12));

        String body = mockMvc.perform(get("/diaries")
                        .param("q", "제주").param("sort", "TRIP_ASC").param("page", "2")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        verify(diaryService).getMyDiaryPage(7L, "제주", DiarySort.TRIP_ASC, 2);
        // 고른 정렬이 버튼 이름과 체크 표시에 그대로 보인다
        assertThat(body).contains("id=\"diary-sort-button\"");
        assertThat(body).contains("data-default-sort=\"UPDATED_DESC\"");
        assertThat(body).containsPattern("is-current[^>]*data-sort=\"TRIP_ASC\"");
        assertThat(body).contains("오래된 여행순");
        // 시스템 드롭다운은 쓰지 않는다
        assertThat(body).doesNotContain("<select");
        // 쪽 링크는 검색어와 정렬을 함께 달고 다닌다
        assertThat(body).contains("/diaries?q=%EC%A0%9C%EC%A3%BC&amp;sort=TRIP_ASC&amp;page=1");
        assertThat(body).contains("/diaries?q=%EC%A0%9C%EC%A3%BC&amp;sort=TRIP_ASC&amp;page=3");
    }

    /** 목록에 없는 정렬값은 기본 정렬(최근 수정순)로 본다. (SQL 조각이 들어올 자리가 없다) */
    @Test
    void unknownSortFallsBackToTheDefault() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryPage(7L, null, DiarySort.UPDATED_DESC, 1))
                .thenReturn(listPage(List.of(item())));

        for (String sort : new String[]{"id; DROP TABLE diaries", "start_date", ""}) {
            mockMvc.perform(get("/diaries").param("sort", sort)
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    userDetails, null, List.of()))))
                    .andExpect(status().isOk());
        }

        verify(diaryService, org.mockito.Mockito.times(3))
                .getMyDiaryPage(7L, null, DiarySort.UPDATED_DESC, 1);
    }

    /** 조각도 같은 정렬 규칙을 그대로 쓴다. (비동기 정렬 변경) */
    @Test
    void fragmentUsesTheSameSort() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryPage(7L, null, DiarySort.TITLE_ASC, 1))
                .thenReturn(new DiaryListPageDto(List.of(item()), "", DiarySort.TITLE_ASC,
                        1, 1, 1, 12));

        mockMvc.perform(get("/diaries/fragment").param("sort", "TITLE_ASC")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/list :: results"));

        verify(diaryService).getMyDiaryPage(7L, null, DiarySort.TITLE_ASC, 1);
    }

    @Test
    void fragmentOfAGuestIsSentToLogin() throws Exception {
        mockMvc.perform(get("/diaries/fragment").param("q", "제주"))
                .andExpect(status().is3xxRedirection());
    }

    /** 0/음수/숫자가 아닌 쪽 번호는 첫 쪽으로 본다. */
    @Test
    void oddPageNumbersFallBackToTheFirstPage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryPage(eq(7L), any(), any(), eq(1)))
                .thenReturn(listPage(List.of(item())));

        for (String page : new String[]{"0", "-3", "abc", ""}) {
            mockMvc.perform(get("/diaries").param("page", page)
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    userDetails, null, List.of()))))
                    .andExpect(status().isOk());
        }

        verify(diaryService, org.mockito.Mockito.times(4))
                .getMyDiaryPage(eq(7L), any(), any(), eq(1));
    }

    @Test
    void emptySearchResultIsShownApartFromAnEmptyShelf() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryPage(7L, "부산", DiarySort.UPDATED_DESC, 1))
                .thenReturn(new DiaryListPageDto(List.of(), "부산", DiarySort.UPDATED_DESC,
                        1, 1, 0, 12));

        String body = mockMvc.perform(get("/diaries").param("q", "부산")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("해당하는 여행일기를 찾지 못했어요.");
        assertThat(body).contains("전체 여행일기 보기");
        // 아직 한 권도 없는 상태의 안내와는 구분한다
        assertThat(body).doesNotContain("아직 작성한 여행일기가 없습니다.");
        assertThat(body).contains("value=\"부산\"");
    }

    @Test
    void emptyListShowsAGuideMessage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryPage(7L, null, DiarySort.UPDATED_DESC, 1)).thenReturn(listPage(List.of()));

        mockMvc.perform(get("/diaries")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("아직 작성한 여행일기가 없습니다.")));
    }

    @Test
    void detailOpensTheOwnedDiaryWithItsPagesInOrder() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));

        mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/detail"))
                .andExpect(content().string(containsString("여름 제주 여행")))
                .andExpect(content().string(containsString("전체 1장")))
                .andExpect(content().string(containsString("diary-book-spread")))
                .andExpect(content().string(containsString("나의 여행일기로")));

        // 소유권은 서비스에서 확인한다 (diaryId 만으로 조회하지 않는다)
        verify(diaryService).getMyDiary(10L, 7L);
        verify(diaryPageService).getPages(10L, 7L);
    }

    @Test
    void detailOfAnotherUsersDiaryIsNotFound() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(99L, 7L)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "다이어리를 찾을 수 없습니다."));

        mockMvc.perform(get("/diaries/99")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void detailWithoutPagesShowsTheEmptySheetMessage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of());

        mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("아직 작성한 페이지가 없습니다.")))
                .andExpect(content().string(containsString("여행의 첫 장을 남겨보세요.")));
    }

    @Test
    void detailShowsTwoPagesPerSpreadAndMovesWithTheSpreadParameter() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(
                page(1, "2026-08-01"), page(2, "2026-08-01"),
                page(3, "2026-08-02"), page(4, "2026-08-02"),
                page(5, "2026-08-03")));

        // 두 번째 펼침: 3,4 장
        mockMvc.perform(get("/diaries/10").param("spread", "1")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentSpread", 1))
                .andExpect(model().attribute("totalSpreads", 3))
                .andExpect(model().attribute("hasPreviousSpread", true))
                .andExpect(model().attribute("hasNextSpread", true))
                .andExpect(content().string(containsString("3-4 / 전체 5장")))
                .andExpect(content().string(containsString("spread=0")))
                .andExpect(content().string(containsString("spread=2")));

        // 마지막 펼침: 한 장만 남으면 오른쪽은 빈 종이
        mockMvc.perform(get("/diaries/10").param("spread", "2")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("rightPage", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attribute("hasNextSpread", false))
                .andExpect(content().string(containsString("5 / 전체 5장")));
    }

    @Test
    void outOfRangeSpreadIsClampedInsteadOfFailing() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L))
                .thenReturn(List.of(page(1, "2026-08-01"), page(2, "2026-08-01")));

        mockMvc.perform(get("/diaries/10").param("spread", "-5")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentSpread", 0));

        mockMvc.perform(get("/diaries/10").param("spread", "99")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentSpread", 0))
                .andExpect(model().attribute("hasNextSpread", false));
    }

    @Test
    void detailOffersTheAddPageFormLimitedToTheTravelPeriod() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of());

        // 페이지 추가 폼은 읽기 상단 액션에서 펼친다
        mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("새 페이지 추가")))
                .andExpect(content().string(containsString("action=\"/diaries/10/pages\"")))
                .andExpect(content().string(containsString("min=\"2026-08-01\"")))
                .andExpect(content().string(containsString("max=\"2026-08-05\"")))
                // 배경은 한국어 라벨로 보여준다
                .andExpect(content().string(containsString("무지")))
                .andExpect(content().string(containsString("줄노트")))
                .andExpect(content().string(containsString("모눈")))
                .andExpect(content().string(containsString("도트")))
                // 순서는 사용자가 입력하지 않는다
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("name=\"pageOrder\""))));
    }

    @Test
    void addPageDelegatesToTheServiceAndRedirectsBackToTheDiary() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryPageService.append(eq(10L), eq(7L), any(DiaryPage.class)))
                .thenReturn(page(1, "2026-08-01"));

        mockMvc.perform(post("/diaries/10/pages")
                        .param("pageDate", "2026-08-02")
                        .param("backgroundType", "LINED")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                // 편집 모드는 한 장씩 보므로 방금 추가한 장을 바로 연다
                .andExpect(redirectedUrl("/diaries/10?edit=true&page=1"))
                .andExpect(flash().attribute("diaryMessage", "새 페이지가 추가되었습니다."));

        ArgumentCaptor<DiaryPage> captor = ArgumentCaptor.forClass(DiaryPage.class);
        verify(diaryPageService).append(eq(10L), eq(7L), captor.capture());
        assertThat(captor.getValue().getPageDate()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(captor.getValue().getBackgroundType()).isEqualTo("LINED");
        // 순서는 서비스가 정한다 (요청 값 사용 안 함)
        assertThat(captor.getValue().getPageOrder()).isNull();
    }

    @Test
    void addPageValidationFailureShowsTheMessageOnTheDiary() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryPageService.append(eq(10L), eq(7L), any(DiaryPage.class))).thenThrow(
                new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "페이지 날짜는 여행 기간 안에서 선택해 주세요."));

        mockMvc.perform(post("/diaries/10/pages")
                        .param("pageDate", "2026-09-09")
                        .param("backgroundType", "PLAIN")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                // 추가 폼은 읽기 화면 상단에 있으므로 실패하면 읽기 화면으로 돌아온다
                .andExpect(redirectedUrl("/diaries/10"))
                .andExpect(flash().attribute(
                        "diaryPageError", "페이지 날짜는 여행 기간 안에서 선택해 주세요."));
    }

    @Test
    void detailRendersThePageContentAndPhotoElementsOfTheOpenedPagesOnly() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        DiaryPage first = page(1, "2026-08-01");
        first.setContent("<p>첫째 날 기록</p>");
        when(diaryPageService.getPages(10L, 7L))
                .thenReturn(List.of(first, page(2, "2026-08-02")));
        when(diaryElementService.getElements(10L, 1L, 7L)).thenReturn(List.of(
                photoElement(101L, "/uploads/diary/photo.jpg")));
        when(diaryElementService.getElements(10L, 2L, 7L)).thenReturn(List.of());

        mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                // 본문은 종이 자체에 그대로 그려진다
                .andExpect(content().string(containsString("<p>첫째 날 기록</p>")))
                .andExpect(content().string(containsString("diary-editor")))
                .andExpect(content().string(containsString("/uploads/diary/photo.jpg")));

        // 펼친 두 장만 조회한다
        verify(diaryElementService).getElements(10L, 1L, 7L);
        verify(diaryElementService).getElements(10L, 2L, 7L);
    }

    @Test
    void elementsAreDrawnOnTheCanvasWithTheirStoredRelativeGeometry() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));

        DiaryElement photo = photoElement(100L, "/uploads/diary/photo.jpg");
        photo.setPositionX(new java.math.BigDecimal("0.25000"));
        photo.setPositionY(new java.math.BigDecimal("0.40000"));
        photo.setWidth(new java.math.BigDecimal("0.30000"));
        photo.setHeight(new java.math.BigDecimal("0.20000"));
        photo.setRotation(new java.math.BigDecimal("-4.50"));
        photo.setZIndex(2);
        when(diaryElementService.getElements(10L, 1L, 7L)).thenReturn(List.of(photo));

        String body = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("diary-canvas");
        // 상대값(0~1)이 % 로 그대로 옮겨진다
        assertThat(body).contains("left:25.00000%");
        assertThat(body).contains("top:40.00000%");
        assertThat(body).contains("width:30.00000%");
        assertThat(body).contains("height:20.00000%");
        assertThat(body).contains("rotate(-4.50deg)");
        assertThat(body).contains("z-index:2");
    }

    @Test
    void diaryEditFormIsPrefilledForTheOwner() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        Diary existing = diary();
        existing.setCoverImageUrl("/uploads/diary-covers/old.jpg");
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(existing);

        mockMvc.perform(get("/diaries/10/edit")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/edit"))
                .andExpect(content().string(containsString("여름 제주 여행")))
                .andExpect(content().string(containsString("/uploads/diary-covers/old.jpg")))
                .andExpect(content().string(containsString("action=\"/diaries/10/update\"")));
    }

    @Test
    void diaryUpdateKeepsTheOldCoverWhenNoNewFileIsChosen() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        Diary existing = diary();
        existing.setCoverImageUrl("/uploads/diary-covers/old.jpg");
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(existing);
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-02")));

        mockMvc.perform(multipart("/diaries/10/update")
                        .param("title", "제주 여행 다시")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-05")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                // 다이어리 설정은 목록의 ⋯ 메뉴에서 들어오므로 저장 뒤 목록으로 돌아간다
                .andExpect(redirectedUrl("/diaries"));

        ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryService).update(eq(10L), eq(7L), captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("제주 여행 다시");
        assertThat(captor.getValue().getCoverImageUrl()).isEqualTo("/uploads/diary-covers/old.jpg");
        // 표지를 바꾸지 않았으므로 새 파일 저장도 없다
        verify(fileUploadService, org.mockito.Mockito.never()).saveFile(any(), any());
    }

    @Test
    void shrinkingThePeriodIsBlockedWhenAPageWouldFallOutside() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(
                page(1, "2026-08-01"), page(2, "2026-08-05")));

        mockMvc.perform(multipart("/diaries/10/update")
                        .param("title", "여름 제주 여행")
                        .param("startDate", "2026-08-02")
                        .param("endDate", "2026-08-04")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/edit"))
                .andExpect(content().string(containsString("여행 기간 밖으로 밀려나는 페이지가 있어")));

        verify(diaryService, org.mockito.Mockito.never())
                .update(any(), any(), any(Diary.class));
    }

    @Test
    void diaryDeleteCollectsCoverAndPhotoFilesBeforeRemovingTheRow() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        Diary existing = diary();
        existing.setCoverImageUrl("/uploads/diary-covers/cover.jpg");
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(existing);
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        when(diaryElementService.getElements(10L, 1L, 7L)).thenReturn(List.of(
                textElement(100L, "기록"),
                photoElement(101L, "/uploads/diary-pages/a.jpg")));

        mockMvc.perform(post("/diaries/10/delete")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries"))
                .andExpect(flash().attribute("diaryMessage", "여행일기가 삭제되었습니다."));

        // 파일 경로를 모두 확보한 뒤 다이어리를 지운다
        InOrder inOrder = org.mockito.Mockito.inOrder(diaryPageService, diaryElementService, diaryService);
        inOrder.verify(diaryPageService).getPages(10L, 7L);
        inOrder.verify(diaryElementService).getElements(10L, 1L, 7L);
        inOrder.verify(diaryService).delete(10L, 7L);
    }

    @Test
    void updatePageKeepsItsOrderAndChangesOnlyDateAndBackground() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        DiaryPage existing = page(3, "2026-08-02");
        when(diaryPageService.getPage(10L, 3L, 7L)).thenReturn(existing);

        mockMvc.perform(post("/diaries/10/pages/3/update")
                        .param("pageDate", "2026-08-04")
                        .param("backgroundType", "GRID")
                        .param("pageOrder", "1")
                        .param("spread", "1")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/10?spread=1&edit=true"));

        ArgumentCaptor<DiaryPage> captor = ArgumentCaptor.forClass(DiaryPage.class);
        verify(diaryPageService).update(eq(10L), eq(3L), eq(7L), captor.capture());
        assertThat(captor.getValue().getPageDate()).isEqualTo(LocalDate.of(2026, 8, 4));
        assertThat(captor.getValue().getBackgroundType()).isEqualTo("GRID");
        // 요청으로 넘어온 pageOrder(1) 가 아니라 기존 순서(3) 를 유지한다
        assertThat(captor.getValue().getPageOrder()).isEqualTo(3);
    }

    @Test
    void deletePageRemovesPhotoFilesAfterTheRowIsGone() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.getElements(10L, 3L, 7L)).thenReturn(List.of(
                textElement(100L, "기록"),
                photoElement(101L, "/uploads/diary-pages/a.jpg"),
                photoElement(102L, "/uploads/diary-pages/b.jpg")));

        mockMvc.perform(post("/diaries/10/pages/3/delete")
                        .param("spread", "2")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/10?spread=2&edit=true"));

        // 사진 경로를 먼저 확보한 뒤 페이지를 지운다
        InOrder inOrder = org.mockito.Mockito.inOrder(diaryElementService, diaryPageService);
        inOrder.verify(diaryElementService).getElements(10L, 3L, 7L);
        inOrder.verify(diaryPageService).delete(10L, 3L, 7L);
    }

    @Test
    void pageUpdateValidationFailureShowsTheMessage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryPageService.getPage(10L, 3L, 7L)).thenReturn(page(3, "2026-08-02"));
        org.mockito.Mockito.doThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "페이지 날짜는 여행 기간 안에서 선택해 주세요."))
                .when(diaryPageService).update(eq(10L), eq(3L), eq(7L), any(DiaryPage.class));

        mockMvc.perform(post("/diaries/10/pages/3/update")
                        .param("pageDate", "2026-09-09")
                        .param("backgroundType", "PLAIN")
                        .param("spread", "0")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(redirectedUrl("/diaries/10?spread=0&edit=true"))
                .andExpect(flash().attribute(
                        "diaryPageError", "페이지 날짜는 여행 기간 안에서 선택해 주세요."));
    }

    @Test
    void movingAnElementSavesOnlyItsPosition() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.move(eq(10L), eq(3L), eq(100L), eq(7L), any(), any()))
                .thenReturn(textElement(100L, "기록"));

        mockMvc.perform(post("/diaries/10/pages/3/elements/100/position")
                        .param("positionX", "0.42000")
                        .param("positionY", "0.15000")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNoContent());

        verify(diaryElementService).move(10L, 3L, 100L, 7L,
                new java.math.BigDecimal("0.42000"), new java.math.BigDecimal("0.15000"));
    }

    @Test
    void movingOutOfRangeReturnsAMessageInsteadOfAnErrorPage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.move(eq(10L), eq(3L), eq(100L), eq(7L), any(), any()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "가로 위치가 허용 범위를 벗어났습니다."));

        mockMvc.perform(post("/diaries/10/pages/3/elements/100/position")
                        .param("positionX", "9")
                        .param("positionY", "0")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("가로 위치가 허용 범위를 벗어났습니다.")));
    }

    @Test
    void resizingAnElementSavesOnlyItsSize() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.resize(eq(10L), eq(3L), eq(100L), eq(7L), any(), any()))
                .thenReturn(textElement(100L, "기록"));

        mockMvc.perform(post("/diaries/10/pages/3/elements/100/size")
                        .param("width", "0.45000")
                        .param("height", "0.22000")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNoContent());

        verify(diaryElementService).resize(10L, 3L, 100L, 7L,
                new java.math.BigDecimal("0.45000"), new java.math.BigDecimal("0.22000"));
    }

    @Test
    void resizingOutOfRangeReturnsAMessageInsteadOfAnErrorPage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.resize(eq(10L), eq(3L), eq(100L), eq(7L), any(), any()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "너비가 허용 범위를 벗어났습니다."));

        mockMvc.perform(post("/diaries/10/pages/3/elements/100/size")
                        .param("width", "3")
                        .param("height", "0.3")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("너비가 허용 범위를 벗어났습니다.")));
    }

    @Test
    void rotatingAnElementSavesOnlyItsRotation() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.rotate(eq(10L), eq(3L), eq(100L), eq(7L), any()))
                .thenReturn(textElement(100L, "기록"));

        mockMvc.perform(post("/diaries/10/pages/3/elements/100/rotation")
                        .param("rotation", "-12.50")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNoContent());

        verify(diaryElementService).rotate(10L, 3L, 100L, 7L,
                new java.math.BigDecimal("-12.50"));
    }

    @Test
    void rotatingOutOfRangeReturnsAMessageInsteadOfAnErrorPage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.rotate(eq(10L), eq(3L), eq(100L), eq(7L), any()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "회전 각도가 허용 범위를 벗어났습니다."));

        mockMvc.perform(post("/diaries/10/pages/3/elements/100/rotation")
                        .param("rotation", "720")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("회전 각도가 허용 범위를 벗어났습니다.")));
    }

    @Test
    void layerChangeReturnsTheRenumberedOrder() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        DiaryElement first = textElement(100L, "기록");
        first.setZIndex(0);
        DiaryElement second = photoElement(101L, "/uploads/diary-pages/a.jpg");
        second.setZIndex(1);
        when(diaryElementService.changeLayer(10L, 3L, 100L, 7L, true))
                .thenReturn(List.of(second, first));

        mockMvc.perform(post("/diaries/10/pages/3/elements/100/layer")
                        .param("direction", "FORWARD")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"id\":101")))
                .andExpect(content().string(containsString("\"id\":100")));

        verify(diaryElementService).changeLayer(10L, 3L, 100L, 7L, true);
    }

    @Test
    void unknownLayerDirectionIsRejected() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        mockMvc.perform(post("/diaries/10/pages/3/elements/100/layer")
                        .param("direction", "SIDEWAYS")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isBadRequest());

        verify(diaryElementService, org.mockito.Mockito.never())
                .changeLayer(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void canvasElementsCarryTheDragMetadata() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        when(diaryElementService.getElements(10L, 1L, 7L))
                .thenReturn(List.of(photoElement(100L, "/uploads/diary/photo.jpg")));

        // 조작점/레이어 액션은 편집 모드에서만 그린다
        mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "data-position-url=\"/diaries/10/pages/1/elements/100/position\"")))
                .andExpect(content().string(containsString(
                        "data-size-url=\"/diaries/10/pages/1/elements/100/size\"")))
                .andExpect(content().string(containsString(
                        "data-rotation-url=\"/diaries/10/pages/1/elements/100/rotation\"")))
                .andExpect(content().string(containsString("data-position-x=\"0.00000\"")))
                .andExpect(content().string(containsString("data-width=\"0.30000\"")))
                .andExpect(content().string(containsString("data-rotation=\"0.00\"")))
                .andExpect(content().string(containsString("diary-resize-handle")))
                .andExpect(content().string(containsString("diary-rotate-handle")))
                .andExpect(content().string(containsString(
                        "data-layer-url=\"/diaries/10/pages/1/elements/100/layer\"")))
                .andExpect(content().string(containsString("data-layer-direction=\"FORWARD\"")))
                .andExpect(content().string(containsString("/js/diary-canvas-drag.js")));
    }

    @Test
    void detailOpensInReadModeByDefault() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        when(diaryElementService.getElements(10L, 1L, 7L))
                .thenReturn(List.of(photoElement(100L, "/uploads/diary/photo.jpg")));

        String body = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("editMode", false))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("is-read-mode");
        // 읽기 모드는 감상 화면이다. 조작은 종이 바깥의 작은 아이콘(연필 / +)만 남는다.
        assertThat(body).doesNotContain("편집하기");
        assertThat(body).contains("diary-read-tools");
        // 책 자체 관리(설정/삭제)는 목록 카드의 ⋯ 메뉴로 옮겼다
        assertThat(body).doesNotContain("다이어리 설정");
        assertThat(body).doesNotContain("/diaries/10/edit");
        assertThat(body).doesNotContain("다이어리 삭제");
        assertThat(body).doesNotContain("/diaries/10/delete");
        // 읽기 모드에는 페이지 내용 편집 UI 가 없다
        assertThat(body).doesNotContain("diary-toolbar");
        assertThat(body).doesNotContain("diary-photo-input");
        assertThat(body).doesNotContain("diary-page-action");
        assertThat(body).doesNotContain("/diaries/10/pages/1/update");
        assertThat(body).doesNotContain("/diaries/10/pages/1/delete");
        // 제목 아래 한 줄에 기간과 장수를 함께 둔다
        assertThat(body).contains("diary-detail-headline");
        assertThat(body).contains("diary-detail-meta");
        assertThat(body.indexOf("diary-detail-title"))
                .isLessThan(body.indexOf("diary-detail-meta"));
        assertThat(body).contains("전체 1장");
        // 새 페이지 추가는 읽기 상단 액션에 있다 (편집 캔버스가 아니라 책 관리 쪽)
        assertThat(body).contains("diary-detail-actions");
        assertThat(body).contains("diary-page-add");
        assertThat(body).contains("새 페이지 추가");
        assertThat(body).contains("action=\"/diaries/10/pages\"");
        // 펼침이 바뀌면 통째로 갈리는 판 바깥에 있어야 버튼이 사라지지 않는다
        assertThat(body.indexOf("diary-page-add"))
                .isLessThan(body.indexOf("diary-read-board"));
        assertThat(body).doesNotContain("diary-resize-handle");
        assertThat(body).doesNotContain("diary-rotate-handle");
        assertThat(body).doesNotContain("diary-layer-action");
        assertThat(body).doesNotContain("photo/delete");
        // 본문은 편집기가 아니라 읽기 전용으로 그려진다
        assertThat(body).contains("is-read-only");
        assertThat(body).doesNotContain("data-content-url");
        // 사진 자체는 그대로 보인다
        assertThat(body).contains("/uploads/diary/photo.jpg");
    }

    @Test
    void editQueryOpensTheEditingUi() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        when(diaryElementService.getElements(10L, 1L, 7L))
                .thenReturn(List.of(photoElement(100L, "/uploads/diary/photo.jpg")));

        String body = mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("editMode", true))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("is-edit-mode");
        assertThat(body).contains("편집 중");
        // 완료 / 설정 / 삭제는 툴바 바로 위 한 줄에 같은 모양으로 모여 있다
        assertThat(body).contains("aria-label=\"편집 완료\"");
        assertThat(body.indexOf("diary-page-meta")).isLessThan(body.indexOf("diary-toolbar"));
        assertThat(body.indexOf("편집 완료"))
                .isLessThan(body.indexOf("페이지 설정"));
        assertThat(body.indexOf("페이지 설정"))
                .isLessThan(body.indexOf("페이지 삭제"));
        // 페이지 설정/삭제는 글자 대신 아이콘만 남는다
        assertThat(body).contains("aria-label=\"페이지 설정\"");
        assertThat(body).contains("aria-label=\"페이지 삭제\"");
        assertThat(body).doesNotContain(">페이지 설정</");
        assertThat(body).doesNotContain(">페이지 삭제</");
        // 종이 바깥의 쪽번호·날짜 줄은 없앴다 (종이 안 날짜는 그대로)
        assertThat(body).doesNotContain("diary-page-meta-label");
        assertThat(body).contains("diary-toolbar");
        assertThat(body).contains("diary-photo-input");
        assertThat(body).contains("diary-page-action");
        assertThat(body).contains("/diaries/10/pages/1/update");
        // 새 페이지 추가 폼은 편집 화면에서 빠지고 읽기 상단으로 옮겼다
        assertThat(body).doesNotContain("diary-page-add");
        assertThat(body).contains("diary-resize-handle");
        assertThat(body).contains("/diaries/10/pages/1/content");
        // 사진 삭제는 폼 전송(=화면 새로고침)이 아니라 비동기 요청으로 처리한다
        assertThat(body).contains("data-delete-confirm=\"이 사진을 삭제하시겠습니까?\"");
        assertThat(body).contains("/diaries/10/pages/1/elements/100/photo/delete");
        assertThat(body).doesNotContain("diary-photo-delete");
        // 편집 완료는 edit 이 빠진 읽기 모드 주소로 간다
        assertThat(body).contains("data-editor-done=\"true\"");
        // 책 자체 관리(설정/삭제)는 읽기 모드 몫이라 편집 모드에는 없다
        assertThat(body).doesNotContain("다이어리 설정");
        assertThat(body).doesNotContain("다이어리 삭제");
        assertThat(body).doesNotContain("/diaries/10/edit");
        assertThat(body).doesNotContain("/diaries/10/delete");
        // 페이지 관리 액션은 종이 안이 아니라 종이 바깥 줄에 있다
        assertThat(body.indexOf("diary-page-meta")).isLessThan(body.indexOf("diary-book-single"));
        assertThat(body.substring(body.indexOf("diary-sheet-single")))
                .doesNotContain("diary-page-action");
    }

    /** 읽기 화면의 편집 진입: 고르는 창 없이 그 장의 연필로 바로 간다. */
    @Test
    void eachOpenPageHasItsOwnEditPencil() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(
                page(1, "2026-08-01"), page(2, "2026-08-02"), page(3, "2026-08-03")));

        String body = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 펼쳐 놓은 두 장이 각자의 편집 주소를 들고 있다
        assertThat(body).contains("diary-read-tools");
        assertThat(body).contains("class=\"diary-page-edit\"");
        assertThat(body).contains("spread=0&amp;edit=true&amp;page=1");
        assertThat(body).contains("spread=0&amp;edit=true&amp;page=2");
        assertThat(body).contains("1페이지 편집").contains("2페이지 편집");
        // 지금 펼치지 않은 장은 이 화면에 없다
        assertThat(body).doesNotContain("edit=true&amp;page=3");

        // 고르는 창과 '편집하기' 버튼은 없앴다
        assertThat(body).doesNotContain("편집하기");
        assertThat(body).doesNotContain("diary-page-picker");
        assertThat(body).doesNotContain("편집할 페이지를 선택해 주세요");
        assertThat(body).doesNotContain("/js/diary-page-picker.js");
    }

    /** 오른쪽 장이 없는 펼침에서는 그 자리에 연필도 없다. */
    @Test
    void aMissingRightPageHasNoEditPencil() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));

        String body = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("1페이지 편집");
        assertThat(body).doesNotContain("2페이지 편집");
    }

    /** 펼침 이동용 조각. 책 자리만 돌려주고 페이지 껍데기는 다시 그리지 않는다. */
    @Test
    void spreadFragmentReturnsOnlyTheReadBoard() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(
                page(1, "2026-08-01"), page(2, "2026-08-02"), page(3, "2026-08-03")));
        when(diaryElementService.getElements(10L, 3L, 7L)).thenReturn(List.of(stickerElement(
                200L, "/images/diary/stickers/masking-tape/tape-cat-cream.svg")));

        String body = mockMvc.perform(get("/diaries/10/spread").param("spread", "1")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/detail :: readBoard"))
                .andReturn().getResponse().getContentAsString();

        // 책 + 장별 연필 + 펼침 이동이 모두 조각 안에 들어 있다
        assertThat(body).contains("id=\"diary-read-board\"");
        assertThat(body).contains("data-spread=\"1\"");
        assertThat(body).contains("diary-read-tools");
        assertThat(body).contains("3페이지 편집");
        assertThat(body).contains("diary-book-spread");
        assertThat(body).contains("diary-spread-arrow");
        // 저장된 요소도 상세 화면과 같은 값으로 그려진다 (좌표/되풀이 조각 포함)
        assertThat(body).contains("data-tape-center");
        assertThat(body).contains("data-sticker-kind=\"masking-tape\"");
        // 페이지 껍데기(제목/기간/레이아웃)는 들어 있지 않다
        assertThat(body).doesNotContain("<html");
        assertThat(body).doesNotContain("diary-detail-header");
    }

    /** 조각도 상세와 같은 소유권/범위 규칙을 그대로 쓴다. */
    @Test
    void spreadFragmentClampsTheSpreadAndNeedsLogin() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));

        String body = mockMvc.perform(get("/diaries/10/spread").param("spread", "9")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("data-spread=\"0\"");

        mockMvc.perform(get("/diaries/10/spread").param("spread", "0"))
                .andExpect(status().is3xxRedirection());
    }

    /** 아래쪽 펼침 이동은 작은 화살표만 남기고 경로/비활성 처리는 그대로다. */
    @Test
    void readSpreadNavIsACompactArrowPair() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(
                page(1, "2026-08-01"), page(2, "2026-08-02"), page(3, "2026-08-03")));

        String body = mockMvc.perform(get("/diaries/10").param("spread", "1")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("diary-spread-nav is-read");
        assertThat(body).contains("class=\"diary-spread-arrow\"");
        // 이동은 조각만 갈아 끼운다 (조각 주소와 지금 펼침을 함께 실어 둔다)
        assertThat(body).contains("data-spread-url=\"/diaries/10/spread\"");
        assertThat(body).contains("data-spread=\"1\"");
        assertThat(body).contains("aria-label=\"이전 페이지\"");
        // 큰 사각 버튼과 글자는 빠졌다
        assertThat(body).doesNotContain("← 이전");
        assertThat(body).doesNotContain("다음 →");
        // 이동 경로와 넘김 연출 표시는 그대로다
        assertThat(body).contains("data-flip=\"previous\"");
        assertThat(body).contains("/diaries/10?spread=0");
        // 더 갈 곳이 없으면 옅게만 남긴다
        assertThat(body).contains("diary-spread-arrow is-disabled");
        assertThat(body).contains("3 / 전체 3장");
    }

    /** 한 장도 없는 다이어리: 편집할 장이 없으니 연필도 없고 + 만 남는다. */
    @Test
    void anEmptyDiaryOnlyOffersAddPage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of());

        String body = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("아직 작성한 페이지가 없습니다.");
        // 첫 장은 종이 바깥의 + 로 만든다
        assertThat(body).contains("새 페이지 추가");
        assertThat(body).contains("action=\"/diaries/10/pages\"");
        assertThat(body).doesNotContain("class=\"diary-page-edit\"");
        // 펼침 이동 줄은 아예 나오지 않는다
        assertThat(body).doesNotContain("diary-spread-arrow");
    }

    @Test
    void diaryEditFormUsesTheSameLayoutAsTheNewForm() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        Diary diary = diary();
        diary.setCoverImageUrl("/uploads/diary-covers/old.jpg");
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary);

        String body = mockMvc.perform(get("/diaries/10/edit")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 작성 화면과 같은 여행 기간 묶음 + 4자리 연도 제한
        assertThat(body).contains("여행 기간");
        assertThat(body).contains("diary-period-inputs");
        assertThat(body).contains("diary-period-separator");
        assertThat(body).contains("min=\"1000-01-01\"").contains("max=\"9999-12-31\"");
        // 작성 화면과 같은 compact picker (기본 파일 선택 UI 숨김, 별도 미리보기 상자 없음)
        assertThat(body).contains("diary-cover-input");
        assertThat(body).contains("diary-cover-picker");
        assertThat(body).contains("diary-cover-thumb");
        assertThat(body).doesNotContain("diary-cover-preview\"");
        // 지금 표지는 같은 picker 안의 썸네일로 보인다
        assertThat(body).contains("has-image");
        assertThat(body).contains("지금 표지를 사용 중");
        assertThat(body).contains("/uploads/diary-covers/old.jpg");
        assertThat(body).contains("/js/diary-cover-picker.js");
        assertThat(body).contains("수정 완료");
    }

    @Test
    void newFormUsesTheSameCompactCoverPickerInItsEmptyState() throws Exception {
        String body = mockMvc.perform(get("/diaries/new")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("diary-cover-picker");
        assertThat(body).contains("diary-cover-thumb");
        assertThat(body).contains("대표 이미지 선택");
        // 고르기 전에는 썸네일 이미지가 숨겨져 있고 별도 미리보기 상자도 없다
        assertThat(body).doesNotContain("has-image");
        assertThat(body).doesNotContain("diary-cover-preview\"");
    }

    /** 일반 노트는 예전 화면 그대로다. 코일은 어디에도 나오지 않는다. */
    @Test
    void aClassicDiaryKeepsTheBookSpineAndShowsNoCoil() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));

        String body = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("diary-book-classic");
        assertThat(body).doesNotContain("diary-book-spiral");
        assertThat(body).doesNotContain("diary-book-spring");
        assertThat(body).doesNotContain("diary-sheet-spring");
        // 두 장 사이 칸은 남지만 그 안에 장식은 없다 (CSS 가 이 칸을 0 으로 줄인다)
        assertThat(body).contains("diary-book-gutter");
        assertThat(body).doesNotContain("diary-book-ribbon");
    }

    @Test
    void aSpiralDiaryShowsTheCoilDownTheMiddleWhileReading() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(spiralDiary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));

        String body = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("diary-book-spiral");
        assertThat(body).doesNotContain("diary-book-classic");
        assertThat(body).contains("diary-book-spring");
        // 좁은 화면에서 쓰는 장 가장자리 코일도 함께 그려 둔다
        assertThat(body).contains("diary-sheet-spring");
        // 코일이 지날 자리는 남는다 (일반 노트는 이 칸을 0 으로 줄인다)
        assertThat(body).contains("diary-book-gutter");
    }

    @Test
    void aSpiralDiaryKeepsItsCoilWhileEditingOnePage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(spiralDiary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));

        String body = mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("diary-book-single");
        assertThat(body).contains("diary-book-spiral");
        // 한 장 화면에는 가운데 제본이 없으므로 장 가장자리 코일만 쓴다
        assertThat(body).contains("diary-sheet-spring");
        assertThat(body).doesNotContain("diary-book-spring");
    }

    /** 페이지를 넘겨 판을 갈아 끼워도 공책 모양은 그대로다. */
    @Test
    void theCoilSurvivesAPageTurn() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(spiralDiary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(
                page(1, "2026-08-01"), page(2, "2026-08-02"),
                page(3, "2026-08-03"), page(4, "2026-08-04")));

        String board = mockMvc.perform(get("/diaries/10/spread").param("spread", "1")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(board).contains("diary-book-spiral");
        assertThat(board).contains("diary-book-spring");
    }

    /** 컬럼이 생기기 전의 다이어리(값 없음)는 일반 노트로 그린다. */
    @Test
    void aDiarySavedBeforeTheColumnExistedIsDrawnAsAClassicNotebook() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        Diary old = diary();
        old.setNotebookType(null);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(old);
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));

        String body = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("diary-book-classic");
        assertThat(body).doesNotContain("diary-book-spring");
    }

    private Diary spiralDiary() {
        Diary diary = diary();
        diary.setNotebookType("SPIRAL");
        return diary;
    }

    /** 표지 아래에 노트 종류 한 줄. 고르지 않으면 일반 노트다. */
    @Test
    void newFormOffersBothNotebookTypesWithTheClassicOneChosen() throws Exception {
        String body = mockMvc.perform(get("/diaries/new")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("노트 종류");
        assertThat(body).contains("name=\"notebookType\"");
        assertThat(body).contains("value=\"CLASSIC\"").contains("value=\"SPIRAL\"");
        assertThat(body).contains("일반 노트").contains("스프링 노트");
        // 표지 고르는 자리 아래에 둔다
        assertThat(body.indexOf("diary-cover-style-picker"))
                .isLessThan(body.indexOf("diary-notebook-picker"));
        // 표지와 노트는 서로 다른 이름의 값이라 따로 고른다
        assertThat(body).contains("name=\"coverStyle\"");
        assertThat(checkedNotebookType(body)).isEqualTo("CLASSIC");
    }

    @Test
    void editFormOpensWithTheStoredNotebookTypeChosen() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        Diary existing = diary();
        existing.setNotebookType("SPIRAL");
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(existing);

        String body = mockMvc.perform(get("/diaries/10/edit")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(checkedNotebookType(body)).isEqualTo("SPIRAL");
    }

    @Test
    void creatingADiaryStoresTheChosenNotebookTypeAlongsideTheCover() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        mockMvc.perform(multipart("/diaries")
                        .param("title", "여름 제주 여행")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-05")
                        .param("coverStyle", "LEATHER_BLACK")
                        .param("notebookType", "SPIRAL")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryService).create(eq(7L), captor.capture());
        // 두 값은 서로를 덮지 않는다
        assertThat(captor.getValue().getCoverStyle()).isEqualTo("LEATHER_BLACK");
        assertThat(captor.getValue().getNotebookType()).isEqualTo("SPIRAL");
    }

    @Test
    void updatingADiaryStoresTheChosenNotebookType() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        Diary existing = diary();
        existing.setNotebookType("CLASSIC");
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(existing);
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-02")));

        mockMvc.perform(multipart("/diaries/10/update")
                        .param("title", "여름 제주 여행")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-05")
                        .param("notebookType", "SPIRAL")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryService).update(eq(10L), eq(7L), captor.capture());
        assertThat(captor.getValue().getNotebookType()).isEqualTo("SPIRAL");
    }

    /** 기간을 잘못 넣어 폼이 다시 열려도 고른 종류는 그대로 있어야 한다. */
    @Test
    void aRejectedFormComesBackWithTheChosenNotebookTypeStillSelected() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.create(eq(7L), any(Diary.class)))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "여행 종료일은 시작일 이후여야 합니다."));

        String body = mockMvc.perform(multipart("/diaries")
                        .param("title", "여름 제주 여행")
                        .param("startDate", "2026-08-05")
                        .param("endDate", "2026-08-01")
                        .param("notebookType", "SPIRAL")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/new"))
                .andReturn().getResponse().getContentAsString();

        assertThat(checkedNotebookType(body)).isEqualTo("SPIRAL");
    }

    /** 노트 종류 고르는 자리 안에서 실제로 체크된 값 하나를 읽는다. */
    private String checkedNotebookType(String body) {
        int start = body.indexOf("diary-notebook-picker");
        assertThat(start).as("노트 종류 고르는 자리").isNotNegative();
        String picker = body.substring(start);
        for (String code : new String[]{"CLASSIC", "SPIRAL"}) {
            if (picker.contains("value=\"" + code + "\" checked")) {
                return code;
            }
        }
        return null;
    }

    @Test
    void readModeMovesBySpreadAndEditModeMovesByOnePage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(
                page(1, "2026-08-01"), page(2, "2026-08-02"),
                page(3, "2026-08-03"), page(4, "2026-08-04")));

        // 읽기 모드: 두 장 펼침 그대로, 이동도 펼침 단위
        String readBody = mockMvc.perform(get("/diaries/10").param("spread", "1")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(readBody).contains("diary-book-spread");
        assertThat(readBody).doesNotContain("diary-book-single");
        assertThat(readBody).contains("href=\"/diaries/10?spread=0\"");
        assertThat(readBody).contains("data-flip=\"previous\"");
        // 편집하기는 지금 펼친 왼쪽 장부터 연다
        assertThat(readBody).contains("spread=1&amp;edit=true&amp;page=3");

        // 편집 모드: 한 장만, 이동도 한 장씩
        String editBody = mockMvc.perform(get("/diaries/10")
                        .param("spread", "1").param("edit", "true").param("page", "3")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("editPageNumber", 3))
                .andReturn().getResponse().getContentAsString();
        assertThat(editBody).contains("diary-book-single");
        assertThat(editBody).doesNotContain("diary-book-spread");
        // 지금 몇 번째 장인지는 아래 이동 줄 가운데에서만 알려 준다
        assertThat(editBody).contains("diary-spread-nav is-compact");
        assertThat(editBody).contains("3 / 4");
        assertThat(editBody).contains("class=\"diary-spread-arrow\"");
        assertThat(editBody).doesNotContain("← 이전 페이지");
        assertThat(editBody).contains("page=2");
        assertThat(editBody).contains("page=4");
        // 편집 모드 이동에는 읽기 모드의 책장 넘김 연출을 붙이지 않는다
        assertThat(editBody).doesNotContain("data-flip=");
    }

    @Test
    void editModeClampsAnUnknownPageAndDisablesEndOfBookMoves() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(
                page(1, "2026-08-01"), page(2, "2026-08-02"), page(3, "2026-08-03")));

        // 목록에 없는 page 값은 지금 펼친 장으로 되돌린다 (요청 값을 그대로 믿지 않는다)
        mockMvc.perform(get("/diaries/10").param("edit", "true").param("page", "99")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("editPageNumber", 1))
                .andExpect(model().attribute("hasPreviousPage", false))
                .andExpect(model().attribute("hasNextPage", true));

        // 마지막 장에서는 다음 이동이 막힌다
        mockMvc.perform(get("/diaries/10").param("spread", "1")
                        .param("edit", "true").param("page", "3")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasPreviousPage", true))
                .andExpect(model().attribute("hasNextPage", false))
                // 3페이지는 읽기 모드 두 번째 펼침에 들어 있다
                .andExpect(model().attribute("editSpread", 1));
    }

    @Test
    void editingAnotherUsersDiaryIsStillNotFound() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "다이어리를 찾을 수 없습니다."));

        mockMvc.perform(get("/diaries/10").param("edit", "true").param("page", "1")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void emojiPickerStartsClosed() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));

        String body = mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("id=\"diary-emoji-popover\"");
        assertThat(body).contains("aria-expanded=\"false\"");
        assertThat(body).contains("aria-controls=\"diary-emoji-popover\"");
        // 열림/닫힘은 hidden 속성으로 관리한다
        assertThat(body).containsPattern("id=\"diary-emoji-popover\"[^>]*hidden");
        assertThat(body).contains("id=\"diary-emoji-tabs\"");
        assertThat(body).contains("/js/diary-emoji-data.js");
    }

    @Test
    void fontPickerOffersOnlyTheDiaryFonts() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));

        String body = mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 이름 미리보기용 커스텀 드롭다운과 다이어리 전용 글꼴 CSS
        assertThat(body).contains("id=\"diary-font-trigger\"");
        assertThat(body).contains("id=\"diary-font-list\"");
        assertThat(body).containsPattern("id=\"diary-font-list\"[^>]*hidden");
        assertThat(body).contains("aria-haspopup=\"listbox\"");
        assertThat(body).contains("aria-expanded=\"false\"");
        assertThat(body).contains("aria-controls=\"diary-font-list\"");
        assertThat(body).contains("role=\"listbox\"");
        // 목록을 채우는 스크립트가 함께 실려야 한다
        assertThat(body).contains("/js/diary-editor.js");
        assertThat(body).contains("/css/diary-fonts.css");
        // 예전 글꼴 select 와 옵션은 더 이상 없다
        assertThat(body).doesNotContain("id=\"diary-font\"");
        assertThat(body).doesNotContain("Pretendard");
        assertThat(body).doesNotContain("Noto Sans KR");
        assertThat(body).doesNotContain("나눔휴먼");
    }

    @Test
    void savingPageContentGoesThroughTheContentOnlyPath() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        mockMvc.perform(post("/diaries/10/pages/3/content")
                        .param("content", "<p>오늘의 기록</p>")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNoContent());

        verify(diaryPageService).updateContent(10L, 3L, 7L, "<p>오늘의 기록</p>");
        // 본문 저장은 날짜/순서/배경을 바꾸는 경로를 쓰지 않는다
        verify(diaryPageService, org.mockito.Mockito.never())
                .update(any(), any(), any(), any(DiaryPage.class));
    }

    @Test
    void savingContentOfAnotherUsersPageIsNotFound() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryPageService.updateContent(eq(10L), eq(3L), eq(7L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "페이지를 찾을 수 없습니다."));

        mockMvc.perform(post("/diaries/10/pages/3/content")
                        .param("content", "<p>남의 일기</p>")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void tooLongContentReturnsAMessageInsteadOfAnErrorPage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryPageService.updateContent(eq(10L), eq(3L), eq(7L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "본문이 너무 깁니다."));

        mockMvc.perform(post("/diaries/10/pages/3/content")
                        .param("content", "<p>...</p>")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("본문이 너무 깁니다.")));
    }

    @Test
    void addPhotoStoresTheUploadedFileAsAPhotoElement() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(fileUploadService.saveFile(any(), eq("diary-pages")))
                .thenReturn("/uploads/diary-pages/new.jpg");

        mockMvc.perform(multipart("/diaries/10/pages/3/elements/photo")
                        .file(new MockMultipartFile("image", "trip.jpg", "image/jpeg", new byte[]{1}))
                        .param("spread", "1")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries/10?spread=1&edit=true"));

        ArgumentCaptor<DiaryElement> captor = ArgumentCaptor.forClass(DiaryElement.class);
        verify(diaryElementService).create(eq(10L), eq(3L), eq(7L), captor.capture());
        assertThat(captor.getValue().getElementType()).isEqualTo("PHOTO");
        assertThat(captor.getValue().getImageUrl()).isEqualTo("/uploads/diary-pages/new.jpg");
        assertThat(captor.getValue().getTextContent()).isNull();
    }

    @Test
    void photoWithoutFileShowsTheErrorAndDoesNotSaveAnything() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        mockMvc.perform(multipart("/diaries/10/pages/3/elements/photo")
                        .param("spread", "0")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(redirectedUrl("/diaries/10?spread=0&edit=true"))
                .andExpect(flash().attribute("diaryPageError", "사진을 선택해 주세요."));

        verify(fileUploadService, org.mockito.Mockito.never()).saveFile(any(), any());
        verify(diaryElementService, org.mockito.Mockito.never())
                .create(any(), any(), any(), any());
    }

    @Test
    void pageSettingsCarryThePaperColorToTheService() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryPageService.getPage(10L, 3L, 7L)).thenReturn(page(2, "2026-08-02"));

        mockMvc.perform(post("/diaries/10/pages/3/update")
                        .param("pageDate", "2026-08-03")
                        .param("backgroundType", "LINED")
                        .param("paperColor", "#FFF9E8")
                        .param("spread", "1")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<DiaryPage> captor = ArgumentCaptor.forClass(DiaryPage.class);
        verify(diaryPageService).update(eq(10L), eq(3L), eq(7L), captor.capture());
        assertThat(captor.getValue().getPaperColor()).isEqualTo("#FFF9E8");
        // 배경 무늬는 종이색과 따로 저장된다
        assertThat(captor.getValue().getBackgroundType()).isEqualTo("LINED");
    }

    @Test
    void paperColorIsPickedInThePageSettingsAndShownOnBothModes() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        DiaryPage left = page(1, "2026-08-01");
        left.setPaperColor("#FFF9E8");
        DiaryPage right = page(2, "2026-08-02");
        right.setPaperColor("#E8F2F8");
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(left, right));

        String editBody = mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 색 고르기는 기존 페이지 설정 안에 있다 (새 툴바를 만들지 않는다)
        assertThat(editBody.indexOf("diary-page-settings"))
                .isLessThan(editBody.indexOf("class=\"diary-paper-color\""));
        assertThat(editBody).contains("name=\"paperColor\"");
        assertThat(editBody).contains("data-paper-color=\"#FFF9E8\"");
        // 예전 기본 종이색은 아이보리 프리셋으로 남아 있다
        assertThat(editBody).contains("data-paper-color=\"#FDFAF3\"");
        // 설정은 작은 outline 버튼이다 (삭제는 그대로 낮은 위계)
        assertThat(editBody).contains("diary-page-settings-toggle");
        assertThat(editBody).contains("diary-page-action-icon");
        assertThat(editBody).contains("diary-paper-color-picker");
        assertThat(editBody).contains("/js/diary-paper-color.js");
        // 저장된 색은 변수만 덮어써서 무늬/질감 위에 얹는다
        assertThat(editBody).contains("--diary-paper-color:#FFF9E8");

        String readBody = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 좌/우 종이색은 서로 독립이다
        assertThat(readBody).contains("--diary-paper-color:#FFF9E8");
        assertThat(readBody).contains("--diary-paper-color:#E8F2F8");
        // 읽기 모드에는 색 고르기 UI 가 없다
        assertThat(readBody).doesNotContain("class=\"diary-paper-color\"");
        assertThat(readBody).doesNotContain("name=\"paperColor\"");
        assertThat(readBody).doesNotContain("diary-paper-swatch");
    }

    /** 색을 고르지 않은 페이지는 기본 종이색을 그대로 쓴다. */
    @Test
    void pageWithoutAPaperColorKeepsTheDefaultPaper() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));

        String body = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("--diary-paper-color:");
    }

    @Test
    void pageHeaderIsSavedThroughItsOwnEndpoint() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        mockMvc.perform(post("/diaries/10/pages/3/header")
                        .param("pageHeader", "제주 여행 첫째 날")
                        .param("pageHeaderFont", "mitmi")
                        .param("pageHeaderBold", "true")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNoContent());

        // 내용/글꼴/굵기를 한 번에 저장한다
        verify(diaryPageService).updatePageHeader(10L, 3L, 7L, "제주 여행 첫째 날", "mitmi", true);
        // 본문 저장 경로는 건드리지 않는다
        verify(diaryPageService, org.mockito.Mockito.never())
                .updateContent(any(), any(), any(), any());
    }

    /** 글꼴/굵기를 보내지 않던 요청도 그대로 동작한다. (기본값) */
    @Test
    void pageHeaderWithoutStyleParametersUsesTheDefaults() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        mockMvc.perform(post("/diaries/10/pages/3/header")
                        .param("pageHeader", "메모")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNoContent());

        verify(diaryPageService).updatePageHeader(10L, 3L, 7L, "메모", null, false);
    }

    @Test
    void pageHeaderOfAnotherUsersPageIsNotFound() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryPageService.updatePageHeader(eq(10L), eq(3L), eq(7L), any(), any(), anyBoolean()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "페이지를 찾을 수 없습니다."));

        mockMvc.perform(post("/diaries/10/pages/3/header")
                        .param("pageHeader", "남의 메모")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void tooLongPageHeaderReturnsAMessageInsteadOfAnErrorPage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryPageService.updatePageHeader(eq(10L), eq(3L), eq(7L), any(), any(), anyBoolean()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "한 줄 메모는 100자 이하로 입력해 주세요."));

        mockMvc.perform(post("/diaries/10/pages/3/header")
                        .param("pageHeader", "가".repeat(101))
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("한 줄 메모는 100자 이하로 입력해 주세요.")));
    }

    @Test
    void pageHeaderIsEditableInEditModeAndReadOnlyTextInReadMode() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        DiaryPage first = page(1, "2026-08-01");
        first.setPageHeader("제주 여행 첫째 날");
        DiaryPage second = page(2, "2026-08-02");
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(first, second));

        String editBody = mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(editBody).contains("diary-sheet-header-input");
        assertThat(editBody).contains("maxlength=\"100\"");
        assertThat(editBody).contains("오늘의 한 줄...");
        assertThat(editBody).contains("/diaries/10/pages/1/header");
        assertThat(editBody).contains("제주 여행 첫째 날");
        // 한 줄 메모 전용 글꼴/굵기 컨트롤은 상단 툴바에 있다
        assertThat(editBody).contains("diary-header-font-trigger");
        assertThat(editBody).contains("diary-header-bold");

        String readBody = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 읽기 모드는 입력 요소 없이 적어 둔 메모만 보여준다
        assertThat(readBody).contains("diary-sheet-header");
        assertThat(readBody).contains("제주 여행 첫째 날");
        assertThat(readBody).doesNotContain("diary-sheet-header-input");
        assertThat(readBody).doesNotContain("오늘의 한 줄...");
        assertThat(readBody).doesNotContain("diary-header-font-trigger");
        assertThat(readBody).doesNotContain("diary-header-bold");
        // 비어 있는 페이지는 아무것도 두지 않는다 (날짜만 남는다)
        assertThat(readBody.split("diary-sheet-header", -1).length - 1).isEqualTo(1);
    }

    @Test
    void pageHeaderKeepsItsFontAndBoldInBothModes() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        DiaryPage first = page(1, "2026-08-01");
        first.setPageHeader("Day 1");
        first.setPageHeaderFont("mitmi");
        first.setPageHeaderBold(true);
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(first));

        String readBody = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 본문 글꼴과 같은 클래스를 그대로 쓴다
        assertThat(readBody).contains("diary-font-mitmi");
        assertThat(readBody).contains("is-bold");
        // 날짜에는 사용자 글꼴/굵기를 적용하지 않는다
        assertThat(readBody).contains("class=\"diary-sheet-date\"");

        String editBody = mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(editBody).contains("data-header-font=\"mitmi\"");
        assertThat(editBody).contains("data-header-bold=\"true\"");
        assertThat(editBody).contains("diary-font-mitmi");
    }

    /**
     * 자유배치 좌표의 기준은 읽기/편집 모두 종이 전체다.
     * 캔버스가 본문 영역(.diary-sheet-body) 안에 있으면 머리말 높이만큼 기준이 밀려
     * 같은 상대 좌표가 두 모드에서 다른 자리를 가리킨다.
     */
    @Test
    void canvasCoversTheWholeSheetInBothModes() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        when(diaryElementService.getElements(10L, 1L, 7L)).thenReturn(List.of(
                stickerElement(200L, "/images/diary/stickers/emotion/heart.svg")));

        for (boolean edit : new boolean[]{false, true}) {
            String body = mockMvc.perform(get("/diaries/10")
                            .param("edit", String.valueOf(edit))
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    userDetails, null, List.of()))))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // 캔버스는 본문 층/페이지 번호 다음(종이 직속)에 온다
            assertThat(body.indexOf("diary-writing-layer"))
                    .as("편집 모드=" + edit)
                    .isLessThan(body.indexOf("class=\"diary-canvas\""));
            assertThat(body.indexOf("diary-sheet-number"))
                    .as("편집 모드=" + edit)
                    .isLessThan(body.indexOf("class=\"diary-canvas\""));
            // 저장된 상대 좌표는 두 모드에서 같은 % 로 그려진다
            assertThat(body).as("편집 모드=" + edit).contains("left:41.");
        }
    }

    @Test
    void photoButtonMovedFromThePaperToTheTopToolbar() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));

        String body = mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 기존 PHOTO 생성 폼은 그대로 쓰고 자리만 상단 툴바로 옮겼다
        assertThat(body).contains("diary-photo-add");
        // 아이콘은 이모지 문자가 아니라 inline SVG 이고 의미는 라벨로 남긴다
        assertThat(body).contains("diary-toolbar-icon");
        assertThat(body).contains("aria-label=\"일반 사진 추가\"");
        assertThat(body).contains("/diaries/10/pages/1/elements/photo");
        assertThat(body).contains("diary-photo-input");
        assertThat(body.indexOf("diary-photo-add"))
                .isLessThan(body.indexOf("diary-book-single"));
        // 종이 안에는 사진 버튼이 남아 있지 않다
        assertThat(body).doesNotContain("diary-sheet-toolbar");
        assertThat(body.substring(body.indexOf("diary-sheet-single")))
                .doesNotContain("diary-photo-add");
    }

    @Test
    void stickerIsCreatedFromTheServerSideCatalogOnly() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.getElements(10L, 3L, 7L)).thenReturn(List.of());
        when(diaryElementService.create(eq(10L), eq(3L), eq(7L), any()))
                .thenReturn(stickerElement(200L, "/images/diary/stickers/travel/airplane.svg"));

        mockMvc.perform(post("/diaries/10/pages/3/elements/sticker")
                        .param("sticker", "airplane")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/images/diary/stickers/travel/airplane.svg")))
                .andExpect(content().string(containsString("/sticker/delete")));

        ArgumentCaptor<DiaryElement> captor = ArgumentCaptor.forClass(DiaryElement.class);
        verify(diaryElementService).create(eq(10L), eq(3L), eq(7L), captor.capture());
        DiaryElement saved = captor.getValue();
        assertThat(saved.getElementType()).isEqualTo("STICKER");
        // 경로는 요청 값이 아니라 서버 목록에서 나온다
        assertThat(saved.getImageUrl()).isEqualTo("/images/diary/stickers/travel/airplane.svg");
        assertThat(saved.getTextContent()).isNull();
        // 종이 가운데 부근의 작은 기본 크기
        assertThat(saved.getWidth()).isEqualByComparingTo("0.18000");
        assertThat(saved.getHeight()).isEqualByComparingTo("0.18000");
        assertThat(saved.getPositionX()).isEqualByComparingTo("0.41000");
    }

    /** 마스킹테이프는 처음부터 띠 모양으로 놓이고, 화면도 그렇게 다루도록 함께 알려 준다. */
    @Test
    void maskingTapeIsPlacedAsAStrip() throws Exception {
        String tapeUrl = "/images/diary/stickers/masking-tape/tape-cloud-sky.svg";
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.getElements(10L, 3L, 7L)).thenReturn(List.of());
        when(diaryElementService.create(eq(10L), eq(3L), eq(7L), any()))
                .thenReturn(stickerElement(201L, tapeUrl));

        String body = mockMvc.perform(post("/diaries/10/pages/3/elements/sticker")
                        .param("sticker", "tape-cloud-sky")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ArgumentCaptor<DiaryElement> captor = ArgumentCaptor.forClass(DiaryElement.class);
        verify(diaryElementService).create(eq(10L), eq(3L), eq(7L), captor.capture());
        DiaryElement saved = captor.getValue();
        // 유형은 그대로 STICKER 이고 크기만 띠 모양이다
        assertThat(saved.getElementType()).isEqualTo("STICKER");
        assertThat(saved.getWidth()).isEqualByComparingTo("0.46000");
        assertThat(saved.getHeight()).isEqualByComparingTo("0.09000");
        // 화면이 곧바로 마스킹테이프로 알아보게 함께 내려 준다
        assertThat(body).contains("\"maskingTape\":true");
        // 마스킹테이프는 모두 되풀이형이라 조각 경로도 함께 온다
        assertThat(body).contains("repeat/tape-cloud-sky-center.svg");
    }

    /** 되풀이형 테이프는 붙는 즉시(새로고침 전에도) 같은 조각으로 그려지도록 함께 내려 준다. */
    @Test
    void repeatingTapeCreateResponseCarriesItsPieces() throws Exception {
        String tapeUrl = "/images/diary/stickers/masking-tape/tape-cat-cream.svg";
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.getElements(10L, 3L, 7L)).thenReturn(List.of());
        when(diaryElementService.create(eq(10L), eq(3L), eq(7L), any()))
                .thenReturn(stickerElement(202L, tapeUrl));

        String body = mockMvc.perform(post("/diaries/10/pages/3/elements/sticker")
                        .param("sticker", "tape-cat-cream")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("repeat/tape-cat-cream-left.svg");
        assertThat(body).contains("repeat/tape-cat-cream-center.svg");
        assertThat(body).contains("repeat/tape-cat-cream-right.svg");
        // DB 에 남는 그림 경로는 지금까지처럼 완성형 하나뿐이다
        ArgumentCaptor<DiaryElement> captor = ArgumentCaptor.forClass(DiaryElement.class);
        verify(diaryElementService).create(eq(10L), eq(3L), eq(7L), captor.capture());
        assertThat(captor.getValue().getImageUrl()).isEqualTo(tapeUrl);
    }

    @Test
    void arbitraryStickerImageUrlsAreRejected() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        mockMvc.perform(post("/diaries/10/pages/3/elements/sticker")
                        .param("sticker", "https://evil.example.com/tracker.svg")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("알 수 없는 스티커입니다.")));

        verify(diaryElementService, org.mockito.Mockito.never())
                .create(any(), any(), any(), any());
    }

    @Test
    void stickerOnAnotherUsersPageIsNotFound() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.getElements(99L, 3L, 7L)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "다이어리를 찾을 수 없습니다."));

        mockMvc.perform(post("/diaries/99/pages/3/elements/sticker")
                        .param("sticker", "airplane")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void stickerDeleteRemovesTheRowWithoutTouchingTheSharedAsset() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.getElement(10L, 3L, 200L, 7L))
                .thenReturn(stickerElement(200L, "/images/diary/stickers/travel/airplane.svg"));

        // 꾸미던 화면을 새로 고치지 않도록 리다이렉트 없이 204 로 끝낸다
        mockMvc.perform(post("/diaries/10/pages/3/elements/200/sticker/delete")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNoContent());

        verify(diaryElementService).delete(10L, 3L, 200L, 7L);
        // 공용 asset 이므로 업로드 파일 정리 경로를 타지 않는다
        verify(fileUploadService, org.mockito.Mockito.never()).saveFile(any(), any());

        // 사진은 스티커 삭제 경로로 지울 수 없다
        when(diaryElementService.getElement(10L, 3L, 201L, 7L))
                .thenReturn(photoElement(201L, "/uploads/diary-pages/trip.jpg"));

        mockMvc.perform(post("/diaries/10/pages/3/elements/201/sticker/delete")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("스티커 요소가 아닙니다.")));
        verify(diaryElementService, org.mockito.Mockito.never()).delete(10L, 3L, 201L, 7L);
    }

    /** 남의 요소는 예전처럼 정보를 드러내지 않는 404 로 끝난다. */
    @Test
    void deletingAnotherUsersElementIsStillNotFound() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.getElement(10L, 3L, 999L, 7L)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "요소를 찾을 수 없습니다."));

        mockMvc.perform(post("/diaries/10/pages/3/elements/999/sticker/delete")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNotFound());

        verify(diaryElementService, org.mockito.Mockito.never()).delete(any(), any(), any(), any());
    }

    /**
     * 되풀이형 테이프는 저장된 imageUrl 하나만으로 조각을 다시 찾아 화면에 실어 준다.
     * (DB 에는 조각 경로가 없고, 읽기/편집 화면이 같은 값을 쓴다)
     */
    @Test
    void savedRepeatingTapeGetsItsPiecesBackOnBothModes() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        when(diaryElementService.getElements(10L, 1L, 7L)).thenReturn(List.of(stickerElement(
                200L, "/images/diary/stickers/masking-tape/tape-cat-cream.svg")));

        for (boolean edit : new boolean[]{true, false}) {
            String body = mockMvc.perform(get("/diaries/10").param("edit", String.valueOf(edit))
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    userDetails, null, List.of()))))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            String sticker = body.substring(body.indexOf("diary-canvas-sticker"));
            assertThat(sticker).contains(
                    "data-tape-left=\"/images/diary/stickers/masking-tape/repeat/tape-cat-cream-left.svg\"");
            assertThat(sticker).contains(
                    "data-tape-center=\"/images/diary/stickers/masking-tape/repeat/tape-cat-cream-center.svg\"");
            assertThat(sticker).contains(
                    "data-tape-right=\"/images/diary/stickers/masking-tape/repeat/tape-cat-cream-right.svg\"");
            // 조각을 이어 붙이는 일은 읽기 화면에서도 같은 스크립트가 맡는다
            assertThat(body).contains("/js/diary-tape-repeat.js");
        }
    }

    /** 마스킹테이프가 아닌 스티커는 지금까지처럼 완성형 그림 하나로 그린다. */
    @Test
    void ordinaryStickerHasNoRepeatPieces() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        when(diaryElementService.getElements(10L, 1L, 7L)).thenReturn(List.of(stickerElement(
                200L, "/images/diary/stickers/emotion/heart.svg")));

        String body = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body.substring(body.indexOf("diary-canvas-sticker")))
                .doesNotContain("data-tape-center");
    }

    /** 이미 붙여 둔 마스킹테이프도 저장된 경로만으로 다시 알아본다. (EDIT/READ 모두) */
    @Test
    void savedMaskingTapeKeepsItsKindOnBothModes() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        when(diaryElementService.getElements(10L, 1L, 7L)).thenReturn(List.of(stickerElement(
                200L, "/images/diary/stickers/masking-tape/tape-cloud-sky.svg")));

        for (boolean edit : new boolean[]{true, false}) {
            String body = mockMvc.perform(get("/diaries/10").param("edit", String.valueOf(edit))
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    userDetails, null, List.of()))))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            String sticker = body.substring(body.indexOf("diary-canvas-sticker"));
            assertThat(sticker).contains("data-sticker-kind=\"masking-tape\"");
        }
    }

    @Test
    void editModeOffersTheStickerPickerAndReadModeShowsSavedStickersOnly() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        when(diaryElementService.getElements(10L, 1L, 7L)).thenReturn(List.of(
                stickerElement(200L, "/images/diary/stickers/emotion/heart.svg")));

        String editBody = mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(editBody).contains("diary-sticker-button");
        assertThat(editBody).contains("data-sticker-id=\"airplane\"");
        // '최근' 은 manifest 분류가 아니라 브라우저에만 남는 화면용 탭이다
        assertThat(editBody).contains("data-sticker-category=\"recent\"");
        assertThat(editBody).contains("id=\"diary-sticker-grid-recent\"");
        assertThat(editBody).contains("아직 사용한 스티커가 없어요.");
        // 가로로 긴 마스킹테이프는 picker 에서 넓게 보이도록 성격을 함께 실어 준다
        assertThat(editBody).containsPattern(
                "data-sticker-id=\"tape-cloud-sky\"\\s+data-sticker-kind=\"masking-tape\"");
        // 마스킹테이프 묶음 안에서만 일반/투명을 다시 고를 수 있다
        assertThat(editBody).contains("diary-sticker-subfilter");
        assertThat(editBody).contains("data-tape-type=\"TRANSLUCENT\"");
        assertThat(editBody).contains("data-tape-type=\"CLEAR\"");
        assertThat(editBody).containsPattern(
                "data-sticker-id=\"tape-clear-star\"[^>]*data-tape-type=\"TRANSLUCENT\"");
        assertThat(editBody).containsPattern(
                "data-sticker-id=\"tape-glass-star\"[^>]*data-tape-type=\"CLEAR\"");
        assertThat(editBody).containsPattern(
                "data-sticker-id=\"tape-cloud-sky\"[^>]*data-tape-type=\"NORMAL\"");
        // 일반 스티커에는 성격 표시가 붙지 않는다
        assertThat(editBody).doesNotContain("data-sticker-id=\"airplane\" data-sticker-kind");
        assertThat(editBody).contains("/images/diary/stickers/emotion/heart.svg");
        // 스티커도 사진과 같은 자유배치 조작을 쓴다
        assertThat(editBody).contains("diary-canvas-sticker");
        assertThat(editBody).contains("/diaries/10/pages/1/elements/200/position");
        assertThat(editBody).contains("/diaries/10/pages/1/elements/200/layer");
        assertThat(editBody).contains("/diaries/10/pages/1/elements/200/sticker/delete");
        assertThat(editBody).contains("/js/diary-sticker-picker.js");
        // 삭제/떼기는 이미지 위가 아니라 뒤로·앞으로와 같은 액션 줄 안에 있다
        String actions = editBody.substring(editBody.indexOf("diary-layer-actions"));
        actions = actions.substring(0, actions.indexOf("</div>", actions.indexOf("sticker/delete")));
        assertThat(actions).contains("data-layer-direction=\"BACKWARD\"")
                .contains("data-layer-direction=\"FORWARD\"")
                .contains("sticker/delete");
        // 떼기는 폼 전송(=화면 새로고침)이 아니라 비동기 요청으로 처리한다
        assertThat(actions).contains("data-delete-url").contains("data-delete-confirm");
        assertThat(actions).doesNotContain("<form").doesNotContain("type=\"submit\"");

        String readBody = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 읽기 모드에는 스티커 그림만 남고 편집 UI 는 없다
        assertThat(readBody).contains("diary-canvas-sticker");
        assertThat(readBody).contains("/images/diary/stickers/emotion/heart.svg");
        assertThat(readBody).doesNotContain("diary-sticker-button");
        assertThat(readBody).doesNotContain("sticker/delete");
        assertThat(readBody).doesNotContain("diary-resize-handle");
    }

    @Test
    void photoDeleteRemovesTheRowAndRejectsTextElements() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.getElement(10L, 3L, 101L, 7L))
                .thenReturn(photoElement(101L, "/uploads/diary-pages/old.jpg"));

        // 사진도 화면 이동 없이 204 로 끝난다 (업로드 파일 정리는 서버가 그대로 한다)
        mockMvc.perform(post("/diaries/10/pages/3/elements/101/photo/delete")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNoContent());

        verify(diaryElementService).delete(10L, 3L, 101L, 7L);

        // 글 요소는 사진 삭제 경로로 지울 수 없다
        when(diaryElementService.getElement(10L, 3L, 100L, 7L))
                .thenReturn(textElement(100L, "기록"));

        mockMvc.perform(post("/diaries/10/pages/3/elements/100/photo/delete")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("사진 요소가 아닙니다.")));
        verify(diaryElementService, org.mockito.Mockito.never()).delete(10L, 3L, 100L, 7L);
    }

    @Test
    void newFormShowsTheDiaryCreationFields() throws Exception {
        mockMvc.perform(get("/diaries/new")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/new"))
                .andExpect(content().string(containsString("새 여행일기")))
                .andExpect(content().string(containsString("name=\"title\"")))
                .andExpect(content().string(containsString("name=\"startDate\"")))
                .andExpect(content().string(containsString("name=\"endDate\"")))
                .andExpect(content().string(containsString("name=\"coverImage\"")))
                .andExpect(content().string(containsString("maxlength=\"150\"")));
    }

    @Test
    void createSavesWithTheCurrentUserAndRedirects() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.create(eq(7L), any(Diary.class))).thenReturn(new Diary());

        mockMvc.perform(multipart("/diaries")
                        .param("title", "여름 제주 여행")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-05")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries"))
                .andExpect(flash().attribute("diaryMessage", "여행일기가 만들어졌습니다."));

        ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryService).create(eq(7L), captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("여름 제주 여행");
        assertThat(captor.getValue().getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(captor.getValue().getEndDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        // 이미지를 고르지 않으면 표지는 비워 둔다 (목록에서 기본 표지 사용)
        assertThat(captor.getValue().getCoverImageUrl()).isNull();
        // 소유자는 요청 값이 아니라 로그인 사용자로 정해진다
        assertThat(captor.getValue().getUserId()).isNull();
    }

    /**
     * 라벨기로 붙인 글씨. 화면이 곧바로 그릴 수 있도록 글·글꼴·자리와 저장 주소를 함께 준다.
     * 자리와 크기는 요청 값이 아니라 서비스가 정한다.
     */
    @Test
    void attachingALabelComesBackWithItsTextFontAndAddresses() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        DiaryElement created = new DiaryElement();
        created.setId(100L);
        created.setElementType("TEXT");
        created.setTextContent("JEJU 2026");
        created.setTextFont("nanum-square");
        created.setPositionX(new java.math.BigDecimal("0.34000"));
        created.setPositionY(new java.math.BigDecimal("0.34000"));
        created.setWidth(new java.math.BigDecimal("0.32000"));
        created.setHeight(new java.math.BigDecimal("0.07000"));
        created.setRotation(new java.math.BigDecimal("0.00"));
        created.setZIndex(0);
        created.setTextColor("#C86B7C");
        when(diaryElementService.createLabel(10L, 3L, 7L, "JEJU 2026", "nanum-square", "#C86B7C"))
                .thenReturn(created);

        String body = mockMvc.perform(post("/diaries/10/pages/3/elements/label")
                        .param("text", "JEJU 2026")
                        .param("textFont", "nanum-square")
                        .param("textColor", "#C86B7C")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"textContent\":\"JEJU 2026\"");
        assertThat(body).contains("\"textFont\":\"nanum-square\"");
        assertThat(body).contains("\"fontClass\":\"diary-font-nanum-square\"");
        // 다음 UI 단계가 곧바로 색을 입힐 수 있도록 글자색도 함께 준다
        assertThat(body).contains("\"textColor\":\"#C86B7C\"");
        assertThat(body).contains("/diaries/10/pages/3/elements/100/label/delete");
        assertThat(body).contains("/diaries/10/pages/3/elements/100/position");
    }

    /** 문구·글꼴 검증은 서비스 한 곳에서 한다. 그 이유가 그대로 화면에 전해진다. */
    @Test
    void aRejectedLabelTellsWhy() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.createLabel(anyLong(), anyLong(), anyLong(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "내용을 입력해 주세요."));

        mockMvc.perform(post("/diaries/10/pages/3/elements/label")
                        .param("text", "   ")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("내용을 입력해 주세요.")));
    }

    /** 글씨는 파일을 갖지 않는다. 떼는 것은 DB 행뿐이다. */
    @Test
    void removingALabelNeverTouchesAnyFile() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        mockMvc.perform(post("/diaries/10/pages/3/elements/100/label/delete")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNoContent());

        verify(diaryElementService).deleteLabel(10L, 3L, 100L, 7L);
        // 사진 삭제와 달리 파일 저장소는 아예 부르지 않는다
        org.mockito.Mockito.verifyNoInteractions(fileUploadService);
    }

    /** 수정 화면도 작성 화면과 같은 표지 고르기를 쓴다. 기본 표지를 쓰는 다이어리다. */
    @Test
    void theEditFormOffersBothCoverKindsAndOpensOnThePresetOne() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryCoverService.findMyCover(10L, 7L)).thenReturn(java.util.Optional.empty());
        when(diaryCoverDesignService.getMyDesigns(7L))
                .thenReturn(List.of(coverDesign(5L, "제주 표지")));
        when(diaryCoverDesignElementService.getElementsByDesign(List.of(5L), 7L))
                .thenReturn(Map.of(5L, List.of()));

        String body = mockMvc.perform(get("/diaries/10/edit")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("기본 디자인").contains("내 디자인").contains("제주 표지");
        assertThat(between(body, "name=\"coverSelectionType\"", "data-cover-selection"))
                .contains("value=\"PRESET\"");
        // 커스텀 표지를 쓰지 않으므로 '현재 표지' 자리는 없다
        assertThat(body).doesNotContain("diary-cover-current");
        // 요소는 디자인마다 따로 묻지 않고 한 번에 읽는다
        verify(diaryCoverDesignElementService).getElementsByDesign(List.of(5L), 7L);
    }

    /**
     * 커스텀 표지를 쓰는 다이어리는 '내 디자인' 쪽이 열린 채로 시작한다.
     * 지금 표지가 어느 저장 디자인에서 왔는지는 역추적하지 않으므로
     * 저장 디자인 어느 것도 골라 두지 않는다. (고르지 않고 저장하면 지금 표지가 그대로다)
     */
    @Test
    void theEditFormOpensOnMyDesignsWithoutMarkingAnyOfThem() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        DiaryCover cover = new DiaryCover();
        cover.setId(3L);
        cover.setDiaryId(10L);
        cover.setBaseCoverStyle("LEATHER_DEEP_GREEN");
        when(diaryCoverService.findMyCover(10L, 7L)).thenReturn(java.util.Optional.of(cover));
        when(diaryCoverDesignService.getMyDesigns(7L))
                .thenReturn(List.of(coverDesign(5L, "제주 표지")));
        when(diaryCoverDesignElementService.getElementsByDesign(List.of(5L), 7L))
                .thenReturn(Map.of(5L, List.of()));

        String body = mockMvc.perform(get("/diaries/10/edit")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(between(body, "name=\"coverSelectionType\"", "data-cover-selection"))
                .contains("value=\"CUSTOM\"");
        // 내 디자인 쪽에는 저장 디자인 목록만 있다. 지금 표지를 따로 그리지 않는다
        assertThat(body).doesNotContain("diary-cover-current");
        assertThat(body).contains("제주 표지");
        // 고른 디자인이 없으므로 저장 디자인 어느 것도 골라져 있지 않다
        assertThat(body).contains("name=\"customCoverDesignId\" value=\"\"");
        assertThat(between(body, "data-cover-panel=\"CUSTOM\"", "data-cover-custom-hint"))
                .doesNotContain("checked");
        // 지금 표지의 요소는 그리지 않으므로 읽지도 않는다
        verify(diaryCoverService, org.mockito.Mockito.never()).getElements(anyLong(), anyLong());
    }

    /** PRESET → CUSTOM. 대표 이미지는 저장하지 않고 표지 교체만 한다. */
    @Test
    void updatingToACustomCoverAppliesTheDesignAndSkipsTheCoverImage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryCoverService.findMyCover(10L, 7L)).thenReturn(java.util.Optional.empty());
        when(diaryCoverDesignService.getMyDesign(5L, 7L)).thenReturn(coverDesign(5L, "제주 표지"));
        when(diaryCoverService.updateWithDesign(eq(10L), eq(7L), any(Diary.class), eq(5L)))
                .thenReturn(List.of());

        mockMvc.perform(multipart("/diaries/10/update")
                        .file(new MockMultipartFile("coverImage", "cover.jpg",
                                "image/jpeg", "x".getBytes()))
                        .param("title", "여름 제주 여행")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-05")
                        .param("coverSelectionType", "CUSTOM")
                        .param("customCoverDesignId", "5")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries"));

        ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryCoverService).updateWithDesign(eq(10L), eq(7L), captor.capture(), eq(5L));
        assertThat(captor.getValue().getCoverImageUrl()).isNull();
        assertThat(captor.getValue().getCoverStyle()).isEqualTo("LEATHER_DEEP_GREEN");
        verify(fileUploadService, org.mockito.Mockito.never()).saveFile(any(), anyString());
        verify(diaryService, org.mockito.Mockito.never()).update(anyLong(), anyLong(), any());
    }

    /** CUSTOM → 다른 CUSTOM. 쓰던 표지를 떼고 고른 디자인으로 갈아 끼운다. */
    @Test
    void swappingToAnotherCustomCoverReplacesTheOneInUse() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        DiaryCover cover = new DiaryCover();
        cover.setId(3L);
        when(diaryCoverService.findMyCover(10L, 7L)).thenReturn(java.util.Optional.of(cover));
        when(diaryCoverDesignService.getMyDesign(6L, 7L)).thenReturn(coverDesign(6L, "새 표지"));
        // 떼어 낸 표지에 공용 스티커만 있으면 지울 파일이 없다
        DiaryCoverElement sticker = new DiaryCoverElement();
        sticker.setElementType("STICKER");
        sticker.setImageUrl("/images/diary/stickers/travel/plane.svg");
        when(diaryCoverService.updateWithDesign(eq(10L), eq(7L), any(Diary.class), eq(6L)))
                .thenReturn(List.of(sticker));

        mockMvc.perform(multipart("/diaries/10/update")
                        .param("title", "여름 제주 여행")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-05")
                        .param("coverSelectionType", "CUSTOM")
                        .param("customCoverDesignId", "6")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection());

        // 교체는 한 번의 서비스 호출로 끝난다. (지우고 다시 입히는 순서는 서비스가 지킨다)
        verify(diaryCoverService).updateWithDesign(eq(10L), eq(7L), any(Diary.class), eq(6L));
        verify(diaryCoverService, org.mockito.Mockito.never())
                .updateWithPreset(anyLong(), anyLong(), any());
    }

    /** CUSTOM → PRESET. 표지를 떼고 고른 기본 표지를 저장한다. */
    @Test
    void goingBackToAPresetCoverRemovesTheCustomOne() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        DiaryCover cover = new DiaryCover();
        cover.setId(3L);
        when(diaryCoverService.findMyCover(10L, 7L)).thenReturn(java.util.Optional.of(cover));
        when(diaryCoverService.updateWithPreset(eq(10L), eq(7L), any(Diary.class)))
                .thenReturn(List.of());

        mockMvc.perform(multipart("/diaries/10/update")
                        .param("title", "여름 제주 여행")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-05")
                        .param("coverStyle", "HARDCOVER_NAVY")
                        .param("coverSelectionType", "PRESET")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryCoverService).updateWithPreset(eq(10L), eq(7L), captor.capture());
        assertThat(captor.getValue().getCoverStyle()).isEqualTo("HARDCOVER_NAVY");
        verify(diaryCoverService, org.mockito.Mockito.never())
                .updateWithDesign(anyLong(), anyLong(), any(), anyLong());
    }

    /** 내 디자인 쪽에서 아무것도 고르지 않고 저장하면 지금 표지가 그대로 남는다. */
    @Test
    void savingWithoutChoosingADesignKeepsTheCoverInUse() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        DiaryCover cover = new DiaryCover();
        cover.setId(3L);
        when(diaryCoverService.findMyCover(10L, 7L)).thenReturn(java.util.Optional.of(cover));

        mockMvc.perform(multipart("/diaries/10/update")
                        .param("title", "여름 제주 여행 2")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-05")
                        .param("coverSelectionType", "CUSTOM")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection());

        // 제목만 바뀌고 표지는 어느 쪽으로도 손대지 않는다
        verify(diaryService).update(eq(10L), eq(7L), any(Diary.class));
        verify(diaryCoverService, org.mockito.Mockito.never())
                .updateWithDesign(anyLong(), anyLong(), any(), anyLong());
        verify(diaryCoverService, org.mockito.Mockito.never())
                .updateWithPreset(anyLong(), anyLong(), any());
    }

    /**
     * 사진은 등록하는 자리가 모습을 정한다. 일반 사진과 폴라로이드가 고르개를 따로 갖는다.
     * (붙인 뒤에 모습을 바꾸는 길은 두지 않는다)
     */
    @Test
    void photosAreAddedFromTwoEntryPointsThatDecideTheirLook() throws Exception {
        String body = editPageBody();

        assertThat(body).contains("aria-label=\"일반 사진 추가\"")
                .contains("aria-label=\"폴라로이드 사진 추가\"");
        assertThat(body).contains("data-photo-style=\"FULL\"")
                .contains("data-photo-style=\"POLAROID\"");
        // 고른 자리의 값이 폼에 담겨 함께 전송된다
        assertThat(body).contains("name=\"photoStyle\"");
        // 두 자리 모두 여러 장을 고를 수 있다
        assertThat(countOf(body, "class=\"diary-photo-input\" name=\"image\" accept=\"image/*\""
                + " multiple")).isEqualTo(2);
        // 붙이는 주소는 예전 그대로다
        assertThat(countOf(body, "action=\"/diaries/10/pages/1/elements/photo\"")).isEqualTo(1);
    }

    /** 여러 장을 한 번에 붙일 수 있고, 모습은 고른 자리의 값으로 정해진다. */
    @Test
    void severalPhotosBecomeSeveralElementsWithTheLookOfTheirEntryPoint() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(fileUploadService.saveFile(any(), anyString()))
                .thenReturn("/uploads/diary-pages/a.jpg", "/uploads/diary-pages/b.jpg");
        when(diaryElementService.create(anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(new DiaryElement());

        mockMvc.perform(multipart("/diaries/10/pages/1/elements/photo")
                        .file(new MockMultipartFile("image", "a.jpg", "image/jpeg", "a".getBytes()))
                        .file(new MockMultipartFile("image", "b.jpg", "image/jpeg", "b".getBytes()))
                        .param("photoStyle", "FULL")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<DiaryElement> captor = ArgumentCaptor.forClass(DiaryElement.class);
        verify(diaryElementService, org.mockito.Mockito.times(2))
                .create(anyLong(), anyLong(), anyLong(), captor.capture());
        assertThat(captor.getAllValues()).extracting(DiaryElement::getPhotoStyle)
                .containsExactly("FULL", "FULL");
        // 첫 장은 예전처럼 기본 자리에 놓고, 함께 고른 나머지만 조금씩 어긋나게 둔다
        assertThat(captor.getAllValues().get(0).getPositionX()).isNull();
        assertThat(captor.getAllValues().get(1).getPositionX()).isNotNull();
    }

    /** 붙여 둔 사진은 저장된 모습 그대로 그려진다. 값이 없는 예전 사진은 폴라로이드다. */
    @Test
    void savedPhotosKeepTheLookTheyWereAddedWith() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        DiaryElement full = photoElement(100L, "/uploads/diary-pages/a.jpg");
        full.setPhotoStyle("FULL");
        DiaryElement old = photoElement(101L, "/uploads/diary-pages/b.jpg");
        when(diaryElementService.getElements(10L, 1L, 7L)).thenReturn(List.of(full, old));

        String body = mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("is-photo-full").contains("is-photo-polaroid");
    }

    /** 라벨기는 작성 폼에서 쓰는 글꼴 전부를 고를 수 있고, 글자색도 함께 고른다. */
    @Test
    void theLabelMakerOffersEveryDiaryFontAndAColour() throws Exception {
        String body = editPageBody();
        String panel = between(body, "data-decor-panel=\"text\"", "diary-sticker-status");

        // 목록은 서버(카탈로그)가 그린다. 화면에 글꼴을 적어 두지 않는다
        assertThat(countOf(panel, "data-label-font=")).isEqualTo(15);
        assertThat(panel).contains("diary-font-fromsol").contains("diary-font-chosun-gungsuh");
        // 글자색은 색 하나만 고른다
        assertThat(panel).contains("type=\"color\"").contains("data-label-color");
    }

    /** 저장된 글씨는 고른 색으로 그려지고, 색이 없으면 규칙의 기본 먹색으로 그려진다. */
    @Test
    void savedLabelsKeepTheirColourAndFallBackWhenThereIsNone() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        DiaryElement coloured = labelElement();
        coloured.setTextColor("#C86B7C");
        DiaryElement plain = labelElement();
        plain.setId(101L);
        when(diaryElementService.getElements(10L, 1L, 7L)).thenReturn(List.of(coloured, plain));

        String body = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("color:#C86B7C");
        // 색이 없는 글씨에는 색을 입히지 않는다 (한 번만 나온다)
        assertThat(countOf(body, "color:#")).isEqualTo(1);
    }

    /** 새 여행일기 폼에서 기본 표지와 내가 저장해 둔 디자인 중 하나를 고른다. */
    @Test
    void newFormOffersMySavedCoverDesignsBesideTheDefaultOnes() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        DiaryCoverDesign design = coverDesign(5L, "제주 표지");
        when(diaryCoverDesignService.getMyDesigns(7L)).thenReturn(List.of(design));
        when(diaryCoverDesignElementService.getElementsByDesign(List.of(5L), 7L))
                .thenReturn(Map.of(5L, List.of()));

        mockMvc.perform(get("/diaries/new")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("기본 디자인")))
                .andExpect(content().string(containsString("내 디자인")))
                .andExpect(content().string(containsString("제주 표지")))
                .andExpect(content().string(containsString("name=\"coverSelectionType\"")))
                .andExpect(content().string(containsString("name=\"customCoverDesignId\"")));

        // 요소는 디자인마다 따로 묻지 않고 한 번에 읽는다
        verify(diaryCoverDesignElementService).getElementsByDesign(List.of(5L), 7L);
    }

    /** 저장해 둔 디자인이 없으면 만들러 가는 길만 조용히 안내한다. */
    @Test
    void newFormWithoutAnyDesignPointsToTheCoverDesignShelf() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.getMyDesigns(7L)).thenReturn(List.of());

        mockMvc.perform(get("/diaries/new")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("아직 만든 표지 디자인이 없습니다.")))
                .andExpect(content().string(containsString("/diaries/cover-designs")));
    }

    /**
     * 내 디자인을 고르면 다이어리와 표지가 한 번에 만들어진다.
     * 이때 대표 이미지는 저장하지 않고, cover_style 만 디자인의 재질로 남겨 둔다.
     */
    @Test
    void creatingWithMyDesignAppliesTheCoverAndSkipsTheCoverImage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.getMyDesign(5L, 7L))
                .thenReturn(coverDesign(5L, "제주 표지"));
        when(diaryCoverService.createWithDesign(eq(7L), any(Diary.class), eq(5L)))
                .thenReturn(new Diary());

        mockMvc.perform(multipart("/diaries")
                        .file(new MockMultipartFile("coverImage", "cover.jpg",
                                "image/jpeg", "x".getBytes()))
                        .param("title", "여름 제주 여행")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-05")
                        .param("coverSelectionType", "CUSTOM")
                        .param("customCoverDesignId", "5")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diaries"));

        ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryCoverService).createWithDesign(eq(7L), captor.capture(), eq(5L));
        // 표지에 들어갈 사진은 디자인이 들고 있으므로 대표 이미지는 저장조차 하지 않는다
        verify(fileUploadService, org.mockito.Mockito.never()).saveFile(any(), anyString());
        assertThat(captor.getValue().getCoverImageUrl()).isNull();
        // 커스텀 표지를 지웠을 때 돌아갈 자리
        assertThat(captor.getValue().getCoverStyle()).isEqualTo("LEATHER_DEEP_GREEN");
        // 커스텀 표지를 고른 요청은 기본 생성 길을 타지 않는다
        verify(diaryService, org.mockito.Mockito.never()).create(anyLong(), any(Diary.class));
    }

    /** 남의 디자인 번호를 보내면 다이어리도 표지도 만들어지지 않는다. */
    @Test
    void creatingWithSomeoneElsesDesignCreatesNothing() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryCoverDesignService.getMyDesign(5L, 7L)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "표지 디자인을 찾을 수 없습니다."));

        mockMvc.perform(multipart("/diaries")
                        .param("title", "여름 제주 여행")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-05")
                        .param("coverSelectionType", "CUSTOM")
                        .param("customCoverDesignId", "5")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                // 다른 입력 오류와 같은 길로, 작성 화면에 이유를 보여 준다
                .andExpect(view().name("diary/new"))
                .andExpect(content().string(containsString("표지 디자인을 찾을 수 없습니다.")));

        verify(diaryCoverService, org.mockito.Mockito.never())
                .createWithDesign(anyLong(), any(Diary.class), anyLong());
        verify(diaryService, org.mockito.Mockito.never()).create(anyLong(), any(Diary.class));
    }

    /** 기본 디자인으로 만들면 예전 길 그대로다. */
    @Test
    void creatingWithADefaultCoverStillTakesTheOldPath() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.create(eq(7L), any(Diary.class))).thenReturn(new Diary());

        mockMvc.perform(multipart("/diaries")
                        .param("title", "여름 제주 여행")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-05")
                        .param("coverSelectionType", "PRESET")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().is3xxRedirection());

        verify(diaryService).create(eq(7L), any(Diary.class));
        verify(diaryCoverService, org.mockito.Mockito.never())
                .createWithDesign(anyLong(), any(Diary.class), anyLong());
    }

    /** 책장 목록도 커스텀 표지를 꾸민 그대로 보여 준다. (카드마다 묻지 않는다) */
    @Test
    void theShelfDrawsCustomCoversWithoutAskingPerCard() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryPage(7L, null, DiarySort.UPDATED_DESC, 1))
                .thenReturn(listPage(List.of(item())));
        DiaryCover cover = new DiaryCover();
        cover.setId(3L);
        cover.setDiaryId(10L);
        cover.setBaseCoverStyle("LEATHER_DEEP_GREEN");
        when(diaryCoverService.findCoversByDiary(List.of(10L), 7L))
                .thenReturn(Map.of(10L, cover));
        DiaryCoverElement element = new DiaryCoverElement();
        element.setCoverId(3L);
        element.setElementType("PHOTO");
        element.setImageUrl("/uploads/diary-cover-elements/a.jpg");
        element.setPhotoStyle("POLAROID");
        element.setPositionX(new java.math.BigDecimal("0.2000"));
        element.setPositionY(new java.math.BigDecimal("0.3000"));
        element.setWidth(new java.math.BigDecimal("0.4000"));
        element.setHeight(new java.math.BigDecimal("0.3000"));
        element.setRotation(new java.math.BigDecimal("0.00"));
        element.setZIndex(1);
        when(diaryCoverService.findElementsByCover(any()))
                .thenReturn(Map.of(3L, List.of(element)));

        mockMvc.perform(get("/diaries")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("diary-cover-canvas")))
                .andExpect(content().string(
                        containsString("/uploads/diary-cover-elements/a.jpg")))
                // 보기 전용이라 조작 손잡이나 저장 주소는 실리지 않는다
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(containsString("data-position-url"))));

        // 표지와 요소를 각각 한 번씩만 읽는다 (카드 수와 상관없이)
        verify(diaryCoverService).findCoversByDiary(List.of(10L), 7L);
        verify(diaryCoverService).findElementsByCover(any());
    }

    /**
     * 디자인에 붙인 글씨는 적용된 표지로 복사되어 책장 목록에서도 보인다.
     * (diary_cover_design_elements TEXT → diary_cover_elements TEXT → 책 표지)
     */
    @Test
    void theShelfDrawsTheLabelsOnAppliedCovers() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryPage(7L, null, DiarySort.UPDATED_DESC, 1))
                .thenReturn(listPage(List.of(item())));
        DiaryCover cover = new DiaryCover();
        cover.setId(3L);
        cover.setDiaryId(10L);
        cover.setBaseCoverStyle("LEATHER_DEEP_GREEN");
        when(diaryCoverService.findCoversByDiary(List.of(10L), 7L))
                .thenReturn(Map.of(10L, cover));
        DiaryCoverElement label = new DiaryCoverElement();
        label.setCoverId(3L);
        label.setElementType("TEXT");
        label.setTextContent("JEJU 2026");
        label.setTextFont("park-dahyun");
        label.setPositionX(new java.math.BigDecimal("0.38000"));
        label.setPositionY(new java.math.BigDecimal("0.38000"));
        label.setWidth(new java.math.BigDecimal("0.44000"));
        label.setHeight(new java.math.BigDecimal("0.09000"));
        label.setRotation(new java.math.BigDecimal("0.00"));
        label.setZIndex(1);
        when(diaryCoverService.findElementsByCover(any())).thenReturn(Map.of(3L, List.of(label)));

        String body = mockMvc.perform(get("/diaries")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String item = between(body, "class=\"diary-canvas-item diary-label\"", "</figure>");
        assertThat(item).contains("JEJU 2026").contains("diary-font-park-dahyun");
        assertThat(item).contains("left:38.00000%").contains("width:44.00000%");
        // 보기 전용이라 조작 UI 는 없다
        assertThat(item).doesNotContain("diary-resize-handle").doesNotContain("data-position-url");
        // 글꼴 정의는 편집 화면과 같은 파일에서 온다
        assertThat(body).contains("/css/diary-fonts.css");
    }

    /** 새 여행일기의 '내 디자인' 미리보기에서도 같은 조각이 글씨를 그린다. */
    @Test
    void theNewDiaryFormPreviewAlsoShowsTheLabels() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        DiaryCoverDesign design = coverDesign(5L, "제주 표지");
        when(diaryCoverDesignService.getMyDesigns(7L)).thenReturn(List.of(design));
        DiaryCoverDesignElement label = new DiaryCoverDesignElement();
        label.setElementType("TEXT");
        label.setTextContent("SUMMER TRIP");
        label.setTextFont("bookk-myeongjo");
        label.setPositionX(new java.math.BigDecimal("0.30000"));
        label.setPositionY(new java.math.BigDecimal("0.40000"));
        label.setWidth(new java.math.BigDecimal("0.44000"));
        label.setHeight(new java.math.BigDecimal("0.09000"));
        label.setRotation(new java.math.BigDecimal("0.00"));
        label.setZIndex(1);
        when(diaryCoverDesignElementService.getElementsByDesign(List.of(5L), 7L))
                .thenReturn(Map.of(5L, List.of(label)));

        String body = mockMvc.perform(get("/diaries/new")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String item = between(body, "class=\"diary-canvas-item diary-label\"", "</figure>");
        assertThat(item).contains("SUMMER TRIP").contains("diary-font-bookk-myeongjo");
        assertThat(body).contains("/css/diary-fonts.css");
    }

    private DiaryCoverDesign coverDesign(Long id, String name) {
        DiaryCoverDesign design = new DiaryCoverDesign();
        design.setId(id);
        design.setUserId(7L);
        design.setName(name);
        design.setBaseCoverStyle("LEATHER_DEEP_GREEN");
        return design;
    }

    @Test
    void validationFailureKeepsTheInputAndShowsTheMessage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.create(eq(7L), any(Diary.class))).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "여행 종료일은 시작일 이후여야 합니다."));

        mockMvc.perform(multipart("/diaries")
                        .param("title", "여름 제주 여행")
                        .param("startDate", "2026-08-05")
                        .param("endDate", "2026-08-01")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/new"))
                .andExpect(content().string(containsString("여행 종료일은 시작일 이후여야 합니다.")))
                .andExpect(content().string(containsString("여름 제주 여행")));
    }

    @Test
    void onlyTheReadSpreadKeepsTheGutterColumn() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));

        String readBody = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 두 장 사이의 칸은 읽기 펼침에만 있다. 안에 든 장식은 없다
        assertThat(readBody).contains("diary-book-gutter");
        assertThat(readBody).doesNotContain("diary-book-ribbon");
        assertThat(readBody).contains("aria-hidden=\"true\"");

        String editBody = mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 편집은 한 장 집중 화면이라 제본/책갈피 장식이 없다
        assertThat(editBody).doesNotContain("diary-book-gutter");
        assertThat(editBody).doesNotContain("diary-book-ribbon");
    }

    @Test
    void editModeShowsTheHighlighterControlAndReadModeKeepsTheSavedHighlight() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        DiaryPage first = page(1, "2026-08-01");
        first.setContent("<p><span style=\"background-color: #fff5a5;\">형광펜 문장</span></p>");
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(first));

        String editBody = mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(editBody).contains("형광펜");
        assertThat(editBody).contains("diary-highlight-trigger");
        assertThat(editBody).contains("diary-highlight-palette");

        String readBody = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 읽기 모드는 편집 도구만 감추고 형광펜 자국은 그대로 보여준다
        assertThat(readBody).contains("background-color: #fff5a5").contains("형광펜 문장");
        assertThat(readBody).doesNotContain("diary-highlight-trigger");
    }

    @Test
    void listKeepsTheBookLinkCoverImageAndPlaceholderStructure() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        DiaryListItemDto withCover = item();
        withCover.setCoverImageUrl("/uploads/diary/cover.jpg");
        DiaryListItemDto withoutCover = item();
        withoutCover.setId(11L);
        withoutCover.setTitle("겨울 강릉 여행");
        when(diaryService.getMyDiaryPage(7L, null, DiarySort.UPDATED_DESC, 1))
                .thenReturn(listPage(List.of(withCover, withoutCover)));

        String body = mockMvc.perform(get("/diaries")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("href=\"/diaries/10\"").contains("href=\"/diaries/11\"");
        // 대표 이미지가 있으면 표지 이미지, 없으면 기본 표지
        assertThat(body).contains("diary-book-image").contains("/uploads/diary/cover.jpg");
        assertThat(body).contains("diary-book-placeholder");
        assertThat(body).contains("diary-book-spine");
        assertThat(body).contains("diary-book-title");
        assertThat(body).contains("3장");
        // 제목은 표지 안이 아니라 표지 아래 정보 영역에 있다
        assertThat(body.indexOf("diary-book-image"))
                .isLessThan(body.indexOf("diary-book-title"));
        assertThat(body.indexOf("diary-book-title"))
                .isLessThan(body.indexOf("diary-book-period"));
        assertThat(body).doesNotContain("diary-book-label");
    }

    @Test
    void listCardCarriesTheBookLevelActionsInACompactMenu() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiaryPage(7L, null, DiarySort.UPDATED_DESC, 1)).thenReturn(listPage(List.of(item())));

        String body = mockMvc.perform(get("/diaries")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("diary-book-menu-button");
        assertThat(body).contains("aria-haspopup=\"menu\"");
        assertThat(body).contains("id=\"diary-book-menu-10\"");
        // 상세에서 옮겨 온 액션은 기존 route 를 그대로 쓴다
        assertThat(body).contains("다이어리 설정").contains("/diaries/10/edit");
        assertThat(body).contains("다이어리 삭제").contains("action=\"/diaries/10/delete\"");
        // 삭제는 바로 실행되지 않고 확인을 거친다
        assertThat(body).contains("onsubmit=\"return confirm(");
        // 메뉴는 카드 링크(a) 바깥에 있어 표지 클릭을 가로채지 않는다
        assertThat(body.indexOf("diary-book-meta"))
                .isLessThan(body.indexOf("diary-book-menu-button"));
        assertThat(body).contains("/js/diary-book-menu.js");
    }

    @Test
    void listRendersTheStoredCoverStyleAsAHyphenatedClass() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        DiaryListItemDto leather = item();
        leather.setCoverStyle("LEATHER_DARK_BROWN");
        DiaryListItemDto legacy = item();
        legacy.setId(11L);
        legacy.setCoverStyle("DEFAULT");
        when(diaryService.getMyDiaryPage(7L, null, DiarySort.UPDATED_DESC, 1)).thenReturn(listPage(List.of(leather, legacy)));

        String body = mockMvc.perform(get("/diaries")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("diary-cover-leather-dark-brown");
        // 기존 DEFAULT 데이터도 그대로 동작한다
        assertThat(body).contains("diary-cover-default");
    }

    @Test
    void newAndEditFormsLetTheUserPickACoverStyle() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());

        String newBody = mockMvc.perform(get("/diaries/new")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(newBody).contains("name=\"coverStyle\"");
        assertThat(newBody).contains("value=\"DEFAULT\"").contains("value=\"LEATHER_BLACK\"")
                .contains("value=\"HARDCOVER_BEIGE\"");
        assertThat(newBody).contains("diary-cover-option");

        String editBody = mockMvc.perform(get("/diaries/10/edit")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(editBody).contains("name=\"coverStyle\"");
        assertThat(editBody).contains("value=\"HARDCOVER_NAVY\"");
    }

    @Test
    void createPassesTheChosenCoverStyleToTheService() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.create(eq(7L), any(Diary.class))).thenReturn(new Diary());

        mockMvc.perform(multipart("/diaries")
                        .param("title", "여름 제주 여행")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-05")
                        .param("coverStyle", "LEATHER_DEEP_GREEN")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(redirectedUrl("/diaries"));

        ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryService).create(eq(7L), captor.capture());
        assertThat(captor.getValue().getCoverStyle()).isEqualTo("LEATHER_DEEP_GREEN");
    }

    @Test
    void updateKeepsTheCurrentCoverStyleWhenNoneIsSubmitted() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        Diary existing = diary();
        existing.setCoverStyle("HARDCOVER_BURGUNDY");
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(existing);
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of());

        mockMvc.perform(multipart("/diaries/10/update")
                        .param("title", "여름 제주 여행")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-05")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(redirectedUrl("/diaries"));

        ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryService).update(eq(10L), eq(7L), captor.capture());
        assertThat(captor.getValue().getCoverStyle()).isEqualTo("HARDCOVER_BURGUNDY");
    }

    @Test
    void unknownCoverStyleIsRejectedWithTheFormMessage() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.create(eq(7L), any(Diary.class))).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "표지 스타일을 다시 선택해 주세요."));

        mockMvc.perform(multipart("/diaries")
                        .param("title", "여름 제주 여행")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-05")
                        .param("coverStyle", "GOLD_PLATED")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(view().name("diary/new"))
                .andExpect(content().string(containsString("표지 스타일을 다시 선택해 주세요.")));
    }

    private Diary diary() {
        Diary diary = new Diary();
        diary.setId(10L);
        diary.setUserId(7L);
        diary.setTitle("여름 제주 여행");
        diary.setStartDate(LocalDate.of(2026, 8, 1));
        diary.setEndDate(LocalDate.of(2026, 8, 5));
        return diary;
    }

    private DiaryElement textElement(Long id, String content) {
        DiaryElement element = element(id, "TEXT");
        element.setTextContent(content);
        return element;
    }

    private DiaryElement photoElement(Long id, String imageUrl) {
        DiaryElement element = element(id, "PHOTO");
        element.setImageUrl(imageUrl);
        return element;
    }

    /** 스티커는 사진과 같은 자유배치 이미지 요소다. (공용 asset 경로만 다르다) */
    // ── 꾸미기 picker ────────────────────────────────────────

    @Test
    void theDecorPickerOffersStickersLabelsAndMemosUnderOneButton() throws Exception {
        String body = editPageBody();

        // 툴바에는 꾸미기 버튼 하나뿐이다. 종류는 팝오버 안에서 고른다
        assertThat(countOf(body, "class=\"diary-toolbar-button\" id=\"diary-sticker-button\""))
                .isEqualTo(1);
        assertThat(body).contains("title=\"꾸미기\"");
        assertThat(body)
                .contains("data-decor-tab=\"sticker\"")
                .contains("data-decor-tab=\"label\"")
                .contains("data-decor-tab=\"memo\"");
        // 처음 열면 스티커부터 보인다
        assertThat(between(body, "data-decor-tab=\"sticker\"", "</button>"))
                .doesNotContain("hidden");
        assertThat(between(body, "class=\"diary-decor-tab is-active\"", "</button>"))
                .contains("data-decor-tab=\"sticker\"");
    }

    /**
     * 라벨기는 꾸미기 팝오버의 네 번째 갈래다.
     * 글꼴 목록은 서버가 manifest 대로 그려 준다 — 화면이 목록을 따로 들지 않는다.
     */
    @Test
    void theLabelMakerIsAFourthDecorTabWithFontsFromTheManifest() throws Exception {
        String body = editPageBody();

        assertThat(body).contains("data-decor-tab=\"text\"");
        // 기존 NOTE '라벨'과 이름이 겹치지 않게 나눈다
        assertThat(between(body, "data-decor-tab=\"text\"", "</button>")).contains("라벨기");

        String panel = between(body, "data-decor-panel=\"text\"", "diary-sticker-status");
        // 붙일 자리는 고르는 칸이 들고 있다. (표지 편집과 같은 조각을 쓰는 자리다)
        assertThat(panel)
                .contains("data-label-maker")
                .contains("data-create-url=\"/diaries/10/pages/1/elements/label\"");
        assertThat(panel)
                .contains("id=\"diary-label-text\"")
                .contains("id=\"diary-label-attach\"")
                // 서버 상한과 같은 50자
                .contains("maxlength=\"50\"");
        // 세 글꼴이 실제 그 글꼴로 미리 보인다
        assertThat(panel)
                .contains("data-label-font=\"nanum-square\"")
                .contains("data-label-font=\"bookk-myeongjo\"")
                .contains("data-label-font=\"park-dahyun\"")
                .contains("diary-font-nanum-square")
                .contains("diary-font-bookk-myeongjo")
                .contains("diary-font-park-dahyun");
        // 처음에는 첫 글꼴이 눌려 있다
        assertThat(between(panel, "class=\"diary-label-font is-active\"", "</button>"))
                .contains("data-label-font=\"nanum-square\"");
    }

    /** 붙여 둔 글씨는 배경 없이 글자만, 조작 주소와 함께 그려진다. */
    @Test
    void savedLabelsComeBackWithTheirFontAndTheAddressesTheEngineReads() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        when(diaryElementService.getElements(10L, 1L, 7L)).thenReturn(List.of(labelElement()));

        String body = mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String label = between(body, "class=\"diary-canvas-item diary-label\"", "</figure>");
        assertThat(label).contains("JEJU 2026").contains("diary-font-park-dahyun");
        // 글자 크기를 상자에 맞추는 데 쓰는 값도 함께 실린다
        assertThat(label).contains("--diary-label-chars:9");
        assertThat(label)
                .contains("/diaries/10/pages/1/elements/100/position")
                .contains("/diaries/10/pages/1/elements/100/size")
                .contains("/diaries/10/pages/1/elements/100/rotation")
                .contains("/diaries/10/pages/1/elements/100/layer")
                .contains("/diaries/10/pages/1/elements/100/label/delete");
        // 종이 배경은 쓰지 않는다 (NOTE 와 다른 갈래다)
        assertThat(label).doesNotContain("diary-note-surface");
    }

    /** 읽기 모드는 같은 자리에 글씨만 남긴다. 조작 UI 는 만들지 않는다. */
    @Test
    void theReadingViewShowsTheLabelWithoutAnyEditingUi() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        when(diaryElementService.getElements(10L, 1L, 7L)).thenReturn(List.of(labelElement()));

        String body = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String label = between(body, "class=\"diary-canvas-item diary-label\"", "</figure>");
        // 자리·크기·회전은 편집 모드와 같은 값이다
        assertThat(label).contains("left:20.00000%").contains("width:32.00000%");
        assertThat(label).contains("JEJU 2026").contains("diary-font-park-dahyun");
        // 조작 UI 는 만들지 않는다 (사진·스티커와 같은 규칙)
        assertThat(label)
                .doesNotContain("diary-resize-handle")
                .doesNotContain("diary-rotate-handle")
                .doesNotContain("diary-layer-actions");
    }

    private DiaryElement labelElement() {
        DiaryElement element = new DiaryElement();
        element.setId(100L);
        element.setPageId(1L);
        element.setElementType("TEXT");
        element.setTextContent("JEJU 2026");
        element.setTextFont("park-dahyun");
        element.setPositionX(new java.math.BigDecimal("0.20000"));
        element.setPositionY(new java.math.BigDecimal("0.30000"));
        element.setWidth(new java.math.BigDecimal("0.32000"));
        element.setHeight(new java.math.BigDecimal("0.07000"));
        element.setRotation(new java.math.BigDecimal("0.00"));
        element.setZIndex(1);
        return element;
    }

    @Test
    void theStickerPickerKeepsEverythingItAlreadyHad() throws Exception {
        String body = editPageBody();
        String stickerPanel = between(body, "data-decor-panel=\"sticker\"",
                "data-decor-panel=\"label\"");

        // 분류 탭·최근·마스킹테이프 갈래가 예전 자리 그대로 스티커 묶음 안에 있다
        assertThat(stickerPanel)
                .contains("data-sticker-category=\"recent\"")
                .contains("diary-sticker-tab")
                .contains("diary-sticker-subtab")
                .contains("data-tape-type=\"TRANSLUCENT\"")
                .contains("data-sticker-id=");
        // 스티커 붙이기 주소도 그대로다
        assertThat(body).contains("data-create-url");
    }

    @Test
    void theLabelAndMemoListsComeFromTheManifest() throws Exception {
        String body = editPageBody();
        String labelPanel = between(body, "data-decor-panel=\"label\"",
                "data-decor-panel=\"memo\"");
        String memoPanel = between(body, "data-decor-panel=\"memo\"", "diary-sticker-status");

        assertThat(labelPanel)
                .contains("data-note-style=\"DATE_LABEL\"")
                .contains("data-note-style=\"TITLE_LABEL\"")
                .contains("날짜 라벨")
                .contains("제목 라벨");
        assertThat(memoPanel)
                .contains("data-note-style=\"MEMO_SQUARE\"")
                .contains("data-note-style=\"MEMO_ROUND\"")
                .contains("사각 메모지")
                .contains("둥근 메모지");
        // 라벨 목록에 메모지가, 메모지 목록에 라벨이 섞이지 않는다
        assertThat(labelPanel).doesNotContain("MEMO_");
        assertThat(memoPanel).doesNotContain("_LABEL");
    }

    @Test
    void bothNoteTabsOfferTheSamePaletteFromTheManifest() throws Exception {
        String body = editPageBody();
        String labelPanel = between(body, "data-decor-panel=\"label\"",
                "data-decor-panel=\"memo\"");
        String memoPanel = between(body, "data-decor-panel=\"memo\"", "diary-sticker-status");

        for (String panel : new String[]{labelPanel, memoPanel}) {
            // 색 목록은 서버가 그린다. 템플릿에 색을 적어 두지 않는다
            assertThat(panel)
                    .contains("data-note-color=\"IVORY\"")
                    .contains("data-note-color=\"PINK\"")
                    .contains("data-note-color=\"SAGE\"")
                    .contains("data-note-color=\"SKY\"")
                    .contains("아이보리").contains("세이지");
            // 스와치도 실제로 붙었을 때와 같은 색 class 로 칠해진다
            assertThat(panel).contains("diary-note-color-sage");
            // 색 없음 자리는 두지 않는다. 처음에는 맨 앞의 기본색(IVORY)이 골라져 있다
            assertThat(panel).doesNotContain("data-note-color=\"\"");
            assertThat(between(panel, "class=\"diary-note-swatch diary-note-color-ivory is-active\"",
                    "</button>")).contains("data-note-color=\"IVORY\"");
        }
    }

    @Test
    void theStickerTabHasNoColourRow() throws Exception {
        String body = editPageBody();

        // 색은 라벨/메모지의 축이다. 스티커·마스킹테이프에는 없다
        assertThat(between(body, "data-decor-panel=\"sticker\"", "data-decor-panel=\"label\""))
                .doesNotContain("diary-note-swatch")
                .doesNotContain("data-note-color");
    }

    @Test
    void aChosenColourIsRememberedPerTabAndShownOnThePreviews() throws Exception {
        String pickerJs = Files.readString(
                Path.of("src/main/resources/static/js/diary-note-picker.js"));

        // 갈래마다 따로 기억한다. 탭을 오갔다고 고른 색이 사라지지 않는다
        assertThat(pickerJs)
                .contains("chosenColors.set(panel.dataset.decorPanel")
                .contains("chosenColors.get(");
        // 고르면 그 갈래의 미리보기 색이 곧바로 바뀐다 (모양 class 는 그대로)
        assertThat(between(pickerJs, "function paintPreviews(panel, colorClass)", "\n    }"))
                .contains(".diary-note-preview")
                .contains("classList.remove(previous)")
                .contains("classList.add(colorClass)");
        // 색 class 는 스와치가 이미 달고 있는 것을 그대로 쓴다 (매핑표를 만들지 않는다)
        assertThat(pickerJs).contains("startsWith('diary-note-color-')");
        assertThat(pickerJs)
                .doesNotContain("IVORY").doesNotContain("SAGE").doesNotContain("#f1f5f0");
    }

    @Test
    void theChosenColourIsSentAlongWhenTheNoteIsPlaced() throws Exception {
        String pickerJs = Files.readString(
                Path.of("src/main/resources/static/js/diary-note-picker.js"));

        assertThat(pickerJs).contains("body.append('color', colorType)");
        // 고르지 않았으면 아예 보내지 않는다. 서버가 그 모양의 기본색을 쓴다
        assertThat(pickerJs).contains("if (colorType) body.append");
        // 붙은 뒤 화면에 칠하는 색도 서버가 준 class 다
        assertThat(pickerJs).contains("note.colorClass");
    }

    @Test
    void thePreviewUsesTheRealNoteLookNotAPicture() throws Exception {
        String body = editPageBody();
        String labelPanel = between(body, "data-decor-panel=\"label\"",
                "data-decor-panel=\"memo\"");

        // 종이 위의 NOTE 와 같은 class 로 그린다. 그림 파일을 따로 두지 않는다
        assertThat(labelPanel)
                .contains("diary-note-preview")
                .contains("diary-note-date-label")
                .contains("diary-note-surface")
                .contains("diary-note-text")
                .doesNotContain("<img");
        assertThat(between(body, "data-decor-panel=\"memo\"", "diary-sticker-status"))
                .contains("diary-note-memo-square")
                .contains("diary-note-memo-round");
    }

    @Test
    void thePreviewWordsAreOnlyForShowing() throws Exception {
        String body = editPageBody();

        // 보기 글은 고르는 자리에만 있다
        assertThat(body).contains("2026.08.28").contains("JEJU DAY 1").contains("오늘의 기록");
        // 붙이는 요청에는 글이 실리지 않는다. 서버가 빈 글로 만든다
        String noteJs = Files.readString(
                Path.of("src/main/resources/static/js/diary-note-picker.js"));
        assertThat(between(noteJs, "new URLSearchParams(", ")"))
                .contains("style: styleType")
                .doesNotContain("text");
    }

    @Test
    void theNoteIsDrawnTheSameWayWhetherTheServerOrTheScreenMadeIt() throws Exception {
        String noteJs = Files.readString(
                Path.of("src/main/resources/static/js/diary-note-picker.js"));

        // 모양 class 는 서버가 준 것을 그대로 쓴다. 화면이 표를 다시 만들지 않는다
        assertThat(noteJs).contains("note.styleClass");
        assertThat(noteJs)
                .doesNotContain("DATE_LABEL")
                .doesNotContain("MEMO_SQUARE");
        // 서버가 그린 figure 와 같은 구조·같은 값을 쓴다
        assertThat(noteJs)
                .contains("diary-canvas-item diary-note")
                .contains("diary-note-surface")
                .contains("diary-note-text")
                .contains("note.positionX")
                .contains("note.width")
                .contains("note.rotation")
                .contains("note.zIndex")
                .contains("note.urls.position")
                .contains("note.urls.size")
                .contains("note.urls.rotation")
                .contains("note.urls.layer")
                .contains("note.urls.delete");
        // 붙인 직후 바로 옮기고 지울 수 있어야 한다. 새로고침을 기다리지 않는다
        assertThat(noteJs).contains("window.diaryCanvas?.register(item)");
        // 글은 서버가 준 값(빈 문자열) 그대로다
        assertThat(noteJs).contains("text.textContent = note.textContent");
        // 아직 글을 고치는 길은 없다
        assertThat(noteJs)
                .doesNotContain("contenteditable")
                .doesNotContain("prompt(");
    }

    @Test
    void aNoteCanBeTakenOffTheSameWayAStickerCan() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        when(diaryElementService.getElements(10L, 1L, 7L))
                .thenReturn(List.of(noteElement(300L, "MEMO_SQUARE")));

        String body = mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String noteJs = Files.readString(
                Path.of("src/main/resources/static/js/diary-note-picker.js"));

        // 서버가 그린 NOTE 에도 사진·스티커와 같은 액션 줄이 붙는다
        assertThat(body)
                .contains("diary-canvas-item diary-note diary-note-memo-square")
                .contains("/elements/300/note/delete")
                .contains("data-layer-direction=\"BACKWARD\"");
        // 화면이 만든 NOTE 도 같은 방식이다 (실제 요청은 공통 canvas JS 가 보낸다)
        assertThat(noteJs)
                .contains("diary-layer-action is-danger")
                .contains("dataset.deleteUrl")
                .contains("dataset.deleteConfirm");
    }

    @Test
    void theStickerScriptIsLeftAloneByTheNewOne() throws Exception {
        String stickerJs = Files.readString(
                Path.of("src/main/resources/static/js/diary-sticker-picker.js"));
        String noteJs = Files.readString(
                Path.of("src/main/resources/static/js/diary-note-picker.js"));

        // 스티커 쪽은 라벨을 모른다. 예전 그대로다
        assertThat(stickerJs)
                .doesNotContain("note")
                .doesNotContain("decor");
        // 라벨 쪽도 스티커의 최근 목록·테이프 처리를 건드리지 않는다
        assertThat(noteJs)
                .doesNotContain("RecentStickers")
                .doesNotContain("diaryTape")
                .doesNotContain("maskingTape");
        // 팝오버 열고 닫기는 스티커 쪽 하나만 갖는다 (두 번 붙지 않는다)
        assertThat(noteJs).doesNotContain("popover.hidden =");
    }

    /** 편집 화면 HTML 한 벌. picker 마크업을 보는 테스트가 함께 쓴다. */
    private String editPageBody() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        when(diaryElementService.getElements(10L, 1L, 7L)).thenReturn(List.of());

        return mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private int countOf(String source, String needle) {
        int count = 0;
        for (int index = source.indexOf(needle); index >= 0;
             index = source.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("end %s", end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }

    // ── 라벨 / 떡메모지 (NOTE) ───────────────────────────────

    @Test
    void aNoteIsCreatedFromTheServerSideCatalogOnly() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.getElements(10L, 3L, 7L)).thenReturn(List.of());
        when(diaryElementService.create(eq(10L), eq(3L), eq(7L), any()))
                .thenReturn(noteElement(300L, "MEMO_SQUARE"));

        String body = mockMvc.perform(post("/diaries/10/pages/3/elements/note")
                        .param("style", "MEMO_SQUARE")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ArgumentCaptor<DiaryElement> captor = ArgumentCaptor.forClass(DiaryElement.class);
        verify(diaryElementService).create(eq(10L), eq(3L), eq(7L), captor.capture());
        DiaryElement saved = captor.getValue();
        // 유형은 화면이 정하지 않는다
        assertThat(saved.getElementType()).isEqualTo("NOTE");
        assertThat(saved.getStyleType()).isEqualTo("MEMO_SQUARE");
        // 붙이기 전에 무슨 말을 쓸지 정하게 하지 않는다
        assertThat(saved.getTextContent()).isEmpty();
        // 라벨/메모지는 그림을 갖지 않는다
        assertThat(saved.getImageUrl()).isNull();

        // 화면이 바로 그릴 수 있는 값이 함께 온다
        assertThat(body)
                .contains("\"elementType\":\"NOTE\"")
                .contains("\"styleType\":\"MEMO_SQUARE\"")
                .contains("\"styleClass\":\"diary-note-memo-square\"")
                .contains("\"textContent\":\"\"")
                .contains("/note/delete");
    }

    @Test
    void aColourCanBeChosenWhenPlacingANote() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.getElements(10L, 3L, 7L)).thenReturn(List.of());
        DiaryElement created = noteElement(310L, "MEMO_SQUARE");
        created.setColorType("SAGE");
        when(diaryElementService.create(eq(10L), eq(3L), eq(7L), any())).thenReturn(created);

        String body = mockMvc.perform(post("/diaries/10/pages/3/elements/note")
                        .param("style", "MEMO_SQUARE")
                        .param("color", "SAGE")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ArgumentCaptor<DiaryElement> captor = ArgumentCaptor.forClass(DiaryElement.class);
        verify(diaryElementService).create(eq(10L), eq(3L), eq(7L), captor.capture());
        assertThat(captor.getValue().getColorType()).isEqualTo("SAGE");
        // 화면이 그대로 붙일 수 있게 class 도 함께 온다
        assertThat(body)
                .contains("\"colorType\":\"SAGE\"")
                .contains("\"colorClass\":\"diary-note-color-sage\"");
    }

    @Test
    void aNoteCanBePlacedWithoutChoosingAColour() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.getElements(10L, 3L, 7L)).thenReturn(List.of());
        when(diaryElementService.create(eq(10L), eq(3L), eq(7L), any()))
                .thenReturn(noteElement(311L, "MEMO_SQUARE"));

        // color 는 선택이다. 무엇을 쓸지는 Service 가 정한다
        String body = mockMvc.perform(post("/diaries/10/pages/3/elements/note")
                        .param("style", "MEMO_SQUARE")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ArgumentCaptor<DiaryElement> captor = ArgumentCaptor.forClass(DiaryElement.class);
        verify(diaryElementService).create(eq(10L), eq(3L), eq(7L), captor.capture());
        assertThat(captor.getValue().getColorType()).isNull();
        // 고르지 않은 색은 기본색(IVORY)으로 정해져 화면에도 그 색으로 내려온다
        assertThat(body).contains("\"colorClass\":\"diary-note-color-ivory\"");
    }

    @Test
    void aColourThatIsNotOnTheListIsRefusedByTheServer() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.getElements(10L, 3L, 7L)).thenReturn(List.of());
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "라벨/메모지 색을 선택해 주세요."))
                .when(diaryElementService).create(anyLong(), anyLong(), anyLong(), any());

        mockMvc.perform(post("/diaries/10/pages/3/elements/note")
                        .param("style", "MEMO_SQUARE")
                        .param("color", "NEON")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("라벨/메모지 색을 선택해 주세요.")));
    }

    @Test
    void aNoteCarriesBothItsShapeAndColourOntoThePaper() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        DiaryElement stored = noteElement(300L, "MEMO_ROUND");
        stored.setColorType("SKY");
        when(diaryElementService.getElements(10L, 1L, 7L)).thenReturn(List.of(stored));

        String body = mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("diary-note-memo-round").contains("diary-note-color-sky");
    }

    @Test
    void anOlderNoteWithNoColourStillDraws() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        // 색 칸이 생기기 전에 만든 요소. color_type 이 비어 있다
        when(diaryElementService.getElements(10L, 1L, 7L))
                .thenReturn(List.of(noteElement(300L, "MEMO_SQUARE")));

        String body = mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andReturn().getResponse().getContentAsString();

        String figure = between(body, "diary-canvas-item diary-note", "</figure>");
        assertThat(figure).contains("diary-note-memo-square");
        // 색 칸이 비어 있는 예전 행은 기본색(IVORY)으로 읽는다. (DB 값은 그대로 둔다)
        assertThat(figure).contains("diary-note-color-ivory");
    }

    @Test
    void everyDesignOnTheListCanBePlaced() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.getElements(10L, 3L, 7L)).thenReturn(List.of());

        for (String style : new String[]{
                "DATE_LABEL", "TITLE_LABEL", "MEMO_SQUARE", "MEMO_ROUND"}) {
            when(diaryElementService.create(eq(10L), eq(3L), eq(7L), any()))
                    .thenReturn(noteElement(300L, style));

            mockMvc.perform(post("/diaries/10/pages/3/elements/note")
                            .param("style", style)
                            .with(csrf())
                            .with(authentication(new UsernamePasswordAuthenticationToken(
                                    userDetails, null, List.of()))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void aLabelIsPlacedAsAWideStripAndAMemoAsASquare() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.getElements(10L, 3L, 7L)).thenReturn(List.of());
        when(diaryElementService.create(eq(10L), eq(3L), eq(7L), any()))
                .thenReturn(noteElement(301L, "DATE_LABEL"));

        mockMvc.perform(post("/diaries/10/pages/3/elements/note")
                        .param("style", "DATE_LABEL")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk());

        ArgumentCaptor<DiaryElement> captor = ArgumentCaptor.forClass(DiaryElement.class);
        verify(diaryElementService).create(eq(10L), eq(3L), eq(7L), captor.capture());
        DiaryElement label = captor.getValue();
        // 라벨은 납작한 가로 딱지다
        assertThat(label.getWidth()).isEqualByComparingTo("0.30000");
        assertThat(label.getHeight()).isEqualByComparingTo("0.08000");
        // 자리와 회전·겹침 순서는 스티커와 같은 규칙을 그대로 쓴다
        assertThat(label.getPositionX()).isEqualByComparingTo("0.41000");
        assertThat(label.getPositionY()).isEqualByComparingTo("0.41000");
        assertThat(label.getRotation()).isNull();
        assertThat(label.getZIndex()).isNull();

        org.mockito.Mockito.reset(diaryElementService);
        when(diaryElementService.getElements(10L, 3L, 7L)).thenReturn(List.of());
        when(diaryElementService.create(eq(10L), eq(3L), eq(7L), any()))
                .thenReturn(noteElement(302L, "MEMO_ROUND"));

        mockMvc.perform(post("/diaries/10/pages/3/elements/note")
                        .param("style", "MEMO_ROUND")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk());

        verify(diaryElementService).create(eq(10L), eq(3L), eq(7L), captor.capture());
        DiaryElement memo = captor.getValue();
        /*
          종이가 41:38 이라 화면에서 정사각형으로 보이려면 세로를 그만큼 더 준다.
          (0.26 * 41 ≈ 0.28 * 38) 페이지 절반을 넘지 않는다.
        */
        assertThat(memo.getWidth()).isEqualByComparingTo("0.26000");
        assertThat(memo.getHeight()).isEqualByComparingTo("0.28000");
    }

    @Test
    void notesAreNudgedApartLikeStickers() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        // 이미 두 개가 놓여 있으면 세 번째는 그만큼 어긋나게 놓인다
        when(diaryElementService.getElements(10L, 3L, 7L))
                .thenReturn(List.of(element(1L, "NOTE"), element(2L, "NOTE")));
        when(diaryElementService.create(eq(10L), eq(3L), eq(7L), any()))
                .thenReturn(noteElement(303L, "MEMO_SQUARE"));

        mockMvc.perform(post("/diaries/10/pages/3/elements/note")
                        .param("style", "MEMO_SQUARE")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk());

        ArgumentCaptor<DiaryElement> captor = ArgumentCaptor.forClass(DiaryElement.class);
        verify(diaryElementService).create(eq(10L), eq(3L), eq(7L), captor.capture());
        // 0.41 + 0.04 * 2 (스티커와 같은 계단)
        assertThat(captor.getValue().getPositionX()).isEqualByComparingTo("0.49000");
        assertThat(captor.getValue().getPositionY()).isEqualByComparingTo("0.49000");
    }

    @Test
    void aDesignThatIsNotOnTheListIsRejected() throws Exception {
        when(userDetails.getId()).thenReturn(7L);

        mockMvc.perform(post("/diaries/10/pages/3/elements/note")
                        .param("style", "MEMO_TRIANGLE")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("알 수 없는 디자인입니다.")));

        verify(diaryElementService, org.mockito.Mockito.never())
                .create(any(), any(), any(), any());
    }

    @Test
    void someoneElsesPageCannotBeGivenANote() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        // 소유권·페이지 소속은 서비스가 본다. 주소의 번호만 믿지 않는다
        when(diaryElementService.getElements(99L, 3L, 7L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "여행일기를 찾을 수 없습니다."));

        mockMvc.perform(post("/diaries/99/pages/3/elements/note")
                        .param("style", "MEMO_SQUARE")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNotFound());

        verify(diaryElementService, org.mockito.Mockito.never())
                .create(any(), any(), any(), any());
    }

    @Test
    void aPageFromAnotherDiaryCannotBeGivenANote() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.getElements(10L, 999L, 7L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "페이지를 찾을 수 없습니다."));

        mockMvc.perform(post("/diaries/10/pages/999/elements/note")
                        .param("style", "MEMO_SQUARE")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void placingANoteWithoutATokenIsRefused() throws Exception {
        mockMvc.perform(post("/diaries/10/pages/3/elements/note")
                        .param("style", "MEMO_SQUARE")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isForbidden());

        verify(diaryElementService, org.mockito.Mockito.never())
                .create(any(), any(), any(), any());
    }

    @Test
    void aNoteIsRemovedWithoutTouchingAnyFile() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.getElement(10L, 3L, 300L, 7L))
                .thenReturn(noteElement(300L, "MEMO_SQUARE"));

        mockMvc.perform(post("/diaries/10/pages/3/elements/300/note/delete")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNoContent());

        verify(diaryElementService).delete(10L, 3L, 300L, 7L);
        // 라벨은 파일을 갖지 않는다. 파일을 다루는 쪽은 아예 부르지 않는다
        org.mockito.Mockito.verifyNoInteractions(fileUploadService);
    }

    @Test
    void aStickerCannotBeRemovedThroughTheNoteDoor() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.getElement(10L, 3L, 200L, 7L))
                .thenReturn(stickerElement(200L, "/images/diary/stickers/travel/airplane.svg"));

        mockMvc.perform(post("/diaries/10/pages/3/elements/200/note/delete")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("라벨/메모지 요소가 아닙니다.")));

        verify(diaryElementService, org.mockito.Mockito.never())
                .delete(any(), any(), any(), any());
    }

    @Test
    void someoneElsesNoteCannotBeRemoved() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        // 남의 것도, 다른 페이지의 것도 여기서 같은 답을 받는다
        when(diaryElementService.getElement(10L, 3L, 300L, 7L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "요소를 찾을 수 없습니다."));

        mockMvc.perform(post("/diaries/10/pages/3/elements/300/note/delete")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNotFound());

        verify(diaryElementService, org.mockito.Mockito.never())
                .delete(any(), any(), any(), any());
    }

    @Test
    void removingANoteWithoutATokenIsRefused() throws Exception {
        mockMvc.perform(post("/diaries/10/pages/3/elements/300/note/delete")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isForbidden());

        verify(diaryElementService, org.mockito.Mockito.never())
                .delete(any(), any(), any(), any());
    }

    // ── 라벨 / 떡메모지에 글쓰기 ──────────────────────────────

    @Test
    void theWordsWrittenOnANoteAreSavedAndSentBack() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        DiaryElement saved = noteElement(300L, "MEMO_SQUARE");
        saved.setTextContent("제주 카페 투어");
        when(diaryElementService.updateNoteText(10L, 3L, 300L, 7L, "제주 카페 투어"))
                .thenReturn(saved);

        mockMvc.perform(post("/diaries/10/pages/3/elements/300/text")
                        .param("text", "제주 카페 투어")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                // 화면은 서버가 다듬어 저장한 글로 다시 그린다
                .andExpect(content().string(containsString("\"textContent\":\"제주 카페 투어\"")));
    }

    @Test
    void aNoteCanBeLeftEmpty() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryElementService.updateNoteText(10L, 3L, 300L, 7L, ""))
                .thenReturn(noteElement(300L, "DATE_LABEL"));

        mockMvc.perform(post("/diaries/10/pages/3/elements/300/text")
                        .param("text", "")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"textContent\":\"\"")));
    }

    @Test
    void tooLongIsRefusedWithSomethingReadable() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "100자까지 입력할 수 있습니다."))
                .when(diaryElementService).updateNoteText(anyLong(), anyLong(), anyLong(),
                        anyLong(), anyString());

        mockMvc.perform(post("/diaries/10/pages/3/elements/300/text")
                        .param("text", "가".repeat(101))
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("100자까지 입력할 수 있습니다.")));
    }

    @Test
    void someoneElsesNoteCannotBeWrittenOn() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "요소를 찾을 수 없습니다."))
                .when(diaryElementService).updateNoteText(anyLong(), anyLong(), anyLong(),
                        anyLong(), anyString());

        mockMvc.perform(post("/diaries/10/pages/3/elements/300/text")
                        .param("text", "몰래")
                        .with(csrf())
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void writingWithoutATokenIsRefused() throws Exception {
        mockMvc.perform(post("/diaries/10/pages/3/elements/300/text")
                        .param("text", "토큰 없음")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isForbidden());

        verify(diaryElementService, org.mockito.Mockito.never())
                .updateNoteText(anyLong(), anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void aNoteCanOnlyBeTypedIntoWhileEditing() throws Exception {
        // 편집 화면에서만 글쓰기 주소가 실린다. 읽기 화면에는 글자만 남는다
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        when(diaryElementService.getElements(10L, 1L, 7L))
                .thenReturn(List.of(noteElement(300L, "MEMO_SQUARE")));

        String editBody = mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andReturn().getResponse().getContentAsString();
        String readBody = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andReturn().getResponse().getContentAsString();

        assertThat(editBody).contains("/elements/300/text");
        assertThat(readBody).doesNotContain("/elements/300/text");
        /*
          어느 쪽도 처음부터 고쳐 쓸 수 있는 상태로 그려지지 않는다.
          contenteditable 은 두 번 눌러 열었을 때만 붙는다.
        */
        assertThat(between(editBody, "diary-canvas-item diary-note", "</figure>"))
                .doesNotContain("contenteditable=");
        assertThat(between(readBody, "diary-canvas-item diary-note", "</figure>"))
                .doesNotContain("contenteditable=");
    }

    @Test
    void typingOnANoteFollowsTheRulesOfItsKind() throws Exception {
        String textJs = Files.readString(
                Path.of("src/main/resources/static/js/diary-note-text.js"));

        // 한글을 조합하는 동안의 Enter 는 글자를 확정하는 Enter 다
        assertThat(textJs).contains("event.isComposing || event.keyCode === 229");
        // 라벨은 한 줄이라 Enter 로 끝내고, 떡메모지는 Ctrl/Cmd 를 함께 눌러야 끝난다
        assertThat(between(textJs, "if (isLabel(item)) {", "commit(item);"))
                .contains("event.preventDefault()");
        assertThat(textJs).contains("event.ctrlKey || event.metaKey");
        // Esc 는 쓰던 것을 버리고, 밖을 누르면 그대로 저장한다
        assertThat(textJs).contains("event.key === 'Escape'").contains("cancel(item)");
        assertThat(between(textJs, "document.addEventListener('focusout'", "});"))
                .contains("commit(item)");
    }

    @Test
    void aNoteIsOnlyTypeableWhileItIsBeingEdited() throws Exception {
        String textJs = Files.readString(
                Path.of("src/main/resources/static/js/diary-note-text.js"));
        String dragJs = Files.readString(
                Path.of("src/main/resources/static/js/diary-canvas-drag.js"));

        // 쓰는 동안에만 붙고, 끝나면 떼어 낸다
        assertThat(textJs)
                .contains("setAttribute('contenteditable', 'plaintext-only')")
                .contains("removeAttribute('contenteditable')");
        // 글자만 들어온다. 붙여넣기로 HTML 이 섞이지 않는다
        assertThat(between(textJs, "document.addEventListener('paste'", "});"))
                .contains("getData('text/plain')")
                .contains("insertText");
        // 글자를 고르려고 끌었을 뿐인데 종이가 따라 움직이면 안 된다
        assertThat(dragJs).contains("item.classList.contains('is-editing')");
    }

    @Test
    void aFailedSaveFallsBackToWhatTheServerLastKnew() throws Exception {
        String textJs = Files.readString(
                Path.of("src/main/resources/static/js/diary-note-text.js"));

        // 화면만 앞서가지 않게 한다
        assertThat(between(textJs, "function restore(", "\n    }"))
                .contains("item.dataset.savedText");
        assertThat(between(textJs, "} catch (error) {", "\n    }"))
                .contains("restore(item, text, previous)");
        // 서버가 다듬어 돌려준 글을 화면에도 그대로 반영한다
        assertThat(textJs).contains("text.textContent = payload.textContent");
    }

    @Test
    void pressingEnterSavesOnceAndKeepsWhatWasTyped() throws Exception {
        String textJs = Files.readString(
                Path.of("src/main/resources/static/js/diary-note-text.js"));

        /*
          편집이 풀리는 순간 브라우저가 focusout 을 그 자리에서 띄운다.
          editing 을 먼저 내려놓아야 그 focusout 이 저장을 한 번 더 시작하지 않는다.
        */
        String close = between(textJs, "function close(item, text)", "\n    }");
        assertThat(close.indexOf("editing = null"))
                .as("editing 을 contenteditable 보다 먼저 내려놓는다")
                .isLessThan(close.indexOf("removeAttribute"));

        /*
          보낼 글을 곧바로 "마지막으로 아는 글" 로 옮긴다.
          늦게 도착한 blur 가 다시 들어와도 바뀐 것이 없어 같은 요청을 두 번 보내지 않는다.
        */
        String commit = between(textJs, "function commit(item)", "\n    }");
        assertThat(commit)
                .contains("if (next === previous) return;")
                .contains("item.dataset.savedText = next;");
        assertThat(commit.indexOf("item.dataset.savedText = next"))
                .isLessThan(commit.indexOf("save(item, text, next, previous)"));
    }

    @Test
    void aFailedSaveGoesBackToTheWordsBeforeThisEditNotToNothing() throws Exception {
        String textJs = Files.readString(
                Path.of("src/main/resources/static/js/diary-note-text.js"));

        // 되돌릴 곳은 이번 편집 직전의 글이다. 빈 값으로 덮어쓰지 않는다
        assertThat(between(textJs, "function restore(item, text, previous)", "\n    }"))
                .contains("const last = previous || '';")
                .contains("text.textContent = last")
                .contains("item.dataset.savedText = last");
    }

    @Test
    void openingTheEditorDoesNotCloseItselfAgain() throws Exception {
        String textJs = Files.readString(
                Path.of("src/main/resources/static/js/diary-note-text.js"));

        /*
          같은 라벨 안의 "글 편집" 을 누르면 그 버튼이 focus 를 잃는다.
          그것까지 편집을 끝낸 것으로 보면 방금 연 편집이 바로 닫힌다.
          글자 칸이 focus 를 잃었을 때만 끝낸다.
        */
        assertThat(between(textJs, "document.addEventListener('focusout'", "});"))
                .contains("event.target !== item.querySelector('.diary-note-text')");
    }

    @Test
    void theWayToWriteOnANoteIsVisibleNotOnlyADoubleClick() throws Exception {
        String body = editPageBodyWithNote();
        String pickerJs = Files.readString(
                Path.of("src/main/resources/static/js/diary-note-picker.js"));
        String textJs = Files.readString(
                Path.of("src/main/resources/static/js/diary-note-text.js"));

        // 고른 라벨 아래 줄에 붙는다. 사진·스티커의 줄과 같은 모양이다
        String actions = between(body, "class=\"diary-layer-actions\"", "</div>");
        assertThat(actions)
                .contains("data-note-edit")
                .contains("글 편집")
                .contains("뒤로")
                .contains("떼기");
        // 화면이 만든 라벨에도 같은 버튼이 붙는다
        assertThat(pickerJs).contains("edit.dataset.noteEdit").contains("'글 편집'");
        // 눌리면 두 번 누른 것과 같은 일을 한다
        assertThat(between(textJs, "document.addEventListener('click'", "});"))
                .contains("closest('[data-note-edit]')")
                .contains("begin(item)");
        // 두 번 누르기도 그대로 남는다
        assertThat(textJs).contains("document.addEventListener('dblclick'");
    }

    @Test
    void readingAPageOffersNoWayToWriteOnANote() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        when(diaryElementService.getElements(10L, 1L, 7L))
                .thenReturn(List.of(noteElement(300L, "MEMO_SQUARE")));

        String readBody = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andReturn().getResponse().getContentAsString();

        // 읽는 화면에는 조작 줄 자체가 없다
        assertThat(between(readBody, "diary-canvas-item diary-note", "</figure>"))
                .doesNotContain("data-note-edit")
                .doesNotContain("diary-layer-actions");
    }

    @Test
    void thePhotoAndStickerActionRowsAreUntouched() throws Exception {
        String template = Files.readString(
                Path.of("src/main/resources/templates/diary/detail.html"));
        String stickerJs = Files.readString(
                Path.of("src/main/resources/static/js/diary-sticker-picker.js"));

        // 글 편집은 라벨/메모지 줄에만 있다
        assertThat(countOf(template, "data-note-edit")).isEqualTo(1);
        assertThat(between(template, "diary-canvas-photo", "</figure>"))
                .doesNotContain("data-note-edit");
        assertThat(between(template, "diary-canvas-sticker", "</figure>"))
                .doesNotContain("data-note-edit");
        assertThat(stickerJs).doesNotContain("noteEdit");
    }

    /** 라벨 한 장이 놓인 편집 화면. */
    private String editPageBodyWithNote() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));
        when(diaryElementService.getElements(10L, 1L, 7L))
                .thenReturn(List.of(noteElement(300L, "DATE_LABEL")));

        return mockMvc.perform(get("/diaries/10").param("edit", "true")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void aFreshNoteOpensReadyToBeTypedInto() throws Exception {
        String pickerJs = Files.readString(
                Path.of("src/main/resources/static/js/diary-note-picker.js"));

        // 붙이자마자 쓸 수 있다. 빈 라벨을 다시 두 번 누르게 하지 않는다
        assertThat(pickerJs).contains("window.diaryNoteText?.begin(item)");
        assertThat(pickerJs).contains("item.dataset.textUrl = note.urls.text");
    }

    /** 좌표/크기는 저장된 뒤의 값이다. (응답을 그리는 데 쓰인다) */
    private DiaryElement noteElement(Long id, String styleType) {
        DiaryElement element = element(id, "NOTE");
        element.setStyleType(styleType);
        element.setTextContent("");
        return element;
    }

    private DiaryElement stickerElement(Long id, String imageUrl) {
        DiaryElement element = element(id, "STICKER");
        element.setImageUrl(imageUrl);
        element.setWidth(new java.math.BigDecimal("0.18000"));
        element.setHeight(new java.math.BigDecimal("0.18000"));
        element.setPositionX(new java.math.BigDecimal("0.41000"));
        element.setPositionY(new java.math.BigDecimal("0.41000"));
        return element;
    }

    /** 좌표/크기는 DB 기본값(NOT NULL)과 같은 값을 채운다. */
    private DiaryElement element(Long id, String elementType) {
        DiaryElement element = new DiaryElement();
        element.setId(id);
        element.setPageId(1L);
        element.setElementType(elementType);
        element.setPositionX(new java.math.BigDecimal("0.00000"));
        element.setPositionY(new java.math.BigDecimal("0.00000"));
        element.setWidth(new java.math.BigDecimal("0.30000"));
        element.setHeight(new java.math.BigDecimal("0.30000"));
        element.setRotation(new java.math.BigDecimal("0.00"));
        element.setZIndex(0);
        return element;
    }

    private DiaryPage page(int order, String date) {
        DiaryPage page = new DiaryPage();
        page.setId((long) order);
        page.setDiaryId(10L);
        page.setPageDate(LocalDate.parse(date));
        page.setPageOrder(order);
        page.setBackgroundType("PLAIN");
        return page;
    }

    /** 그 달 1일에만 여행이 걸쳐 있는 6주짜리 달력. (칸 나누기는 서비스가 한다) */
    private DiaryCalendarDto calendar(YearMonth month, Diary... trips) {
        List<List<DiaryCalendarDto.Day>> weeks = new java.util.ArrayList<>();
        LocalDate cursor = month.atDay(1);
        for (int week = 0; week < 6; week++) {
            List<DiaryCalendarDto.Day> days = new java.util.ArrayList<>();
            for (int day = 0; day < 7; day++) {
                boolean first = week == 0 && day == 0;
                days.add(new DiaryCalendarDto.Day(cursor, true, false,
                        first ? List.of(trips) : List.of()));
                cursor = cursor.plusDays(1);
            }
            weeks.add(days);
        }
        return new DiaryCalendarDto(month, weeks);
    }

    /** 검색어 없는 첫 쪽. (쪽 계산은 서비스가 하므로 화면 확인에는 한 쪽이면 충분하다) */
    private DiaryListPageDto listPage(List<DiaryListItemDto> items) {
        return new DiaryListPageDto(items, "", DiarySort.UPDATED_DESC, 1, 1, items.size(), 12);
    }

    private DiaryListItemDto item() {
        DiaryListItemDto item = new DiaryListItemDto();
        item.setId(10L);
        item.setTitle("여름 제주 여행");
        item.setStartDate(LocalDate.of(2026, 8, 1));
        item.setEndDate(LocalDate.of(2026, 8, 5));
        item.setPageCount(3);
        return item;
    }
}
