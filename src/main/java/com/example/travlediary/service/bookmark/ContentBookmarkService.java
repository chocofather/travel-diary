package com.example.travlediary.service.bookmark;

import com.example.travlediary.model.BookmarkTargetType;
import com.example.travlediary.repository.bookmark.BookmarkMapper;
import com.example.travlediary.repository.course.CourseMapper;
import com.example.travlediary.repository.post.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ContentBookmarkService {

    private final BookmarkMapper bookmarkMapper;
    private final PostMapper postMapper;
    private final CourseMapper courseMapper;

    @Transactional
    public void bookmarkPost(Long postId, Long userId) {
        requireActivePost(postId);
        bookmarkMapper.insertIgnore(userId, BookmarkTargetType.POST.name(), postId);
    }

    @Transactional
    public void unbookmarkPost(Long postId, Long userId) {
        requireActivePost(postId);
        bookmarkMapper.delete(userId, BookmarkTargetType.POST.name(), postId);
    }

    @Transactional
    public void bookmarkCourse(Long courseId, Long userId) {
        requireActiveCourse(courseId);
        bookmarkMapper.insertIgnore(userId, BookmarkTargetType.COURSE.name(), courseId);
    }

    @Transactional
    public void unbookmarkCourse(Long courseId, Long userId) {
        requireActiveCourse(courseId);
        bookmarkMapper.delete(userId, BookmarkTargetType.COURSE.name(), courseId);
    }

    private void requireActivePost(Long postId) {
        if (postId == null || postMapper.findActivePostForUpdate(postId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
    }

    private void requireActiveCourse(Long courseId) {
        if (courseId == null || courseMapper.findActiveCourseForUpdate(courseId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "여행 코스를 찾을 수 없습니다.");
        }
    }
}
