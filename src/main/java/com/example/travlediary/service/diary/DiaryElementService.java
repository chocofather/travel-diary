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

    /**
     * 라벨기로 글씨(TEXT)를 붙인다. 종이 배경 없이 글자만 놓이는 자유배치 요소다.
     *
     * <p>문구는 한 줄로 다듬어 저장하고 빈 문구는 막는다. 글꼴은 라벨 글꼴 목록에 있는
     * 값만, 글자색은 #RRGGBB 형식만 저장하며, 고르지 않았으면 비워 두고 화면이
     * 기본 글꼴·기본 먹색으로 그린다. (글꼴과 글자색은 서로 독립이다)
     * 자리·크기·회전·겹침 순서는 스티커/떡메모지와 같은 규칙으로 서버가 정한다.
     */
    DiaryElement createLabel(Long diaryId, Long pageId, Long userId,
                             String text, String textFont, String textColor);

    /**
     * 라벨기로 붙인 글씨를 뗀다.
     * 파일을 갖지 않으므로 DB 행만 지운다. 다른 유형의 요소 번호는 여기서 걸린다.
     */
    void deleteLabel(Long diaryId, Long pageId, Long elementId, Long userId);

    /** 요소 수정. 유형(TEXT/PHOTO/STICKER/NOTE)은 등록 시 값을 유지한다. */
    DiaryElement update(Long diaryId, Long pageId, Long elementId, Long userId, DiaryElement element);

    /**
     * 라벨/떡메모지의 글만 바꾼다. 디자인·위치·크기·회전·겹침 순서는 기존 값을 그대로 둔다.
     * 빈 글도 그대로 저장된다. (아직 아무것도 쓰지 않은 라벨이 남을 수 있다)
     */
    DiaryElement updateNoteText(Long diaryId, Long pageId, Long elementId, Long userId,
                                String textContent);

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
