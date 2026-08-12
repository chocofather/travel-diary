package com.example.travlediary.repository.course;

import com.example.travlediary.dto.CourseCommentDto;
import com.example.travlediary.model.CourseComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CourseCommentMapper {

    boolean existsActiveCourse(@Param("courseId") Long courseId);

    List<CourseCommentDto> findByCourseId(@Param("courseId") Long courseId,
                                          @Param("currentUserId") Long currentUserId);

    List<CourseCommentDto> findPagedRootComments(@Param("courseId") Long courseId,
                                                 @Param("currentUserId") Long currentUserId,
                                                 @Param("sort") String sort,
                                                 @Param("limit") int limit,
                                                 @Param("offset") int offset);

    List<CourseCommentDto> findRepliesForRootComments(@Param("courseId") Long courseId,
                                                      @Param("currentUserId") Long currentUserId,
                                                      @Param("rootIds") List<Long> rootIds);

    int countRootCommentThreads(@Param("courseId") Long courseId);

    int countActiveComments(@Param("courseId") Long courseId);

    Long findActiveRootIdForLocation(@Param("courseId") Long courseId,
                                     @Param("commentId") Long commentId);

    int countRootCommentsBefore(@Param("courseId") Long courseId,
                                @Param("rootId") Long rootId);

    CourseComment findActiveComment(@Param("commentId") Long commentId);

    CourseComment findActiveCommentForUpdate(@Param("commentId") Long commentId);

    CourseComment findCommentForUpdate(@Param("commentId") Long commentId);

    CourseCommentDto findDtoById(@Param("commentId") Long commentId,
                                 @Param("currentUserId") Long currentUserId);

    int insert(CourseComment comment);

    int updateContent(@Param("commentId") Long commentId,
                      @Param("userId") Long userId,
                      @Param("content") String content);

    int softDelete(@Param("commentId") Long commentId,
                   @Param("userId") Long userId);

    int insertLike(@Param("userId") Long userId,
                   @Param("commentId") Long commentId);

    int deleteLike(@Param("userId") Long userId,
                   @Param("commentId") Long commentId);

    int deleteAllLikesByUserId(@Param("userId") Long userId);
}
