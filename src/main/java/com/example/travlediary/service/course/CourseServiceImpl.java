package com.example.travlediary.service.course;

import com.example.travlediary.dto.CourseCreateRequest;
import com.example.travlediary.dto.CourseDetailDto;
import com.example.travlediary.dto.CourseEditDto;
import com.example.travlediary.dto.CourseUpdateRequest;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;
    private final PostContentSanitizer postContentSanitizer;

    @Override
    @Transactional
    public CourseDetailDto getCourseDetail(Long courseId, Long currentUserId) {
        if (courseMapper.incrementViews(courseId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "여행 코스를 찾을 수 없습니다.");
        }

        CourseDetailDto course = courseMapper.findCourseDetail(courseId, currentUserId);
        if (course == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "여행 코스를 찾을 수 없습니다.");
        }

        course.setContent(postContentSanitizer.sanitize(course.getContent()));
        course.setStops(courseMapper.findCourseStops(courseId));
        course.setMyCourse(currentUserId != null && Objects.equals(course.getUserId(), currentUserId));
        return course;
    }

    @Override
    @Transactional(readOnly = true)
    public CourseEditDto getCourseForEdit(Long courseId, Long userId) {
        Course course = requireOwnedActiveCourse(courseMapper.findActiveCourse(courseId), userId);

        CourseEditDto edit = new CourseEditDto();
        edit.setId(course.getId());
        edit.setTitle(course.getTitle());
        edit.setContent(postContentSanitizer.sanitize(course.getContent()));
        edit.setStops(courseMapper.findCourseStops(courseId));
        return edit;
    }

    @Override
    @Transactional
    public Long createCourse(CourseCreateRequest request, Long userId) {
        ValidatedCourse validated = validateBasic(request == null ? null : request.getTitle(),
                request == null ? null : request.getContent(),
                request == null ? null : request.getDestinationIds());
        validateDestinations(validated.destinationIds());

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

    @Override
    @Transactional
    public void updateCourse(Long courseId, Long userId, CourseUpdateRequest request) {
        ValidatedCourse validated = validateBasic(request == null ? null : request.getTitle(),
                request == null ? null : request.getContent(),
                request == null ? null : request.getDestinationIds());

        requireOwnedActiveCourse(courseMapper.findActiveCourseForUpdate(courseId), userId);
        validateDestinations(validated.destinationIds());

        if (courseMapper.updateCourse(courseId, userId, validated.title(), validated.content()) != 1) {
            throw new IllegalStateException("여행 코스 수정에 실패했습니다.");
        }

        courseMapper.deleteCourseDestinations(courseId);
        insertCourseDestinations(courseId, validated.destinationIds());
    }

    @Override
    @Transactional
    public void deleteCourse(Long courseId, Long userId) {
        requireOwnedActiveCourse(courseMapper.findActiveCourseForUpdate(courseId), userId);
        if (courseMapper.softDeleteCourse(courseId, userId) != 1) {
            throw new IllegalStateException("여행 코스 삭제에 실패했습니다.");
        }
    }

    private ValidatedCourse validateBasic(String requestedTitle, String content, List<Long> destinationIds) {
        String title = requestedTitle == null ? "" : requestedTitle.trim();
        if (title.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "코스 제목을 입력해 주세요.");
        }
        if (title.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "코스 제목은 255자 이하로 입력해 주세요.");
        }

        String sanitizedContent = postContentSanitizer.sanitize(content);
        org.jsoup.nodes.Document document = Jsoup.parseBodyFragment(sanitizedContent);
        boolean hasText = !document.text().trim().isEmpty();
        boolean hasImage = !document.select("img[src]").isEmpty();
        if (!hasText && !hasImage) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "코스 소개를 입력해 주세요.");
        }

        if (destinationIds == null || destinationIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "여행지를 한 곳 이상 선택해 주세요.");
        }

        return new ValidatedCourse(title, sanitizedContent, new ArrayList<>(destinationIds));
    }

    private void validateDestinations(List<Long> destinationIds) {
        if (destinationIds.stream().anyMatch(id -> id == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 여행지가 포함되어 있습니다.");
        }
        if (new HashSet<>(destinationIds).size() != destinationIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "같은 여행지는 한 번만 선택할 수 있습니다.");
        }
        if (courseMapper.countExistingDestinations(destinationIds) != destinationIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 여행지가 포함되어 있습니다.");
        }
    }

    private Course requireOwnedActiveCourse(Course course, Long userId) {
        if (course == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "여행 코스를 찾을 수 없습니다.");
        }
        if (!Objects.equals(course.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "여행 코스 변경 권한이 없습니다.");
        }
        return course;
    }

    private void insertCourseDestinations(Long courseId, List<Long> destinationIds) {
        for (int index = 0; index < destinationIds.size(); index++) {
            CourseDestination destination = new CourseDestination();
            destination.setCourseId(courseId);
            destination.setDestinationId(destinationIds.get(index));
            destination.setVisitOrder(index + 1);
            if (courseMapper.insertCourseDestination(destination) != 1) {
                throw new IllegalStateException("코스 여행지 저장에 실패했습니다.");
            }
        }
    }

    private record ValidatedCourse(String title, String content, List<Long> destinationIds) {
    }
}
