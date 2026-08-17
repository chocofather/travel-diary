package com.example.travlediary.repository.diary;

import com.example.travlediary.model.DiaryElement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 페이지에 올린 요소(TEXT/PHOTO) 저장소.
 * 소유권은 서비스에서 다이어리 → 페이지 순으로 확인하고, 여기서는 page_id 범위로만 제한한다.
 */
@Mapper
public interface DiaryElementMapper {

    /** 페이지의 요소 전체 (겹침 순서대로) */
    List<DiaryElement> findByPageId(@Param("pageId") Long pageId);

    /** 해당 요소가 그 페이지에 속하는지 함께 확인하는 1건 조회 */
    DiaryElement findByIdAndPageId(@Param("elementId") Long elementId,
                                   @Param("pageId") Long pageId);

    /** 요소 등록. 생성된 id 는 element.id 에 채워진다. */
    int insert(DiaryElement element);

    /** 요소 수정 (같은 페이지 안에서만). element_type 은 등록 시 값을 유지한다. */
    int update(DiaryElement element);

    /** 요소 삭제 */
    int delete(@Param("elementId") Long elementId,
               @Param("pageId") Long pageId);
}
