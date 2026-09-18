package com.example.travlediary.repository.diary;

import com.example.travlediary.dto.DiaryPrivatePhotoRef;
import com.example.travlediary.model.DiaryElement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 페이지에 올린 요소(TEXT/PHOTO/STICKER/NOTE) 저장소.
 * 소유권은 서비스에서 다이어리 → 페이지 순으로 확인하고, 여기서는 page_id 범위로만 제한한다.
 */
@Mapper
public interface DiaryElementMapper {

    /** 페이지의 요소 전체 (겹침 순서대로) */
    List<DiaryElement> findByPageId(@Param("pageId") Long pageId);

    /** 해당 요소가 그 페이지에 속하는지 함께 확인하는 1건 조회 */
    DiaryElement findByIdAndPageId(@Param("elementId") Long elementId,
                                   @Param("pageId") Long pageId);

    /**
     * 통제된 사진 응답이 쓰는 한 줄. 요소 → 페이지 → 다이어리 관계와 소유권을 SQL 한 번에 본다.
     * 관계가 어긋나거나 남의 다이어리이거나 사진 요소가 아니면 결과가 없다.
     */
    DiaryPrivatePhotoRef findPhotoRef(@Param("diaryId") Long diaryId,
                                      @Param("pageId") Long pageId,
                                      @Param("elementId") Long elementId,
                                      @Param("userId") Long userId);

    /** 요소 등록. 생성된 id 는 element.id 에 채워진다. */
    int insert(DiaryElement element);

    /** 요소 수정 (같은 페이지 안에서만). element_type 은 등록 시 값을 유지한다. */
    int update(DiaryElement element);

    /** 요소 삭제 */
    int delete(@Param("elementId") Long elementId,
               @Param("pageId") Long pageId);
}
