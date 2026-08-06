package com.example.travlediary.repository.course;

import com.example.travlediary.dto.CourseDetailDto;
import com.example.travlediary.dto.CourseStopDto;
import com.example.travlediary.model.Course;
import com.example.travlediary.model.CourseDestination;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CourseMapper {

    int incrementViews(@Param("courseId") Long courseId);

    CourseDetailDto findCourseDetail(@Param("courseId") Long courseId);

    Course findActiveCourse(@Param("courseId") Long courseId);

    Course findActiveCourseForUpdate(@Param("courseId") Long courseId);

    List<CourseStopDto> findCourseStops(@Param("courseId") Long courseId);

    int countExistingDestinations(@Param("destinationIds") List<Long> destinationIds);

    // 코스 등록
    int insertCourse(Course course);

    // 코스-여행지 연결 등록
    int insertCourseDestination(CourseDestination courseDestination);

    int updateCourse(@Param("courseId") Long courseId,
                     @Param("userId") Long userId,
                     @Param("title") String title,
                     @Param("content") String content);

    int deleteCourseDestinations(@Param("courseId") Long courseId);

    int softDeleteCourse(@Param("courseId") Long courseId,
                         @Param("userId") Long userId);
}
