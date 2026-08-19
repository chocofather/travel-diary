package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCalendarDto;
import com.example.travlediary.dto.DiaryListItemDto;
import com.example.travlediary.dto.DiaryListPageDto;
import com.example.travlediary.model.Diary;
import com.example.travlediary.model.DiaryCoverStyle;
import com.example.travlediary.repository.diary.DiaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DiaryServiceImpl implements DiaryService {

    private static final int MAX_TITLE_LENGTH = 150;
    /** 연도는 4자리만 허용한다. (브라우저 date input 의 min/max 와 같은 범위) */
    private static final int MIN_YEAR = 1000;
    private static final int MAX_YEAR = 9999;
    /** 표지 스타일 기본값 (DB 기본값과 같은 값) */
    private static final String DEFAULT_COVER_STYLE = DiaryCoverStyle.DEFAULT.getCode();
    /** 목록 한 쪽에 보여주는 다이어리 수 */
    private static final int PAGE_SIZE = 12;
    /** 월간 달력은 어느 달이든 같은 높이가 되도록 6주로 그린다. */
    private static final int CALENDAR_WEEKS = 6;
    /** 검색어 길이 상한 (제목 길이와 같은 선) */
    private static final int MAX_KEYWORD_LENGTH = MAX_TITLE_LENGTH;

    private final DiaryMapper diaryMapper;

    @Override
    @Transactional(readOnly = true)
    public List<Diary> getMyDiaries(Long userId) {
        requireUser(userId);
        return diaryMapper.findByUserId(userId);
    }

    /**
     * 목록 한 쪽. 검색어가 있으면 제목/한 줄 메모/본문에서 찾는다.
     * 소유권은 SQL 의 user_id 조건이 함께 막는다.
     */
    @Override
    @Transactional(readOnly = true)
    public DiaryListPageDto getMyDiaryPage(Long userId, String keyword, int page) {
        requireUser(userId);

        String searched = keyword == null ? "" : keyword.strip();
        if (searched.length() > MAX_KEYWORD_LENGTH) {
            searched = searched.substring(0, MAX_KEYWORD_LENGTH);
        }
        String pattern = searched.isEmpty() ? null : likePattern(searched);

        int totalCount = diaryMapper.countListItems(userId, pattern);
        // 결과가 없어도 1쪽은 있는 것으로 본다. (쪽 번호는 1부터)
        int totalPages = Math.max(1, (totalCount + PAGE_SIZE - 1) / PAGE_SIZE);
        int currentPage = Math.min(Math.max(page, 1), totalPages);

        List<DiaryListItemDto> items = totalCount == 0
                ? List.of()
                : diaryMapper.findListItems(userId, pattern, (currentPage - 1) * PAGE_SIZE, PAGE_SIZE);
        return new DiaryListPageDto(items, searched, currentPage, totalPages, totalCount, PAGE_SIZE);
    }

    /**
     * 월간 달력 한 장.
     * 표시 월과 날짜가 겹치는 다이어리만 한 번 읽고, 화면이 그대로 그릴 수 있게 주 단위로 나눈다.
     */
    @Override
    @Transactional(readOnly = true)
    public DiaryCalendarDto getMyDiaryCalendar(Long userId, YearMonth month) {
        requireUser(userId);
        YearMonth target = month == null ? YearMonth.now() : month;

        LocalDate firstDay = target.atDay(1);
        LocalDate lastDay = target.atEndOfMonth();
        List<Diary> diaries = diaryMapper.findByUserIdAndPeriod(userId, firstDay, lastDay);

        // 일요일에서 시작해 6주(42칸)를 채운다. 앞뒤 칸은 옆 달 날짜다.
        LocalDate cursor = firstDay.minusDays(firstDay.getDayOfWeek().getValue() % 7);
        LocalDate today = LocalDate.now();

        List<List<DiaryCalendarDto.Day>> weeks = new ArrayList<>();
        for (int week = 0; week < CALENDAR_WEEKS; week++) {
            List<DiaryCalendarDto.Day> days = new ArrayList<>();
            for (int day = 0; day < 7; day++) {
                days.add(new DiaryCalendarDto.Day(
                        cursor,
                        YearMonth.from(cursor).equals(target),
                        cursor.isEqual(today),
                        diariesOn(diaries, cursor)));
                cursor = cursor.plusDays(1);
            }
            weeks.add(List.copyOf(days));
        }
        return new DiaryCalendarDto(target, List.copyOf(weeks));
    }

    /** 그 날짜에 걸쳐 있는 다이어리. (여행 기간 안이면 모두 담는다) */
    private List<Diary> diariesOn(List<Diary> diaries, LocalDate date) {
        List<Diary> onDate = new ArrayList<>();
        for (Diary diary : diaries) {
            if (!date.isBefore(diary.getStartDate()) && !date.isAfter(diary.getEndDate())) {
                onDate.add(diary);
            }
        }
        return List.copyOf(onDate);
    }

    /** LIKE 특수문자를 그대로 찾도록 이스케이프한 뒤 양쪽을 % 로 감싼다. (SQL 의 ESCAPE '!' 와 짝) */
    private String likePattern(String keyword) {
        String escaped = keyword
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    @Override
    @Transactional(readOnly = true)
    public Diary getMyDiary(Long diaryId, Long userId) {
        return requireOwnedDiary(diaryId, userId);
    }

    @Override
    @Transactional
    public Diary create(Long userId, Diary diary) {
        requireUser(userId);
        Diary prepared = validated(diary);
        // 소유자는 요청 값을 믿지 않고 현재 사용자로 설정한다.
        prepared.setUserId(userId);

        if (diaryMapper.insert(prepared) != 1 || prepared.getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "다이어리를 저장하지 못했습니다.");
        }
        return requireOwnedDiary(prepared.getId(), userId);
    }

    @Override
    @Transactional
    public Diary update(Long diaryId, Long userId, Diary diary) {
        Diary existing = requireOwnedDiary(diaryId, userId);
        Diary prepared = validated(diary);
        // 대상과 소유자는 요청 값이 아니라 검증된 값으로 고정한다. (소유자 변경 불가)
        prepared.setId(existing.getId());
        prepared.setUserId(existing.getUserId());

        if (diaryMapper.update(prepared) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "다이어리를 찾을 수 없습니다.");
        }
        return requireOwnedDiary(existing.getId(), userId);
    }

    @Override
    @Transactional
    public void delete(Long diaryId, Long userId) {
        requireOwnedDiary(diaryId, userId);
        // 페이지/요소 행은 FK ON DELETE CASCADE 로 함께 삭제된다.
        if (diaryMapper.delete(diaryId, userId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "다이어리를 찾을 수 없습니다.");
        }
    }

    /**
     * 본인 소유 다이어리만 통과시킨다.
     * 없는 다이어리와 남의 다이어리를 같은 404 로 처리해 존재 여부가 드러나지 않게 한다.
     */
    private Diary requireOwnedDiary(Long diaryId, Long userId) {
        requireUser(userId);
        Diary diary = diaryId == null ? null : diaryMapper.findByIdAndUserId(diaryId, userId);
        if (diary == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "다이어리를 찾을 수 없습니다.");
        }
        return diary;
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    /** 연도가 4자리 범위를 벗어났는지 확인한다. */
    private boolean isYearOutOfRange(LocalDate date) {
        return date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR;
    }

    /** 저장 전 입력값 검증. 원본을 건드리지 않고 정리된 값을 담은 Diary 를 돌려준다. */
    private Diary validated(Diary diary) {
        if (diary == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "다이어리 정보를 입력해 주세요.");
        }

        String title = diary.getTitle() == null ? "" : diary.getTitle().strip();
        if (title.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목을 입력해 주세요.");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목은 150자 이하로 입력해 주세요.");
        }

        LocalDate startDate = diary.getStartDate();
        LocalDate endDate = diary.getEndDate();
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "여행 기간을 입력해 주세요.");
        }
        if (isYearOutOfRange(startDate) || isYearOutOfRange(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "여행 기간의 연도는 4자리(1000 ~ 9999)로 입력해 주세요.");
        }
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "여행 종료일은 시작일 이후여야 합니다.");
        }

        String coverImageUrl = diary.getCoverImageUrl() == null ? null : diary.getCoverImageUrl().strip();
        // 고르지 않았으면 기본 표지로 두고, 값이 왔다면 아는 표지 스타일만 통과시킨다.
        String coverStyle = diary.getCoverStyle() == null ? "" : diary.getCoverStyle().strip();
        if (coverStyle.isEmpty()) {
            coverStyle = DEFAULT_COVER_STYLE;
        } else if (!DiaryCoverStyle.isSupported(coverStyle)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "표지 스타일을 다시 선택해 주세요.");
        }

        Diary prepared = new Diary();
        prepared.setTitle(title);
        prepared.setStartDate(startDate);
        prepared.setEndDate(endDate);
        prepared.setCoverImageUrl(coverImageUrl == null || coverImageUrl.isEmpty() ? null : coverImageUrl);
        prepared.setCoverStyle(coverStyle);
        return prepared;
    }
}
