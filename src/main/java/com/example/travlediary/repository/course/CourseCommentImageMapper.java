package com.example.travlediary.repository.course;

import com.example.travlediary.model.CourseCommentImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 여행 코스 댓글 사진 저장소.
 * 아직 기존 댓글 저장/조회 흐름에는 연결하지 않는다.
 */
@Mapper
public interface CourseCommentImageMapper {

    /** 사진 1건 저장. */
    int insert(CourseCommentImage image);

    /**
     * 여러 댓글의 사진을 한 번에 조회한다. (댓글 목록 렌더링 시 N+1 방지)
     * comment_id, display_order 오름차순으로 정렬해 돌려준다.
     */
    List<CourseCommentImage> findByCommentIds(@Param("commentIds") List<Long> commentIds);
}
