package com.example.travlediary.service.course;

import com.example.travlediary.model.Course;
import com.example.travlediary.model.CourseDestination;
import com.example.travlediary.model.CourseImage;

import java.util.List;

public interface CourseService {

    /**
     * 나만의 여행코스 등록 (코스, 이미지, 코스-여행지 연동까지 한 번에)
     * @param course         코스 기본정보
     * @param images         이미지 리스트 (없으면 null/빈 리스트)
     * @param destinations   여행지 연결 리스트 (없으면 null/빈 리스트)
     * @return 생성된 코스 id
     */
    Long createCourse(Course course, List<CourseImage> images, List<CourseDestination> destinations);

}
