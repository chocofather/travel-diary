package com.example.travlediary.repository.post;

import com.example.travlediary.model.PostCommentImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 커뮤니티 게시글 댓글 사진 저장소.
 * 아직 기존 댓글 저장/조회 흐름에는 연결하지 않는다.
 */
@Mapper
public interface PostCommentImageMapper {

    /** 사진 1건 저장. */
    int insert(PostCommentImage image);

    /**
     * 여러 댓글의 사진을 한 번에 조회한다. (댓글 목록 렌더링 시 N+1 방지)
     * comment_id, display_order 오름차순으로 정렬해 돌려준다.
     */
    List<PostCommentImage> findByCommentIds(@Param("commentIds") List<Long> commentIds);
}
