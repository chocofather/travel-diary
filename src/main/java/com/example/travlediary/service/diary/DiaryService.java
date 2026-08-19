package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCalendarDto;
import com.example.travlediary.dto.DiaryListPageDto;
import com.example.travlediary.model.Diary;

import java.time.YearMonth;
import java.util.List;

public interface DiaryService {

    /** 현재 사용자의 다이어리 목록 */
    List<Diary> getMyDiaries(Long userId);

    /**
     * 일기장형 목록 한 쪽 (표지 + 페이지 수).
     * 검색어가 있으면 제목/한 줄 메모/본문에서 찾는다.
     * (검색 결과도 다이어리 한 권 단위다)
     */
    DiaryListPageDto getMyDiaryPage(Long userId, String keyword, int page);

    /** 그 달의 월간 달력. 표시 월과 여행 기간이 겹치는 다이어리만 읽는다. */
    DiaryCalendarDto getMyDiaryCalendar(Long userId, YearMonth month);

    /** 본인 소유 다이어리 상세 */
    Diary getMyDiary(Long diaryId, Long userId);

    /** 다이어리 생성. 소유자는 요청 값이 아니라 현재 사용자로 설정한다. */
    Diary create(Long userId, Diary diary);

    /** 본인 소유 다이어리의 기본정보 수정 */
    Diary update(Long diaryId, Long userId, Diary diary);

    /** 본인 소유 다이어리 삭제 */
    void delete(Long diaryId, Long userId);
}
