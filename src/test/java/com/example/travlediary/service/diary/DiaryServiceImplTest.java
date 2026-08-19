package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCalendarDto;
import com.example.travlediary.dto.DiaryListPageDto;
import com.example.travlediary.model.Diary;
import com.example.travlediary.model.DiaryCoverStyle;
import com.example.travlediary.repository.diary.DiaryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryServiceImplTest {

    @Mock
    private DiaryMapper diaryMapper;

    private DiaryService diaryService;

    @BeforeEach
    void setUp() {
        diaryService = new DiaryServiceImpl(diaryMapper);
    }

    /** 검색어가 없으면 조건 없이 첫 12권을 읽는다. */
    @Test
    void listPageReadsTwelveDiariesWithoutAKeyword() {
        when(diaryMapper.countListItems(1L, null)).thenReturn(30);
        when(diaryMapper.findListItems(1L, null, 12, 12)).thenReturn(List.of());

        DiaryListPageDto page = diaryService.getMyDiaryPage(1L, "  ", 2);

        assertThat(page.currentPage()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.totalCount()).isEqualTo(30);
        assertThat(page.pageSize()).isEqualTo(12);
        assertThat(page.isSearching()).isFalse();
        verify(diaryMapper).findListItems(1L, null, 12, 12);
    }

    /** 검색어는 LIKE 특수문자를 그대로 찾도록 이스케이프해서 넘긴다. */
    @Test
    void keywordIsEscapedAndWrappedForLike() {
        when(diaryMapper.countListItems(eq(1L), any())).thenReturn(1);
        when(diaryMapper.findListItems(eq(1L), any(), eq(0), eq(12))).thenReturn(List.of());

        diaryService.getMyDiaryPage(1L, "  100%_할인!  ", 1);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(diaryMapper).countListItems(eq(1L), captor.capture());
        assertThat(captor.getValue()).isEqualTo("%100!%!_할인!!%");
    }

    /** 범위를 벗어난 쪽 번호는 있는 쪽으로 맞춘다. */
    @Test
    void pageNumberIsClampedToTheAvailableRange() {
        when(diaryMapper.countListItems(1L, null)).thenReturn(5);
        when(diaryMapper.findListItems(1L, null, 0, 12)).thenReturn(List.of());

        assertThat(diaryService.getMyDiaryPage(1L, null, 9).currentPage()).isEqualTo(1);
        assertThat(diaryService.getMyDiaryPage(1L, null, 0).currentPage()).isEqualTo(1);
    }

    /** 결과가 없으면 목록 조회를 하지 않는다. */
    @Test
    void noResultSkipsTheListQuery() {
        when(diaryMapper.countListItems(eq(1L), any())).thenReturn(0);

        DiaryListPageDto page = diaryService.getMyDiaryPage(1L, "제주", 1);

        assertThat(page.items()).isEmpty();
        assertThat(page.isSearching()).isTrue();
        assertThat(page.keyword()).isEqualTo("제주");
        verify(diaryMapper, never()).findListItems(any(), any(), anyInt(), anyInt());
    }

    /** 달력은 그 달과 겹치는 다이어리만 읽고, 월 경계에 걸친 여행도 그대로 표시한다. */
    @Test
    void calendarReadsOnlyTheDiariesOverlappingTheMonth() {
        Diary trip = diary(LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 3));
        trip.setId(10L);
        when(diaryMapper.findByUserIdAndPeriod(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
                .thenReturn(List.of(trip));

        DiaryCalendarDto calendar = diaryService.getMyDiaryCalendar(1L, YearMonth.of(2026, 8));

        // 6주 × 7칸으로 어느 달이든 높이가 같다
        assertThat(calendar.weeks()).hasSize(6);
        assertThat(calendar.weeks()).allSatisfy(week -> assertThat(week).hasSize(7));
        assertThat(calendar.monthValue()).isEqualTo("2026-08");
        assertThat(calendar.previousMonthValue()).isEqualTo("2026-07");
        assertThat(calendar.nextMonthValue()).isEqualTo("2026-09");

        // 8월 1~3일에만 걸쳐 있다 (7월 29~31일은 이 달 밖)
        assertThat(tripCount(calendar, LocalDate.of(2026, 8, 1))).isEqualTo(1);
        assertThat(tripCount(calendar, LocalDate.of(2026, 8, 3))).isEqualTo(1);
        assertThat(tripCount(calendar, LocalDate.of(2026, 8, 4))).isZero();
        assertThat(calendar.isEmpty()).isFalse();
    }

    /** 같은 날짜에 여러 다이어리가 있으면 모두 담는다. */
    @Test
    void calendarKeepsEveryDiaryOnTheSameDay() {
        Diary jeju = diary(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 5));
        jeju.setId(1L);
        Diary family = diary(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 7));
        family.setId(2L);
        when(diaryMapper.findByUserIdAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(jeju, family));

        DiaryCalendarDto calendar = diaryService.getMyDiaryCalendar(1L, YearMonth.of(2026, 8));

        assertThat(tripCount(calendar, LocalDate.of(2026, 8, 5))).isEqualTo(2);
        assertThat(tripCount(calendar, LocalDate.of(2026, 8, 7))).isEqualTo(1);
    }

    @Test
    void calendarWithoutAMonthUsesThisMonth() {
        when(diaryMapper.findByUserIdAndPeriod(eq(1L), any(), any())).thenReturn(List.of());

        DiaryCalendarDto calendar = diaryService.getMyDiaryCalendar(1L, null);

        assertThat(calendar.month()).isEqualTo(YearMonth.now());
        assertThat(calendar.isEmpty()).isTrue();
    }

    private int tripCount(DiaryCalendarDto calendar, LocalDate date) {
        return calendar.weeks().stream().flatMap(List::stream)
                .filter(day -> day.date().isEqual(date))
                .findFirst()
                .map(day -> day.diaries().size())
                .orElseThrow();
    }

    @Test
    void createRejectsYearWithMoreThanFourDigits() {
        Diary diary = diary(LocalDate.of(202526, 8, 1), LocalDate.of(202526, 8, 5));

        assertThatThrownBy(() -> diaryService.create(1L, diary))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("여행 기간의 연도는 4자리");
        verify(diaryMapper, never()).insert(any());
    }

    @Test
    void createRejectsYearBelowFourDigits() {
        Diary diary = diary(LocalDate.of(999, 8, 1), LocalDate.of(999, 8, 5));

        assertThatThrownBy(() -> diaryService.create(1L, diary))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("여행 기간의 연도는 4자리");
        verify(diaryMapper, never()).insert(any());
    }

    @Test
    void createStillRejectsEndDateBeforeStartDate() {
        Diary diary = diary(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 1));

        assertThatThrownBy(() -> diaryService.create(1L, diary))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("여행 종료일은 시작일 이후여야 합니다.");
        verify(diaryMapper, never()).insert(any());
    }

    @Test
    void createRejectsAnUnknownCoverStyle() {
        Diary diary = diary(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5));
        diary.setCoverStyle("GOLD_PLATED");

        assertThatThrownBy(() -> diaryService.create(1L, diary))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("표지 스타일을 다시 선택해 주세요.");
        verify(diaryMapper, never()).insert(any());
    }

    /** 표지를 고르지 않은 예전 흐름은 기본 표지로 남는다. */
    @Test
    void createWithoutACoverStyleFallsBackToTheDefault() {
        Diary diary = diary(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5));
        when(diaryMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, Diary.class).setId(5L);
            return 1;
        });
        when(diaryMapper.findByIdAndUserId(5L, 1L)).thenReturn(new Diary());

        diaryService.create(1L, diary);

        ArgumentCaptor<Diary> captor = ArgumentCaptor.forClass(Diary.class);
        verify(diaryMapper).insert(captor.capture());
        assertThat(captor.getValue().getCoverStyle()).isEqualTo("DEFAULT");
    }

    @Test
    void createKeepsEverySupportedCoverStyle() {
        for (DiaryCoverStyle style : DiaryCoverStyle.values()) {
            assertThat(DiaryCoverStyle.isSupported(style.getCode())).isTrue();
        }
        assertThat(DiaryCoverStyle.isSupported("LEATHER_PURPLE")).isFalse();
        assertThat(DiaryCoverStyle.isSupported(null)).isFalse();
        assertThat(DiaryCoverStyle.LEATHER_DARK_BROWN.getCssClass())
                .isEqualTo("diary-cover-leather-dark-brown");
    }

    private Diary diary(LocalDate startDate, LocalDate endDate) {
        Diary diary = new Diary();
        diary.setTitle("여행일기");
        diary.setStartDate(startDate);
        diary.setEndDate(endDate);
        return diary;
    }
}
