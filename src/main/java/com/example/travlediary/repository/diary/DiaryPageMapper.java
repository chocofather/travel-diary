package com.example.travlediary.repository.diary;

import com.example.travlediary.model.DiaryPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 다이어리 페이지 저장소.
 * diary_pages 에는 user_id 가 없으므로 소유권은 서비스에서 부모 다이어리로 먼저 확인하고,
 * 여기서는 diary_id 범위로만 제한한다.
 */
@Mapper
public interface DiaryPageMapper {

    /** 다이어리의 페이지 전체 (page_order 순서) */
    List<DiaryPage> findByDiaryId(@Param("diaryId") Long diaryId);

    /** 해당 페이지가 그 다이어리에 속하는지 함께 확인하는 1건 조회 */
    DiaryPage findByIdAndDiaryId(@Param("pageId") Long pageId,
                                 @Param("diaryId") Long diaryId);

    /** 다이어리의 마지막 page_order. 페이지가 없으면 null */
    Integer findMaxPageOrder(@Param("diaryId") Long diaryId);

    /** 페이지 등록. 생성된 id 는 page.id 에 채워진다. */
    int insert(DiaryPage page);

    /** 페이지 수정 (같은 다이어리 안에서만) */
    int update(DiaryPage page);

    /** 본문(content)만 수정. 날짜/순서/배경은 건드리지 않는다. */
    int updateContent(@Param("pageId") Long pageId,
                      @Param("diaryId") Long diaryId,
                      @Param("content") String content);

    /** 상단 한 줄 메모(내용/글꼴/굵기)만 수정. 본문/날짜/순서/배경은 건드리지 않는다. */
    int updatePageHeader(@Param("pageId") Long pageId,
                         @Param("diaryId") Long diaryId,
                         @Param("pageHeader") String pageHeader,
                         @Param("pageHeaderFont") String pageHeaderFont,
                         @Param("pageHeaderBold") boolean pageHeaderBold);

    /**
     * 삭제한 자리 뒤의 페이지 순서를 한 칸씩 당긴다.
     * UNIQUE(diary_id, page_order) 충돌이 없도록 작은 순서부터 갱신한다.
     */
    int shiftPageOrdersAfter(@Param("diaryId") Long diaryId,
                             @Param("pageOrder") Integer pageOrder);

    /** 페이지 삭제 (요소는 FK CASCADE 로 함께 지워진다) */
    int delete(@Param("pageId") Long pageId,
               @Param("diaryId") Long diaryId);
}
