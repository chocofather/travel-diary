package com.example.travlediary.controller.course;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.config.i18n.I18nConfig;
import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.config.i18n.TravelDiaryLocaleResolver;
import com.example.travlediary.dto.CourseDetailDto;
import com.example.travlediary.dto.CourseStopDto;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.course.CourseService;
import jakarta.servlet.http.Cookie;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 코스 상세를 언어별로 그려 본다.
 *
 * <p>고정 문구는 언어에 맞게 바뀌고, 사용자가 쓴 제목·소개·닉네임은 그대로 남아야 한다.
 */
@WebMvcTest(CourseController.class)
@Import({SecurityConfig.class, I18nConfig.class})
class CourseDetailLocaleRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseService courseService;
    @MockitoBean
    private CountryCategoryService countryCategoryService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    static Stream<Arguments> localizedScreens() {
        return Stream.of(
                Arguments.of(SupportedLanguage.KOREAN,
                        new String[]{"나만의 여행 코스", "작성자", "작성일", "수정일", "조회수",
                                "코스 소개", "총 2개의 여행지", "여행 동선", "지역",
                                "수정", "삭제", "댓글", "최신순", "등록", "코스 목록으로"},
                        "이 여행 코스를 삭제하시겠습니까?"),
                Arguments.of(SupportedLanguage.ENGLISH,
                        new String[]{"My Travel Course", "Author", "Posted", "Updated", "Views",
                                "Course Overview", "2 destinations in total", "Travel Route", "Area",
                                "Edit", "Delete", "Comments", "Newest", "Post", "Back to course list"},
                        "Delete this travel course?"),
                Arguments.of(SupportedLanguage.JAPANESE,
                        new String[]{"わたしだけの旅行コース", "投稿者", "投稿日", "更新日", "閲覧数",
                                "コース紹介", "全2カ所の旅行スポット", "旅行ルート", "地域",
                                "編集", "削除", "コメント", "新着順", "投稿", "コース一覧へ"},
                        "この旅行コースを削除しますか？"),
                Arguments.of(SupportedLanguage.CHINESE_SIMPLIFIED,
                        new String[]{"我的旅行路线", "作者", "发布日期", "更新日期", "浏览量",
                                "路线简介", "共 2 个旅行地", "旅行路线", "地区",
                                "编辑", "删除", "评论", "最新", "发布", "返回路线列表"},
                        "确定要删除这条旅行路线吗？"),
                Arguments.of(SupportedLanguage.CHINESE_TRADITIONAL,
                        new String[]{"我的旅行路線", "作者", "發布日期", "更新日期", "瀏覽次數",
                                "路線簡介", "共 2 個旅遊景點", "旅行路線", "地區",
                                "編輯", "刪除", "留言", "最新", "發布", "返回路線列表"},
                        "確定要刪除這條旅行路線嗎？"));
    }

    @ParameterizedTest
    @MethodSource("localizedScreens")
    void fixedUiFollowsTheChosenLanguageWhileUserWrittenTextStays(
            SupportedLanguage language, String[] expectedTexts, String expectedDeleteConfirm)
            throws Exception {
        when(courseService.getCourseDetail(7L, null, language)).thenReturn(course());

        String body = mockMvc.perform(get("/course/{id}", 7L)
                        .cookie(new Cookie(TravelDiaryLocaleResolver.COOKIE_NAME,
                                language.getLanguageTag())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains(expectedTexts);
        // 삭제 확인 문구는 화면이 스크립트에 건네준다.
        assertThat(body).contains("data-confirm=\"" + expectedDeleteConfirm + "\"");
        // 사용자가 쓴 제목·소개·닉네임과 STOP 이름·지역명은 서버가 준 값 그대로다.
        assertThat(body)
                .contains("여행 코스 테스트")
                .contains("여행 코스 입니다")
                .contains("여행자민준")
                .contains("경복궁")
                .contains("종로구");
        // 로그인 안내는 언어별 문장 안에 로그인 링크를 그대로 품는다.
        assertThat(body).contains("href=\"/login?redirect=/course/7\"");
        // 없는 메시지 키가 남아 있지 않다.
        assertThat(body).doesNotContain("??");
    }

    private CourseDetailDto course() {
        CourseDetailDto course = new CourseDetailDto();
        course.setId(7L);
        course.setUserId(5L);
        course.setTitle("여행 코스 테스트");
        course.setContent("<p>여행 코스 입니다</p>");
        course.setNickname("여행자민준");
        course.setCreatedAt(Timestamp.valueOf("2026-01-02 10:00:00"));
        course.setUpdatedAt(Timestamp.valueOf("2026-01-03 11:00:00"));
        course.setViews(12);
        // 수정·삭제 버튼까지 그려 보려면 내 코스여야 한다.
        course.setMyCourse(true);
        course.setStops(List.of(
                stop(15L, 1, "경복궁", "종로구"),
                stop(16L, 2, "북촌한옥마을", "종로구")));
        return course;
    }

    private CourseStopDto stop(Long destinationId, int visitOrder, String name, String regionName) {
        CourseStopDto stop = new CourseStopDto();
        stop.setDestinationId(destinationId);
        stop.setVisitOrder(visitOrder);
        stop.setName(name);
        stop.setRegionId(235L);
        stop.setRegionName(regionName);
        return stop;
    }
}
