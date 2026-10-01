package com.example.travlediary.controller.board;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.controller.course.CourseController;
import com.example.travlediary.controller.post.PostController;
import com.example.travlediary.dto.CourseEditDto;
import com.example.travlediary.dto.CourseStopDto;
import com.example.travlediary.dto.PostEditDto;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.model.PostType;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.course.CourseService;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.post.PostService;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PostController.class, CourseController.class})
@Import(SecurityConfig.class)
class CommunityWriteMobileRenderingTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private PostService postService;
    @MockitoBean private CourseService courseService;
    @MockitoBean private CountryCategoryService countryCategoryService;
    @MockitoBean private FileUploadService fileUploadService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    private static final String CONTENT = "<p><strong>기존 소개</strong>와 본문</p><p><img src='/uploads/editor/sample.svg' width='240'></p>";

    @Test
    void postWriteAndEditKeepTypeContentAndSubmitContracts() throws Exception {
        for (PostType type : List.of(PostType.QUESTION, PostType.TIP)) {
            var write = Jsoup.parse(render("/post/write?postType=" + type, "post-write-" + type));
            assertThat(write.selectFirst("input[name=postType]").val()).isEqualTo(type.name());
            assertThat(write.selectFirst("#post-form").attr("action")).isEqualTo("/post/write");
            assertThat(write.selectFirst(".write-type-change[data-board-write-open]")).isNotNull();
            PostEditDto post = new PostEditDto();
            post.setId(10L);
            post.setTitle("기존 여행 글 제목");
            post.setPostType(type);
            post.setContent(CONTENT);
            when(postService.getPostForEdit(10L, 5L)).thenReturn(post);
            var edit = Jsoup.parse(render("/post/10/edit", "post-edit-" + type));
            assertThat(edit.selectFirst("#title").val()).isEqualTo(post.getTitle());
            assertThat(edit.selectFirst("#initial-content").val()).isEqualTo(CONTENT);
            assertThat(edit.selectFirst("option[selected]").val()).isEqualTo(type.name());
            assertThat(edit.selectFirst("#post-form").attr("action")).isEqualTo("/post/10/edit");
        }
    }

    @Test
    void courseWriteAndEditKeepServerCountriesInitialStopsAndSaveFields() throws Exception {
        CountryCategory domestic = new CountryCategory();
        domestic.setId(7L);
        domestic.setRegionName("대한민국");
        CountryCategory overseas = new CountryCategory();
        overseas.setId(8L);
        overseas.setRegionName("일본");
        overseas.setParentId(1L);
        when(countryCategoryService.getCourseCountries()).thenReturn(List.of(domestic, overseas));
        var write = Jsoup.parse(render("/course/write", "course-write"));
        assertThat(write.selectFirst("[data-country-scope=domestic]").attr("data-country-id")).isEqualTo("7");
        assertThat(write.selectFirst("#course-form").attr("action")).isEqualTo("/course/write");
        assertThat(write.selectFirst("#course-title").attr("name")).isEqualTo("title");
        for (long countryId : List.of(7L, 8L)) {
            CourseEditDto course = new CourseEditDto();
            course.setId(10L);
            course.setTitle("기존 여행 코스 제목");
            course.setContent(CONTENT);
            course.setCountryId(countryId);
            course.setCountryName(countryId == 7 ? "대한민국" : "일본");
            course.setStops(List.of(stop(3L, countryId), stop(1L, countryId)));
            when(courseService.getCourseForEdit(eq(10L), eq(5L), any())).thenReturn(course);
            var edit = Jsoup.parse(render("/course/10/edit", "course-edit-" + countryId));
            assertThat(edit.select("[data-initial-destination]").eachAttr("data-destination-id")).containsExactly("3", "1");
            assertThat(edit.selectFirst("#course-country-id").val()).isEqualTo(String.valueOf(countryId));
            assertThat(edit.selectFirst("#course-title").val()).isEqualTo(course.getTitle());
            assertThat(edit.selectFirst("#initial-content").val()).isEqualTo(CONTENT);
            assertThat(edit.selectFirst("#course-form").attr("action")).isEqualTo("/course/10/edit");
        }
    }

    private CourseStopDto stop(long id, long countryId) {
        CourseStopDto stop = new CourseStopDto();
        stop.setDestinationId(id);
        stop.setCountryId(countryId);
        stop.setName(id == 3 ? "아주 긴 여행지 이름과 LongDestinationNameWithoutSpaces" : "서울 경교장");
        stop.setRegionName("서울 종로구");
        stop.setImageUrl(id == 3 ? null : "/uploads/editor/sample.svg");
        return stop;
    }

    private String render(String url, String name) throws Exception {
        User member = new User();
        member.setId(5L);
        member.setUserRole(UserRole.USER);
        String html = mvc.perform(get(url).with(user(new CustomUserDetails(member))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("/css/community-write-mobile.css", "name=\"_csrf\"", "id=\"content-input\"");
        String exportPath = System.getenv("COMMUNITY_WRITE_BROWSER_FIXTURES");
        if (exportPath != null) {
            Path directory = Path.of(exportPath);
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(name + ".html"), html);
        }
        return html;
    }
}
