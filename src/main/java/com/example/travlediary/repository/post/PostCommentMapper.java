package com.example.travlediary.repository.post;

import com.example.travlediary.dto.PostCommentDto;
import com.example.travlediary.model.PostComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostCommentMapper {

    boolean existsActivePost(@Param("postId") Long postId);

    List<PostCommentDto> findByPostId(@Param("postId") Long postId,
                                      @Param("currentUserId") Long currentUserId);

    PostComment findActiveComment(@Param("commentId") Long commentId);

    PostComment findActiveCommentForUpdate(@Param("commentId") Long commentId);

    PostComment findCommentForUpdate(@Param("commentId") Long commentId);

    PostCommentDto findDtoById(@Param("commentId") Long commentId,
                               @Param("currentUserId") Long currentUserId);

    int insert(PostComment comment);

    int updateContent(@Param("commentId") Long commentId,
                      @Param("userId") Long userId,
                      @Param("content") String content);

    int softDelete(@Param("commentId") Long commentId,
                   @Param("userId") Long userId);
}
