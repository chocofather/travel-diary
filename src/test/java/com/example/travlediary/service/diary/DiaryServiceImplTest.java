package com.example.travlediary.service.diary;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
