package com.example.travlediary.repository.course;

import com.example.travlediary.dto.BoardListDto; // BoardListDto로 import 수정!
import com.example.travlediary.model.Course;
import com.example.travlediary.model.CourseDestination;
import com.example.travlediary.model.CourseImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CourseMapper {

    // 코스 리스트(정렬/페이징)
    List<BoardListDto> findCourses(
            @Param("sort") String sort,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    // 전체 코스 개수(페이징용)
    int countCourses();

    // 코스 등록
    int insertCourse(Course course);

    // 코스 이미지 등록
    int insertCourseImage(CourseImage courseImage);

    // 코스-여행지 연결 등록
    int insertCourseDestination(CourseDestination courseDestination);
}
