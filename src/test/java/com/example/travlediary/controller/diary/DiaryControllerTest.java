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
import com.example.travlediary.service.diary.DiaryElementService;
import com.example.travlediary.service.diary.DiaryPageService;
import com.example.travlediary.service.diary.DiaryService;
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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
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
@Import({SecurityConfig.class, DiaryStickerCatalog.class})
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
        // 새 페이지 추가는 읽기 상단 액션에 있다 (편집 캔버스가 아니라 책 관리 쪽)
        assertThat(body).contains("diary-page-add");
        assertThat(body).contains("새 페이지 추가");
        assertThat(body).contains("action=\"/diaries/10/pages\"");
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
        assertThat(body).contains("aria-label=\"사진 추가\"");
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
    void onlyTheReadSpreadHangsTheSilkRibbonFromItsSpine() throws Exception {
        when(userDetails.getId()).thenReturn(7L);
        when(diaryService.getMyDiary(10L, 7L)).thenReturn(diary());
        when(diaryPageService.getPages(10L, 7L)).thenReturn(List.of(page(1, "2026-08-01")));

        String readBody = mockMvc.perform(get("/diaries/10")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                userDetails, null, List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 리본은 제본선(gutter) 안에 있는 장식이라 읽는 데 걸리지 않는다
        assertThat(readBody).contains("diary-book-ribbon");
        assertThat(readBody.indexOf("diary-book-gutter"))
                .isLessThan(readBody.indexOf("diary-book-ribbon"));
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
