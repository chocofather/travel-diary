package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryPage;

import java.util.List;

public interface DiaryPageService {

    /** 본인 소유 다이어리의 페이지 전체 (page_order 순서) */
    List<DiaryPage> getPages(Long diaryId, Long userId);

    /** 본인 소유 다이어리의 페이지 1건 */
    DiaryPage getPage(Long diaryId, Long pageId, Long userId);

    /** 페이지 생성. 대상 다이어리는 요청 값이 아니라 검증된 diaryId 로 설정한다. */
    DiaryPage create(Long diaryId, Long userId, DiaryPage page);

    /** 마지막 장 뒤에 새 페이지를 붙인다. (page_order 는 서버가 정한다) */
    DiaryPage append(Long diaryId, Long userId, DiaryPage page);

    /** 페이지 수정 (날짜/순서/배경) */
    DiaryPage update(Long diaryId, Long pageId, Long userId, DiaryPage page);

    /** 본문만 저장한다. 날짜/순서/배경은 건드리지 않는다. */
    DiaryPage updateContent(Long diaryId, Long pageId, Long userId, String content);

    /** 날짜 옆 한 줄 메모(내용/글꼴/굵기)만 저장한다. 본문/날짜/순서/배경은 건드리지 않는다. */
    DiaryPage updatePageHeader(Long diaryId, Long pageId, Long userId,
                               String pageHeader, String pageHeaderFont, boolean pageHeaderBold);

    /** 페이지 삭제 */
    void delete(Long diaryId, Long pageId, Long userId);
}
