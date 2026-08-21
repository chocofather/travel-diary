package com.example.travlediary.repository.comment;

import com.example.travlediary.model.DestinationCommentImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 여행지 댓글 사진 저장소.
 * 아직 기존 댓글 저장/조회 흐름에는 연결하지 않는다 (STEP C 이후 연결 예정).
 */
@Mapper
public interface DestinationCommentImageMapper {

    /** 사진 1건 저장. */
    int insert(DestinationCommentImage image);

    /**
     * 여러 댓글의 사진을 한 번에 조회한다. (댓글 목록 렌더링 시 N+1 방지)
     * comment_id, display_order 오름차순으로 정렬해 돌려준다.
     */
    List<DestinationCommentImage> findByCommentIds(@Param("commentIds") List<Long> commentIds);

    /**
     * 사진 모아보기용 조회.
     * 삭제·관리자 조치된 댓글(deleted = 1)의 사진은 제외한다.
     */
    List<DestinationCommentImage> findGalleryByDestinationId(
            @Param("destinationId") Long destinationId,
            @Param("limit") int limit);

    /**
     * 여행지 삭제 lifecycle 전용 조회.
     * 파일 정리가 목적이라 삭제된 댓글의 사진까지 전부, 개수 제한 없이 URL 만 돌려준다.
     */
    List<String> findAllImageUrlsByDestinationId(@Param("destinationId") Long destinationId);
}
