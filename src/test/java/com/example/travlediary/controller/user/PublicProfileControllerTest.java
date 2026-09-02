package com.example.travlediary.controller.user;

import com.example.travlediary.config.CustomLoginSuccessHandler;
import com.example.travlediary.config.CustomLogoutSuccessHandler;
import com.example.travlediary.config.SecurityConfig;
import com.example.travlediary.dto.BoardListDto;
import com.example.travlediary.dto.PublicUserProfileDto;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.board.BoardService;
import com.example.travlediary.service.user.PublicProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(PublicProfileController.class)
@Import(SecurityConfig.class)
class PublicProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicProfileService publicProfileService;
    @MockitoBean
    private BoardService boardService;
    @MockitoBean
    private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean
    private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean
    private UserMapper userMapper;

    @Test
    void guestCanOpenNumericPublicProfileWithEscapedNickname() throws Exception {
        PublicUserProfileDto profile = profile(7L, "<여행자>", "/images/default.png");
        BoardListDto item = item(20L, "post", "QUESTION", "제주 질문", "2026-08-07 20:30:00");
        when(publicProfileService.getPublicProfile(7L)).thenReturn(profile);
        when(boardService.getBoardListByUserId(7L, "all", 1, 10)).thenReturn(List.of(item));
        when(boardService.getBoardCountByUserId(7L, "all")).thenReturn(1);

        mockMvc.perform(get("/users/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/public-profile"))
                .andExpect(model().attribute("profile", profile))
                .andExpect(model().attribute("type", "all"))
                .andExpect(content().string(containsString("&lt;여행자&gt;")))
                .andExpect(content().string(not(containsString("<여행자>"))))
                .andExpect(content().string(containsString("26.08.07 20:30")))
                .andExpect(content().string(containsString("href=\"/post/20\"")))
                .andExpect(content().string(not(containsString("북마크"))));
    }

    @Test
    void authenticatedMemberCanOpenAnotherPublicProfile() throws Exception {
        User currentUser = user(5L, "member", "/uploads/member.png");
        when(userMapper.findById(5L)).thenReturn(currentUser);
        when(publicProfileService.getPublicProfile(7L))
                .thenReturn(profile(7L, "여행자", "/images/default.png"));
        when(boardService.getBoardListByUserId(7L, "tip", 2, 10)).thenReturn(List.of());
        when(boardService.getBoardCountByUserId(7L, "tip")).thenReturn(12);

        mockMvc.perform(get("/users/7")
                        .param("type", "tip")
                        .param("page", "2")
                        .with(authentication(authenticationFor(currentUser))))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("user"))
                .andExpect(model().attribute("currentUserProfileImage", "/uploads/member.png"))
                .andExpect(model().attribute("currentPage", 2))
                .andExpect(model().attribute("totalPages", 2))
                .andExpect(content().string(containsString("type=tip&amp;page=1&amp;size=10")));
    }

    @Test
    void invalidFilterAndUnsafePaginationAreNormalized() throws Exception {
        when(publicProfileService.getPublicProfile(7L))
                .thenReturn(profile(7L, "여행자", "/images/default.png"));
        when(boardService.getBoardListByUserId(7L, "all", 1, 50)).thenReturn(List.of());
        when(boardService.getBoardCountByUserId(7L, "all")).thenReturn(0);

        mockMvc.perform(get("/users/7")
                        .param("type", "unknown")
                        .param("page", "0")
                        .param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("type", "all"))
                .andExpect(model().attribute("currentPage", 1))
                .andExpect(model().attribute("pageSize", 50));

        verify(boardService).getBoardListByUserId(7L, "all", 1, 50);
    }

    @Test
    void unavailableMemberReturnsTheSameNotFoundResponse() throws Exception {
        when(publicProfileService.getPublicProfile(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));

        mockMvc.perform(get("/users/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void guestPermitRuleDoesNotOpenNicknameOrMutationPaths() throws Exception {
        mockMvc.perform(get("/users/traveler"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/users/7"))
                .andExpect(status().is3xxRedirection());
    }

    private PublicUserProfileDto profile(Long id, String nickname, String image) {
        PublicUserProfileDto profile = new PublicUserProfileDto();
        profile.setId(id);
        profile.setNickname(nickname);
        profile.setProfileImage(image);
        return profile;
    }

    private UsernamePasswordAuthenticationToken authenticationFor(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        return new UsernamePasswordAuthenticationToken(
                userDetails, userDetails.getPassword(), userDetails.getAuthorities());
    }

    private User user(Long id, String username, String profileImage) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setUserPassword("encoded-password");
        user.setUserRole(UserRole.USER);
        user.setProfileImage(profileImage);
        return user;
    }

    private BoardListDto item(Long id, String boardType, String postType, String title, String createdAt) {
        BoardListDto item = new BoardListDto();
        item.setId(id);
        item.setBoardType(boardType);
        item.setPostType(postType);
        item.setTitle(title);
        item.setCreatedAt(createdAt);
        item.setViews(10);
        item.setCommentCount(2);
        return item;
    }
}
