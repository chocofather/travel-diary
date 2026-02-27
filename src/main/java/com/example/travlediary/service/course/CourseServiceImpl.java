package com.example.travlediary.service.course;

import com.example.travlediary.model.Course;
import com.example.travlediary.model.CourseDestination;
import com.example.travlediary.model.CourseImage;
import com.example.travlediary.repository.course.CourseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;

    @Override
    @Transactional
    public Long createCourse(Course course, List<CourseImage> images, List<CourseDestination> destinations) {
        // 1. 코스 등록
        courseMapper.insertCourse(course); // useGeneratedKeys로 course.id 채워짐

        // 2. 이미지 등록
        if (images != null && !images.isEmpty()) {
            for (CourseImage img : images) {
                img.setCourseId(course.getId());
                courseMapper.insertCourseImage(img);
            }
        }

        // 3. 코스-여행지 연결 등록
        if (destinations != null && !destinations.isEmpty()) {
            for (CourseDestination dest : destinations) {
                dest.setCourseId(course.getId());
                courseMapper.insertCourseDestination(dest);
            }
        }

        return course.getId();
    }
}
