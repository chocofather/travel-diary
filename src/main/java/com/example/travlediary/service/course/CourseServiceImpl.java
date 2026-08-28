package com.example.travlediary.service.course;

import com.example.travlediary.dto.CourseCreateRequest;
import com.example.travlediary.dto.CourseDetailDto;
import com.example.travlediary.dto.CourseDestinationCountryDto;
import com.example.travlediary.dto.CourseEditDto;
import com.example.travlediary.dto.CourseUpdateRequest;
import com.example.travlediary.dto.HomePopularCourseDto;
import com.example.travlediary.dto.HomePopularCourseStopDto;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private static final int HOME_POPULAR_COURSE_LIMIT = 3;
    private static final int HOME_COURSE_PREVIEW_STOP_LIMIT = 3;

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
        edit.setCountryId(course.getCountryId());
        edit.setCountryName(course.getCountryName());
        edit.setTitle(course.getTitle());
        edit.setContent(postContentSanitizer.sanitize(course.getContent()));
        edit.setStops(courseMapper.findCourseStops(courseId));
        return edit;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HomePopularCourseDto> getPopularCoursesForHome() {
        List<HomePopularCourseDto> courses = courseMapper
                .findPopularCourses(HOME_POPULAR_COURSE_LIMIT)
                .stream()
                .limit(HOME_POPULAR_COURSE_LIMIT)
                .toList();
        if (courses.isEmpty()) {
            return courses;
        }

        Map<Long, List<String>> previewNamesByCourseId = new LinkedHashMap<>();
        for (HomePopularCourseStopDto stop : courseMapper.findPopularCourseStops(
                courses.stream().map(HomePopularCourseDto::getCourseId).toList())) {
            List<String> names = previewNamesByCourseId.computeIfAbsent(
                    stop.getCourseId(), ignored -> new ArrayList<>());
            if (names.size() < HOME_COURSE_PREVIEW_STOP_LIMIT) {
                names.add(stop.getDestinationName());
            }
        }

        courses.forEach(course -> course.setPreviewDestinationNames(
                List.copyOf(previewNamesByCourseId.getOrDefault(course.getCourseId(), List.of()))));
        return courses;
    }

    @Override
    @Transactional
    public Long createCourse(CourseCreateRequest request, Long userId) {
        ValidatedCourse validated = validateBasic(request == null ? null : request.getTitle(),
                request == null ? null : request.getContent(),
                request == null ? null : request.getDestinationIds());
        Long requestedCountryId = validateRequestedCountryId(request == null ? null : request.getCountryId());
        validateDestinations(validated.destinationIds());
        Long countryId = validateCourseCountry(requestedCountryId, validated.destinationIds());

        Course course = new Course();
        course.setTitle(validated.title());
        course.setContent(validated.content());
        course.setUserId(userId);
        course.setCountryId(countryId);

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
        Long requestedCountryId = validateRequestedCountryId(request == null ? null : request.getCountryId());
        validateDestinations(validated.destinationIds());
        Long countryId = validateCourseCountry(requestedCountryId, validated.destinationIds());

        if (courseMapper.updateCourse(courseId, userId, countryId,
                validated.title(), validated.content()) != 1) {
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

    /**
     * 그 여행지를 담고 있는 코스 번호들.
     *
     * <p>부르는 쪽(여행지 삭제)의 트랜잭션에 그대로 참여한다. 읽기 전용을 적어 두지만,
     * 이미 열려 있는 쓰기 트랜잭션에 합류할 때는 그 성질이 따로 적용되지 않는다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Long> getCourseIdsContainingDestination(Long destinationId) {
        if (destinationId == null) {
            return List.of();
        }
        List<Long> courseIds = courseMapper.findCourseIdsByDestinationId(destinationId);
        return courseIds == null ? List.of() : courseIds;
    }

    /**
     * STOP 번호를 1부터 빈칸 없이 다시 매긴다.
     *
     * <p>지금 보이는 차례(visit_order → 같으면 id)를 그대로 두고 번호만 메꾼다.
     * 그 차례는 화면이 STOP 목록을 읽는 기준과 같아서, 다시 매겨도 순서가 뒤바뀌지 않는다.
     *
     * <p>번호가 이미 맞는 STOP 은 건드리지 않는다. 여행지 하나가 지워졌을 때
     * 그 앞쪽 STOP 들은 대개 그대로라, 코스 전체를 다시 쓰지 않아도 된다.
     *
     * <p>부를 곳이 없으면 조용히 끝난다. 여기서 막을 잘못은 없다.
     */
    @Override
    @Transactional
    public void resequenceStops(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return;
        }
        // 같은 코스가 두 번 들어와도 한 번만 손본다.
        for (Long courseId : new LinkedHashSet<>(courseIds)) {
            if (courseId == null) {
                continue;
            }
            resequenceOneCourse(courseId);
        }
    }

    private void resequenceOneCourse(Long courseId) {
        List<CourseDestination> stops = courseMapper.findCourseStopOrders(courseId);
        if (stops == null || stops.isEmpty()) {
            // 남은 STOP 이 없는 코스. 매길 번호도 없다.
            return;
        }

        int visitOrder = 1;
        for (CourseDestination stop : stops) {
            if (!Objects.equals(stop.getVisitOrder(), visitOrder)
                    && courseMapper.updateCourseDestinationVisitOrder(
                            stop.getId(), visitOrder) != 1) {
                // 방금 읽은 줄이 사라졌다. 번호가 어긋난 채로 두지 않고 전부 되돌린다.
                throw new IllegalStateException("코스 여행지 순서 정리에 실패했습니다.");
            }
            visitOrder++;
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

    private Long validateRequestedCountryId(Long countryId) {
        if (countryId == null || countryId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "코스 국가를 선택해 주세요.");
        }
        return countryId;
    }

    private Long validateCourseCountry(Long requestedCountryId, List<Long> destinationIds) {
        List<CourseDestinationCountryDto> destinations = courseMapper.findDestinationCountries(destinationIds);
        if (destinations.size() != destinationIds.size()
                || destinations.stream().anyMatch(destination -> destination.getCountryId() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "국가 정보를 확인할 수 없는 여행지가 포함되어 있습니다.");
        }

        List<Long> countryIds = destinations.stream()
                .map(CourseDestinationCountryDto::getCountryId)
                .distinct()
                .toList();
        if (countryIds.size() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "하나의 여행 코스에는 같은 국가의 여행지만 추가할 수 있습니다.");
        }
        Long actualCountryId = countryIds.get(0);
        if (!Objects.equals(requestedCountryId, actualCountryId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "선택한 국가와 여행지의 국가가 일치하지 않습니다.");
        }
        return actualCountryId;
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
