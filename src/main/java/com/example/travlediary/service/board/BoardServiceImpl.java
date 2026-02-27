package com.example.travlediary.service.board;

import com.example.travlediary.dto.BoardListDto;
import com.example.travlediary.repository.course.CourseMapper;
import com.example.travlediary.repository.post.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    private final PostMapper postMapper;
    private final CourseMapper courseMapper;

    @Override
    public List<BoardListDto> getBoardList(String boardType, String postType, String sort, int page, int size) {
        int offset = (page - 1) * size;
        List<BoardListDto> result = new ArrayList<>();

        if ("post".equals(boardType)) {
            // 질문/팁 게시판만
            List<BoardListDto> posts = postMapper.findPosts(postType, sort, offset, size);
            posts.forEach(p -> p.setBoardType("post"));
            result.addAll(posts);

        } else if ("course".equals(boardType)) {
            // 코스 게시판만
            List<BoardListDto> courses = courseMapper.findCourses(sort, offset, size);
            courses.forEach(c -> {
                c.setBoardType("course");
                c.setPostType(null); // 코스는 postType 없음
            });
            result.addAll(courses);

        } else {
            // 전체글 보기 (두 쿼리 합쳐서 정렬 필요)
            List<BoardListDto> posts = postMapper.findPosts(postType, sort, offset, size);
            posts.forEach(p -> p.setBoardType("post"));
            List<BoardListDto> courses = courseMapper.findCourses(sort, offset, size);
            courses.forEach(c -> {
                c.setBoardType("course");
                c.setPostType(null);
            });
            result.addAll(posts);
            result.addAll(courses);
            // sort에 따라 Java에서 전체 정렬도 가능 (필요하다면)
        }
        return result;
    }

    @Override
    public int getBoardCount(String boardType, String postType) {
        if ("post".equals(boardType)) {
            return postMapper.countPosts(postType);
        } else if ("course".equals(boardType)) {
            return courseMapper.countCourses();
        } else {
            return postMapper.countPosts(null) + courseMapper.countCourses();
        }
    }
}
