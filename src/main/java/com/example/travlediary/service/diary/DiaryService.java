package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryListItemDto;
import com.example.travlediary.model.Diary;

import java.util.List;

public interface DiaryService {

    /** 현재 사용자의 다이어리 목록 */
    List<Diary> getMyDiaries(Long userId);

    /** 일기장형 목록 화면용 (표지 + 페이지 수) */
    List<DiaryListItemDto> getMyDiaryList(Long userId);

    /** 본인 소유 다이어리 상세 */
    Diary getMyDiary(Long diaryId, Long userId);

    /** 다이어리 생성. 소유자는 요청 값이 아니라 현재 사용자로 설정한다. */
    Diary create(Long userId, Diary diary);

    /** 본인 소유 다이어리의 기본정보 수정 */
    Diary update(Long diaryId, Long userId, Diary diary);

    /** 본인 소유 다이어리 삭제 */
    void delete(Long diaryId, Long userId);
}
