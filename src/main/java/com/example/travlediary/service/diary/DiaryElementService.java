package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryElement;

import java.math.BigDecimal;
import java.util.List;

public interface DiaryElementService {

    /** 페이지의 요소 전체 (겹침 순서대로) */
    List<DiaryElement> getElements(Long diaryId, Long pageId, Long userId);

    /** 페이지의 요소 1건 */
    DiaryElement getElement(Long diaryId, Long pageId, Long elementId, Long userId);

    /** 요소 생성. 대상 페이지는 요청 값이 아니라 검증된 pageId 로 설정한다. */
    DiaryElement create(Long diaryId, Long pageId, Long userId, DiaryElement element);

    /** 요소 수정. 유형(TEXT/PHOTO)은 등록 시 값을 유지한다. */
    DiaryElement update(Long diaryId, Long pageId, Long elementId, Long userId, DiaryElement element);

    /** 위치만 옮긴다. 내용·크기·회전·겹침 순서는 기존 값을 그대로 둔다. */
    DiaryElement move(Long diaryId, Long pageId, Long elementId, Long userId,
                      BigDecimal positionX, BigDecimal positionY);

    /** 크기만 바꾼다. 내용·위치·회전·겹침 순서는 기존 값을 그대로 둔다. */
    DiaryElement resize(Long diaryId, Long pageId, Long elementId, Long userId,
                        BigDecimal width, BigDecimal height);

    /** 회전 각도만 바꾼다. 내용·위치·크기·겹침 순서는 기존 값을 그대로 둔다. */
    DiaryElement rotate(Long diaryId, Long pageId, Long elementId, Long userId,
                        BigDecimal rotation);

    /**
     * 겹침 순서를 한 단계 앞/뒤로 옮긴다.
     * 페이지 요소를 0부터 다시 번호 매겨 정리한 목록을 돌려준다.
     */
    List<DiaryElement> changeLayer(Long diaryId, Long pageId, Long elementId, Long userId,
                                   boolean forward);

    /** 요소 삭제 */
    void delete(Long diaryId, Long pageId, Long elementId, Long userId);
}
