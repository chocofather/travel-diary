package com.example.travlediary.repository.diary;

import com.example.travlediary.model.DiaryCoverDesignElement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 저장해 둔 표지 디자인의 요소 저장소.
 * 부모(디자인)의 소유권은 서비스가 확인하고, 여기서는 design_id 로 남의 디자인 요소를
 * 건드리지 못하게 한 번 더 막는다.
 */
@Mapper
public interface DiaryCoverDesignElementMapper {

    /** 요소 등록. 생성된 id 는 element.id 에 채워진다. */
    int insert(DiaryCoverDesignElement element);

    /** 요소 1건. (그 디자인의 것인지는 design_id 로 함께 확인한다) */
    DiaryCoverDesignElement findById(@Param("elementId") Long elementId,
                                     @Param("designId") Long designId);

    /** 한 디자인의 요소 전부 (겹침 순서 그대로) */
    List<DiaryCoverDesignElement> findAllByDesignId(@Param("designId") Long designId);

    /**
     * 여러 디자인의 요소를 한 번에 (보관함 목록의 미리보기용).
     * 디자인마다 따로 묻지 않으려고 둔 것이다. 남의 디자인이 섞이지 않도록
     * 디자인 쪽과 이어 붙여 user_id 로도 막는다. (빈 목록은 서비스가 먼저 걸러낸다)
     */
    List<DiaryCoverDesignElement> findAllByDesignIds(@Param("designIds") List<Long> designIds,
                                                     @Param("userId") Long userId);

    /** 자리 옮기기 */
    int updatePosition(@Param("elementId") Long elementId,
                       @Param("designId") Long designId,
                       @Param("positionX") BigDecimal positionX,
                       @Param("positionY") BigDecimal positionY);

    /** 크기 바꾸기 */
    int updateSize(@Param("elementId") Long elementId,
                   @Param("designId") Long designId,
                   @Param("width") BigDecimal width,
                   @Param("height") BigDecimal height);

    /** 돌리기 */
    int updateRotation(@Param("elementId") Long elementId,
                       @Param("designId") Long designId,
                       @Param("rotation") BigDecimal rotation);

    /** 겹침 순서 바꾸기 */
    int updateLayer(@Param("elementId") Long elementId,
                    @Param("designId") Long designId,
                    @Param("zIndex") Integer zIndex);

    /** 사진의 모습 바꾸기 (PHOTO 전용). 그 칸 하나만 바꾼다. */
    int updatePhotoStyle(@Param("elementId") Long elementId,
                         @Param("designId") Long designId,
                         @Param("photoStyle") String photoStyle);

    /** 글 고치기 (NOTE / TEXT) */
    int updateText(@Param("elementId") Long elementId,
                   @Param("designId") Long designId,
                   @Param("textContent") String textContent);

    /** 요소 1건 삭제 */
    int deleteById(@Param("elementId") Long elementId,
                   @Param("designId") Long designId);

    /** 한 디자인의 요소 전부 삭제 */
    int deleteAllByDesignId(@Param("designId") Long designId);
}
