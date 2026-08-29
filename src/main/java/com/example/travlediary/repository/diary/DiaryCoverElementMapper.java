package com.example.travlediary.repository.diary;

import com.example.travlediary.model.DiaryCoverElement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 다이어리에 적용된 표지의 요소 저장소.
 * 부모(표지)의 소유권은 서비스가 확인하고, 여기서는 cover_id 로 남의 표지 요소를
 * 건드리지 못하게 한 번 더 막는다.
 */
@Mapper
public interface DiaryCoverElementMapper {

    /** 요소 등록. 생성된 id 는 element.id 에 채워진다. */
    int insert(DiaryCoverElement element);

    /** 요소 1건. (그 표지의 것인지는 cover_id 로 함께 확인한다) */
    DiaryCoverElement findById(@Param("elementId") Long elementId,
                               @Param("coverId") Long coverId);

    /** 한 표지의 요소 전부 (겹침 순서 그대로) */
    List<DiaryCoverElement> findAllByCoverId(@Param("coverId") Long coverId);

    /**
     * 여러 표지의 요소를 한 번에 (목록 화면용).
     * 표지마다 따로 묻지 않으려고 둔 것이다. (빈 목록은 서비스가 먼저 걸러낸다)
     */
    List<DiaryCoverElement> findAllByCoverIds(@Param("coverIds") List<Long> coverIds);

    /** 자리 옮기기 */
    int updatePosition(@Param("elementId") Long elementId,
                       @Param("coverId") Long coverId,
                       @Param("positionX") BigDecimal positionX,
                       @Param("positionY") BigDecimal positionY);

    /** 크기 바꾸기 */
    int updateSize(@Param("elementId") Long elementId,
                   @Param("coverId") Long coverId,
                   @Param("width") BigDecimal width,
                   @Param("height") BigDecimal height);

    /** 돌리기 */
    int updateRotation(@Param("elementId") Long elementId,
                       @Param("coverId") Long coverId,
                       @Param("rotation") BigDecimal rotation);

    /** 겹침 순서 바꾸기 */
    int updateLayer(@Param("elementId") Long elementId,
                    @Param("coverId") Long coverId,
                    @Param("zIndex") Integer zIndex);

    /** 사진의 모습 바꾸기 (PHOTO 전용). 그 칸 하나만 바꾼다. */
    int updatePhotoStyle(@Param("elementId") Long elementId,
                         @Param("coverId") Long coverId,
                         @Param("photoStyle") String photoStyle);

    /** 글 고치기 (NOTE / TEXT) */
    int updateText(@Param("elementId") Long elementId,
                   @Param("coverId") Long coverId,
                   @Param("textContent") String textContent);

    /** 요소 1건 삭제 */
    int deleteById(@Param("elementId") Long elementId,
                   @Param("coverId") Long coverId);

    /** 한 표지의 요소 전부 삭제 */
    int deleteAllByCoverId(@Param("coverId") Long coverId);
}
