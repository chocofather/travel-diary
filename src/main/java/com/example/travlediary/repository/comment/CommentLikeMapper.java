package com.example.travlediary.repository.comment;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommentLikeMapper {
    // 좋아요 추가
    void insert(@Param("userId") Long userId, @Param("commentId") Long commentId);

    // 좋아요 취소
    void delete(@Param("userId") Long userId, @Param("commentId") Long commentId);

    // 해당 유저가 이미 좋아요 눌렀는지 확인
    boolean exists(@Param("userId") Long userId, @Param("commentId") Long commentId);
}
