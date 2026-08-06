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
}
