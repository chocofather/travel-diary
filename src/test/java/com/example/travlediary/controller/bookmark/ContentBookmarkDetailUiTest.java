package com.example.travlediary.controller.bookmark;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.controller.course.CourseController;
import com.example.travlediary.controller.post.PostController;
import com.example.travlediary.dto.CourseDetailDto;
import com.example.travlediary.dto.PostDetailDto;
import com.example.travlediary.model.PostType;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.course.CourseService;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.post.PostService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PostController.class, CourseController.class})
@Import(SecurityConfig.class)
class ContentBookmarkDetailUiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;
    @MockitoBean
    private CourseService courseService;
    @MockitoBean
    private CountryCategoryService countryCategoryService;
    @MockitoBean
    private FileUploadService fileUploadService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @ParameterizedTest
    @MethodSource("postBookmarkStates")
    void postBookmarkRendersInTitleRowForQuestionAndTip(
            long id, PostType postType, boolean bookmarked, String expectedType, String expectedLabel
    ) throws Exception {
        when(postService.getPostDetail(id, null)).thenReturn(post(id, postType, bookmarked));

        String body = mockMvc.perform(get("/post/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains(expectedType)
                .contains("content-detail-title-row post-title-row")
                .contains("data-bookmark-url=\"/bookmarks/posts/" + id + "\"")
                .contains("data-bookmarked=\"" + bookmarked + "\"")
                .contains("aria-pressed=\"" + bookmarked + "\"")
                .contains("content-bookmark-image")
                .contains(expectedLabel);
        assertBookmarkState(body, bookmarked);
        assertThat(actionSection(body, "post-detail-actions")).doesNotContain("content-bookmark-button");
    }

    @ParameterizedTest
    @MethodSource("courseBookmarkStates")
    void courseBookmarkRendersInTitleRow(boolean bookmarked, String expectedLabel, long id) throws Exception {
        when(courseService.getCourseDetail(eq(id), isNull(), any()))
                .thenReturn(course(id, bookmarked));

        String body = mockMvc.perform(get("/course/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("content-detail-title-row course-title-row")
                .contains("data-bookmark-url=\"/bookmarks/courses/" + id + "\"")
                .contains("data-bookmarked=\"" + bookmarked + "\"")
                .contains("aria-pressed=\"" + bookmarked + "\"")
                .contains("content-bookmark-image")
                .contains(expectedLabel);
        assertBookmarkState(body, bookmarked);
        assertThat(actionSection(body, "course-detail-actions")).doesNotContain("content-bookmark-button");
    }

    private static Stream<Arguments> postBookmarkStates() {
        return Stream.of(
                Arguments.of(10L, PostType.QUESTION, false, "여행 질문", "북마크 저장"),
                Arguments.of(11L, PostType.QUESTION, true, "여행 질문", "북마크 취소"),
                Arguments.of(12L, PostType.TIP, false, "여행 팁", "북마크 저장"),
                Arguments.of(13L, PostType.TIP, true, "여행 팁", "북마크 취소")
        );
    }

    private static Stream<Arguments> courseBookmarkStates() {
        return Stream.of(
                Arguments.of(false, "북마크 저장", 20L),
                Arguments.of(true, "북마크 취소", 21L)
        );
    }

    private PostDetailDto post(long id, PostType postType, boolean bookmarked) {
        PostDetailDto post = new PostDetailDto();
        post.setId(id);
        post.setPostType(postType);
        post.setTitle("아주 긴 여행 게시글 제목");
        post.setContent("<p>본문</p>");
        post.setNickname("여행자");
        post.setCreatedAt(Timestamp.valueOf("2026-08-07 10:00:00"));
        post.setUpdatedAt(Timestamp.valueOf("2026-08-07 11:00:00"));
        post.setImages(List.of());
        post.setBookmarked(bookmarked);
        return post;
    }

    private CourseDetailDto course(long id, boolean bookmarked) {
        CourseDetailDto course = new CourseDetailDto();
        course.setId(id);
        course.setTitle("아주 긴 여행 코스 제목");
        course.setContent("<p>코스 소개</p>");
        course.setNickname("여행자");
        course.setCreatedAt(Timestamp.valueOf("2026-08-07 10:00:00"));
        course.setUpdatedAt(Timestamp.valueOf("2026-08-07 11:00:00"));
        course.setStops(List.of());
        course.setBookmarked(bookmarked);
        return course;
    }

    private void assertBookmarkState(String body, boolean bookmarked) {
        int start = body.indexOf("<div class=\"content-detail-title-row");
        int end = body.indexOf("</div>", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        String titleRow = body.substring(start, end);
        if (bookmarked) {
            assertThat(titleRow).contains("is-bookmarked");
        } else {
            assertThat(titleRow).doesNotContain("is-bookmarked");
        }
    }

    private String actionSection(String body, String className) {
        int start = body.indexOf("<div class=\"" + className + "\">");
        int end = body.indexOf("</div>", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return body.substring(start, end);
    }
}
