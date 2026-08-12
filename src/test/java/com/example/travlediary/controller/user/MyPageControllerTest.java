package com.example.travlediary.controller.user;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.BoardListDto;
import com.example.travlediary.dto.MyPageProfileDto;
import com.example.travlediary.dto.ProfileUpdateForm;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.board.BoardService;
import com.example.travlediary.service.user.MyPageService;
import com.example.travlediary.service.user.NicknameCheckStatus;
import com.example.travlediary.service.user.NicknamePolicy;
import com.example.travlediary.service.user.ProfileValidationException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(MyPageController.class)
@Import(SecurityConfig.class)
class MyPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MyPageService myPageService;
    @MockitoBean
    private BoardService boardService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void guestCannotOpenMyPageProfileOrPosts() throws Exception {
        mockMvc.perform(get("/mypage"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?redirect=/mypage"));
        mockMvc.perform(get("/mypage/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?redirect=/mypage/profile"));
        mockMvc.perform(get("/mypage/posts"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?redirect=/mypage/posts"));
        mockMvc.perform(get("/mypage/profile/check-nickname").param("nickname", "여행민준"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?redirect=/mypage/profile/check-nickname"));
    }

    @Test
    void userAndAdminCanOpenMyPageWithOnlyTheScreenProfileData() throws Exception {
        MyPageProfileDto memberProfile = profile("여행자", "member@example.com",
                "/uploads/profiles/member.jpg");
        MyPageProfileDto adminProfile = profile("관리자", "admin@example.com",
                "/images/default.png");
        when(myPageService.getProfile(7L)).thenReturn(memberProfile);
        when(myPageService.getProfile(99L)).thenReturn(adminProfile);

        mockMvc.perform(get("/mypage").with(user(principal(7L, UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/index"))
                .andExpect(model().attribute("profile", memberProfile))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("여행자")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("member@example.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "src=\"/uploads/profiles/member.jpg\"")));

        mockMvc.perform(get("/mypage").with(user(principal(99L, UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("profile", adminProfile))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("관리자")));
    }

    @Test
    void mainAndNavigationExposeOnlyImplementedLinks() throws Exception {
        when(myPageService.getProfile(7L)).thenReturn(profile(
                "여행자", "member@example.com", "/images/default.png"));

        mockMvc.perform(get("/mypage").with(user(principal(7L, UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select("a[href=/mypage/profile]")).isNotEmpty();
                    assertThat(document.select("a[href=/mypage/posts]")).isNotEmpty();
                    assertThat(document.select("a[href=/support/inquiries]")).isNotEmpty();
                    assertThat(document.select("a[href=/mypage/inquiries]")).isEmpty();
                    assertThat(document.select(".mypage-layout a[href='#']")).isEmpty();
                    assertThat(document.select("[aria-disabled=true]").text())
                            .doesNotContain("내가 작성한 글")
                            .contains("내가 작성한 댓글", "북마크", "회원정보 수정");
                    assertThat(document.select(
                            ".mypage-navigation-title.is-active[aria-current=page]").text())
                            .isEqualTo("마이페이지");
                });
    }

    @Test
    void userAndAdminCanOpenPostsUsingOnlyTheirPrincipalId() throws Exception {
        when(boardService.getBoardListByUserId(7L, "all", 1, 10)).thenReturn(List.of());
        when(boardService.getBoardCountByUserId(7L, "all")).thenReturn(0);
        when(boardService.getBoardListByUserId(99L, "all", 1, 10)).thenReturn(List.of());
        when(boardService.getBoardCountByUserId(99L, "all")).thenReturn(0);

        mockMvc.perform(get("/mypage/posts")
                        .param("userId", "999")
                        .with(user(principal(7L, UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/posts"))
                .andExpect(model().attribute("type", "all"))
                .andExpect(model().attribute("currentPage", 1));

        mockMvc.perform(get("/mypage/posts")
                        .with(user(principal(99L, UserRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/posts"));

        verify(boardService).getBoardListByUserId(7L, "all", 1, 10);
        verify(boardService).getBoardCountByUserId(7L, "all");
        verify(boardService).getBoardListByUserId(99L, "all", 1, 10);
        verify(boardService).getBoardCountByUserId(99L, "all");
    }

    @Test
    void postsSupportsEveryFilterAndFallsBackToAllForInvalidType() throws Exception {
        CustomUserDetails member = principal(7L, UserRole.USER);

        for (String type : List.of("all", "question", "tip", "course")) {
            mockMvc.perform(get("/mypage/posts")
                            .param("type", type)
                            .with(user(member)))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("type", type));

            verify(boardService).getBoardListByUserId(7L, type, 1, 10);
            verify(boardService).getBoardCountByUserId(7L, type);
        }

        mockMvc.perform(get("/mypage/posts")
                        .param("type", "abc")
                        .with(user(member)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("type", "all"));

        verify(boardService, times(2)).getBoardListByUserId(7L, "all", 1, 10);
        verify(boardService, times(2)).getBoardCountByUserId(7L, "all");
    }

    @Test
    void postsCorrectsNonPositivePageAndKeepsTheFixedPageSize() throws Exception {
        CustomUserDetails member = principal(7L, UserRole.USER);

        mockMvc.perform(get("/mypage/posts")
                        .param("type", "question")
                        .param("page", "0")
                        .param("size", "100")
                        .with(user(member)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentPage", 1));

        mockMvc.perform(get("/mypage/posts")
                        .param("type", "tip")
                        .param("page", "-3")
                        .with(user(member)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentPage", 1));

        verify(boardService).getBoardListByUserId(7L, "question", 1, 10);
        verify(boardService).getBoardListByUserId(7L, "tip", 1, 10);
    }

    @Test
    void postsRendersExistingDetailLinksActiveNavigationAndFilterPreservingPagination() throws Exception {
        List<BoardListDto> posts = List.of(
                boardItem(11L, "post", "QUESTION", "제주도 렌터카 질문", 12),
                boardItem(12L, "post", "TIP", "부산 여행 팁", 31),
                boardItem(13L, "course", null, "서울 당일치기 코스", 52)
        );
        when(boardService.getBoardListByUserId(7L, "all", 1, 10)).thenReturn(posts);
        when(boardService.getBoardCountByUserId(7L, "all")).thenReturn(12);

        mockMvc.perform(get("/mypage/posts")
                        .with(user(principal(7L, UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("posts", posts))
                .andExpect(model().attribute("totalPages", 2))
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select("a[href=/post/11]")).hasSize(1);
                    assertThat(document.select("a[href=/post/12]")).hasSize(1);
                    assertThat(document.select("a[href=/course/13]")).hasSize(1);
                    assertThat(document.select(".mypage-post-type").text())
                            .contains("여행 질문", "여행 팁", "여행 코스");
                    assertThat(document.select(
                            ".mypage-navigation-link.is-active[aria-current=page]").text())
                            .isEqualTo("내가 작성한 글");
                    assertThat(document.select(
                            ".mypage-post-filters a.is-active[aria-current=page]").text())
                            .isEqualTo("전체");
                    assertThat(document.select(
                            ".mypage-post-pagination a[href='/mypage/posts?type=all&page=2']"))
                            .hasSize(1);
                });
    }

    @Test
    void postsShowsEmptyStateWhenThereAreNoResults() throws Exception {
        when(boardService.getBoardListByUserId(7L, "question", 1, 10)).thenReturn(List.of());
        when(boardService.getBoardCountByUserId(7L, "question")).thenReturn(0);

        mockMvc.perform(get("/mypage/posts")
                        .param("type", "question")
                        .with(user(principal(7L, UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("작성한 글이 없습니다.")))
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(".mypage-post-pagination")).isEmpty();
                    assertThat(document.select(
                            ".mypage-post-filters a.is-active[aria-current=page]").text())
                            .isEqualTo("여행 질문");
                });
    }

    @Test
    void postsPaginationKeepsTheSelectedFilter() throws Exception {
        BoardListDto item = boardItem(12L, "post", "QUESTION", "두 번째 페이지 질문", 3);
        when(boardService.getBoardListByUserId(7L, "question", 2, 10)).thenReturn(List.of(item));
        when(boardService.getBoardCountByUserId(7L, "question")).thenReturn(12);

        mockMvc.perform(get("/mypage/posts")
                        .param("type", "question")
                        .param("page", "2")
                        .with(user(principal(7L, UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(
                            ".mypage-post-pagination a[href='/mypage/posts?type=question&page=1']"))
                            .hasSize(1);
                });
    }

    @Test
    void profileFormShowsCurrentValuesAndIncludesCsrf() throws Exception {
        when(myPageService.getProfile(7L)).thenReturn(profile(
                "기존닉네임", "member@example.com", "/images/default.png"));

        mockMvc.perform(get("/mypage/profile").with(user(principal(7L, UserRole.USER))))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/profile"))
                .andExpect(model().attribute("profileForm",
                        org.hamcrest.Matchers.hasProperty("nickname",
                                org.hamcrest.Matchers.is("기존닉네임"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("maxlength=\"12\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "2~12자의 한글, 영문, 숫자만 사용할 수 있습니다.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "공백·특수문자 및 부적절한 표현은 사용할 수 없습니다.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "src=\"/js/mypage-profile.js\"")))
                .andExpect(result -> {
                    var document = Jsoup.parse(result.getResponse().getContentAsString());
                    assertThat(document.select(
                            "form[action=/mypage/profile][method=post][enctype=multipart/form-data]"))
                            .hasSize(1);
                    assertThat(document.select(
                            ".mypage-navigation-link.is-active[aria-current=page]").text())
                            .isEqualTo("프로필");
                });
    }

    @Test
    void nicknameCheckUsesPrincipalAndExcludesTheCurrentUserThroughTheService() throws Exception {
        when(myPageService.checkNickname(7L, "기존닉네임"))
                .thenReturn(NicknameCheckStatus.CURRENT);
        when(myPageService.checkNickname(7L, "중복닉네임"))
                .thenReturn(NicknameCheckStatus.DUPLICATE);

        mockMvc.perform(get("/mypage/profile/check-nickname")
                        .with(user(principal(7L, UserRole.USER)))
                        .param("nickname", "기존닉네임")
                        .param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.status").value("CURRENT"))
                .andExpect(jsonPath("$.message").value("현재 사용 중인 닉네임입니다."));

        mockMvc.perform(get("/mypage/profile/check-nickname")
                        .with(user(principal(7L, UserRole.USER)))
                        .param("nickname", "중복닉네임"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.status").value("DUPLICATE"));

        verify(myPageService).checkNickname(7L, "기존닉네임");
        verify(myPageService).checkNickname(7L, "중복닉네임");
    }

    @Test
    void nicknameCheckReturnsClearBadRequestForInvalidFormat() throws Exception {
        doThrow(new NicknamePolicy.ViolationException(
                NicknamePolicy.ViolationType.INVALID_FORMAT, NicknamePolicy.INVALID_MESSAGE))
                .when(myPageService).checkNickname(7L, "여행 민준");

        mockMvc.perform(get("/mypage/profile/check-nickname")
                        .with(user(principal(7L, UserRole.USER)))
                        .param("nickname", "여행 민준"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.status").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.message").value(NicknamePolicy.INVALID_MESSAGE));
    }

    @Test
    void nicknameCheckReturnsForbiddenWithoutExposingTheMatchedRule() throws Exception {
        doThrow(new NicknamePolicy.ViolationException(
                NicknamePolicy.ViolationType.FORBIDDEN, NicknamePolicy.FORBIDDEN_MESSAGE))
                .when(myPageService).checkNickname(7L, "병12신");

        mockMvc.perform(get("/mypage/profile/check-nickname")
                        .with(user(principal(7L, UserRole.USER)))
                        .param("nickname", "병12신"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.status").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("사용할 수 없는 닉네임입니다."));
    }

    @Test
    void profileMutationUsesPrincipalRequiresCsrfAndRedirectsWithMessage() throws Exception {
        CustomUserDetails member = principal(7L, UserRole.USER);
        MockMultipartFile noImageSelected = new MockMultipartFile(
                "profileImageFile", "", "application/octet-stream", new byte[0]);

        mockMvc.perform(multipart("/mypage/profile")
                        .file(noImageSelected)
                        .with(user(member))
                        .param("nickname", "새닉네임")
                        .param("userId", "999"))
                .andExpect(status().isForbidden());
        verify(myPageService, never()).updateProfile(any(), any());

        mockMvc.perform(multipart("/mypage/profile")
                        .file(noImageSelected)
                        .with(user(member)).with(csrf())
                        .param("nickname", "새닉네임")
                        .param("userId", "999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/profile"))
                .andExpect(flash().attribute("profileMessage", "프로필이 변경되었습니다."));

        org.mockito.ArgumentCaptor<ProfileUpdateForm> captor =
                org.mockito.ArgumentCaptor.forClass(ProfileUpdateForm.class);
        verify(myPageService).updateProfile(eq(7L), captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("새닉네임");
        assertThat(ProfileUpdateForm.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("userId");
    }

    @Test
    void validationFailureKeepsSubmittedNicknameAndCurrentImage() throws Exception {
        MyPageProfileDto current = profile(
                "기존닉네임", "member@example.com", "/uploads/profiles/current.png");
        when(myPageService.getProfile(7L)).thenReturn(current);
        doThrow(new ProfileValidationException("nickname", "이미 사용 중인 닉네임입니다."))
                .when(myPageService).updateProfile(eq(7L), any(ProfileUpdateForm.class));

        mockMvc.perform(post("/mypage/profile")
                        .with(user(principal(7L, UserRole.USER))).with(csrf())
                        .param("nickname", "입력닉네임"))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/profile"))
                .andExpect(model().attributeHasFieldErrors("profileForm", "nickname"))
                .andExpect(model().attribute("profileForm",
                        org.hamcrest.Matchers.hasProperty("nickname",
                                org.hamcrest.Matchers.is("입력닉네임"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("이미 사용 중인 닉네임입니다.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "src=\"/uploads/profiles/current.png\"")));
    }

    private MyPageProfileDto profile(String nickname, String email, String image) {
        MyPageProfileDto profile = new MyPageProfileDto();
        profile.setNickname(nickname);
        profile.setUserEmail(email);
        profile.setProfileImage(image);
        return profile;
    }

    private BoardListDto boardItem(Long id, String boardType, String postType,
                                   String title, int views) {
        BoardListDto item = new BoardListDto();
        item.setId(id);
        item.setBoardType(boardType);
        item.setPostType(postType);
        item.setTitle(title);
        item.setCreatedAt("2026-08-12 10:20:30");
        item.setViews(views);
        return item;
    }

    private CustomUserDetails principal(Long id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername(role == UserRole.ADMIN ? "admin" : "member");
        user.setUserPassword("password");
        user.setUserRole(role);
        return new CustomUserDetails(user);
    }
}
