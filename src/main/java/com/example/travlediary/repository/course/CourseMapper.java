package com.example.travlediary.repository.course;

import com.example.travlediary.dto.CourseDetailDto;
import com.example.travlediary.dto.CourseDestinationCountryDto;
import com.example.travlediary.dto.CourseStopDto;
import com.example.travlediary.dto.HomePopularCourseDto;
import com.example.travlediary.dto.HomePopularCourseStopDto;
import com.example.travlediary.model.Course;
import com.example.travlediary.model.CourseDestination;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CourseMapper {

    int incrementViews(@Param("courseId") Long courseId);

    CourseDetailDto findCourseDetail(@Param("courseId") Long courseId,
                                     @Param("currentUserId") Long currentUserId);

    Course findActiveCourse(@Param("courseId") Long courseId);

    Course findActiveCourseForUpdate(@Param("courseId") Long courseId);

    List<CourseStopDto> findCourseStops(@Param("courseId") Long courseId);

    List<HomePopularCourseDto> findPopularCourses(@Param("limit") int limit);

    List<HomePopularCourseStopDto> findPopularCourseStops(
            @Param("courseIds") List<Long> courseIds);

    int countExistingDestinations(@Param("destinationIds") List<Long> destinationIds);

    List<CourseDestinationCountryDto> findDestinationCountries(
            @Param("destinationIds") List<Long> destinationIds);

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
