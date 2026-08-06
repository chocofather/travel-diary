package com.example.travlediary.service.course;

import com.example.travlediary.dto.CourseCreateRequest;
import com.example.travlediary.dto.CourseDetailDto;
import com.example.travlediary.model.Course;
import com.example.travlediary.model.CourseDestination;
import com.example.travlediary.repository.course.CourseMapper;
import com.example.travlediary.service.post.PostContentSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import org.jsoup.Jsoup;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;
    private final PostContentSanitizer postContentSanitizer;

    @Override
    @Transactional
    public CourseDetailDto getCourseDetail(Long courseId) {
        if (courseMapper.incrementViews(courseId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "여행 코스를 찾을 수 없습니다.");
        }

        CourseDetailDto course = courseMapper.findCourseDetail(courseId);
        if (course == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "여행 코스를 찾을 수 없습니다.");
        }

        course.setContent(postContentSanitizer.sanitize(course.getContent()));
        course.setStops(courseMapper.findCourseStops(courseId));
        return course;
    }

    @Override
    @Transactional
    public Long createCourse(CourseCreateRequest request, Long userId) {
        ValidatedCourse validated = validateBeforeInsert(request);

        Course course = new Course();
        course.setTitle(validated.title());
        course.setContent(validated.content());
        course.setUserId(userId);

        if (courseMapper.insertCourse(course) != 1 || course.getId() == null) {
            throw new IllegalStateException("여행 코스 저장에 실패했습니다.");
        }

        for (int index = 0; index < validated.destinationIds().size(); index++) {
            CourseDestination destination = new CourseDestination();
            destination.setCourseId(course.getId());
            destination.setDestinationId(validated.destinationIds().get(index));
            destination.setVisitOrder(index + 1);
            if (courseMapper.insertCourseDestination(destination) != 1) {
                throw new IllegalStateException("코스 여행지 저장에 실패했습니다.");
            }
        }
        return course.getId();
    }

    private ValidatedCourse validateBeforeInsert(CourseCreateRequest request) {
        String title = request == null || request.getTitle() == null ? "" : request.getTitle().trim();
        if (title.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "코스 제목을 입력해 주세요.");
        }
        if (title.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "코스 제목은 255자 이하로 입력해 주세요.");
        }

        String sanitizedContent = postContentSanitizer.sanitize(request.getContent());
        org.jsoup.nodes.Document document = Jsoup.parseBodyFragment(sanitizedContent);
        boolean hasText = !document.text().trim().isEmpty();
        boolean hasImage = !document.select("img[src]").isEmpty();
        if (!hasText && !hasImage) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "코스 소개를 입력해 주세요.");
        }

        List<Long> destinationIds = request.getDestinationIds();
        if (destinationIds == null || destinationIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "여행지를 한 곳 이상 선택해 주세요.");
        }
        if (destinationIds.stream().anyMatch(id -> id == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 여행지가 포함되어 있습니다.");
        }
        if (new HashSet<>(destinationIds).size() != destinationIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "같은 여행지는 한 번만 선택할 수 있습니다.");
        }
        if (courseMapper.countExistingDestinations(destinationIds) != destinationIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 여행지가 포함되어 있습니다.");
        }

        return new ValidatedCourse(title, sanitizedContent, List.copyOf(destinationIds));
    }

    private record ValidatedCourse(String title, String content, List<Long> destinationIds) {
    }
}
