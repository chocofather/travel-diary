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

    List<PostCommentDto> findPagedRootComments(@Param("postId") Long postId,
                                               @Param("currentUserId") Long currentUserId,
                                               @Param("sort") String sort,
                                               @Param("limit") int limit,
                                               @Param("offset") int offset);

    List<PostCommentDto> findRepliesForRootComments(@Param("postId") Long postId,
                                                    @Param("currentUserId") Long currentUserId,
                                                    @Param("rootIds") List<Long> rootIds);

    int countRootCommentThreads(@Param("postId") Long postId);

    int countActiveComments(@Param("postId") Long postId);

    Long findActiveRootIdForLocation(@Param("postId") Long postId,
                                     @Param("commentId") Long commentId);

    int countRootCommentsBefore(@Param("postId") Long postId,
                                @Param("rootId") Long rootId);

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

    int insertLike(@Param("userId") Long userId,
                   @Param("commentId") Long commentId);

    int deleteLike(@Param("userId") Long userId,
                   @Param("commentId") Long commentId);
}
