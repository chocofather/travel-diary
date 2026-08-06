package com.example.travlediary.repository.course;

import com.example.travlediary.model.Course;
import com.example.travlediary.model.CourseDestination;
import com.example.travlediary.model.CourseImage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseMapper {

    // 코스 등록
    int insertCourse(Course course);

    // 코스 이미지 등록
    int insertCourseImage(CourseImage courseImage);

    // 코스-여행지 연결 등록
    int insertCourseDestination(CourseDestination courseDestination);
}
