package com.example.travlediary.repository.diary;

import com.example.travlediary.model.DiaryCoverDesign;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 내 표지 디자인 보관함 저장소.
 * 수정/삭제 SQL 에도 user_id 조건을 넣어 서비스 검증과 함께 소유권을 이중으로 지킨다.
 */
@Mapper
public interface DiaryCoverDesignMapper {

    /** 디자인 등록. 생성된 id 는 design.id 에 채워진다. */
    int insert(DiaryCoverDesign design);

    /** 번호로 1건. (소유권은 확인하지 않는다 — 서비스가 확인한 뒤에만 쓴다) */
    DiaryCoverDesign findById(@Param("designId") Long designId);

    /** 본인 소유 디자인 1건 */
    DiaryCoverDesign findByIdAndUserId(@Param("designId") Long designId,
                                       @Param("userId") Long userId);

    /** 회원이 가진 디자인 목록 (최근 수정한 것부터) */
    List<DiaryCoverDesign> findAllByUserId(@Param("userId") Long userId);

    /** 본인 소유 디자인 수정 (이름 / 바탕 표지 / 바탕색) */
    int update(DiaryCoverDesign design);

    /** 본인 소유 디자인 삭제 (요소는 FK CASCADE 로 함께 지워진다) */
    int deleteByIdAndUserId(@Param("designId") Long designId,
                            @Param("userId") Long userId);
}
