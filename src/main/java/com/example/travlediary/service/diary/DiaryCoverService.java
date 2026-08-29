package com.example.travlediary.service.diary;

import com.example.travlediary.model.Diary;
import com.example.travlediary.model.DiaryCover;
import com.example.travlediary.model.DiaryCoverElement;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 다이어리에 적용된 커스텀 표지.
 *
 * <p>표지 행이 있으면 커스텀 표지이고, 없으면 예전 그대로 기본 표지
 * (diaries.cover_style + cover_image_url)를 쓰는 다이어리다. 그래서 조회는 Optional 이다.
 * 소유권은 diary_covers 가 아니라 diaries.user_id 로 확인한다.
 */
public interface DiaryCoverService {

    /** 그 다이어리의 커스텀 표지. 없으면 비어 있다. (기본 표지를 쓰는 다이어리) */
    Optional<DiaryCover> findMyCover(Long diaryId, Long userId);

    /** 커스텀 표지가 반드시 있어야 하는 자리에서 쓴다. 없으면 404 다. */
    DiaryCover getMyCover(Long diaryId, Long userId);

    /** 표지에 올린 요소 전체 (겹침 순서 그대로) */
    List<DiaryCoverElement> getElements(Long diaryId, Long userId);

    /**
     * 여러 다이어리의 커스텀 표지와 그 요소를 한 번에 읽는다. (목록 화면용)
     * 커스텀 표지를 쓰지 않는 다이어리는 결과에 없다 — 그런 다이어리는 기본 표지를 그린다.
     */
    Map<Long, DiaryCover> findCoversByDiary(List<Long> diaryIds, Long userId);

    /** 표지 번호별 요소 묶음. 위 조회 결과와 함께 쓴다. */
    Map<Long, List<DiaryCoverElement>> findElementsByCover(Collection<DiaryCover> covers);

    /**
     * 새 여행일기를 만들면서 고른 표지 디자인을 함께 입힌다.
     *
     * <p>다이어리만 만들어지고 표지가 빠지는 일이 없도록 한 트랜잭션으로 묶는다.
     * 표지는 원본 디자인을 가리키지 않고 값을 복사해 만든 독립본이라,
     * 나중에 원본을 고치거나 지워도 이 표지는 그대로 남는다.
     */
    Diary createWithDesign(Long userId, Diary diary, Long designId);

    /**
     * 저장해 둔 디자인을 다이어리에 입힌다. (값을 복사한다)
     * 사진은 파일까지 새로 복사하고, 스티커는 공용 asset 이라 경로만 옮긴다.
     */
    DiaryCover applyDesign(Long diaryId, Long designId, Long userId);

    /** 표지 기본 정보 수정 (바탕 표지 / 바탕색). 요소는 건드리지 않는다. */
    DiaryCover update(Long diaryId, Long userId, String baseCoverStyle, String backgroundColor);

    /**
     * 커스텀 표지 제거. 이 행이 사라지면 다시 기본 표지로 돌아간다.
     * 요소 행은 FK CASCADE 로 함께 지워지고, 사진 파일 정리는 호출한 쪽의 몫이라
     * 지우기 전 요소 목록을 돌려준다.
     */
    List<DiaryCoverElement> delete(Long diaryId, Long userId);
}
