package com.example.travlediary.controller.user;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.BoardListDto;
import com.example.travlediary.dto.MyPageCommentPageDto;
import com.example.travlediary.dto.MyPageBookmarkPageDto;
import com.example.travlediary.dto.MyPageProfileDto;
import com.example.travlediary.dto.ProfileUpdateForm;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.board.BoardService;
import com.example.travlediary.service.bookmark.MyPageBookmarkService;
import com.example.travlediary.service.comment.MyPageCommentService;
import com.example.travlediary.service.user.MyPageService;
import com.example.travlediary.service.user.NicknameCheckStatus;
import com.example.travlediary.service.user.NicknamePolicy;
import com.example.travlediary.service.user.ProfileValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Controller
@RequiredArgsConstructor
@RequestMapping("/mypage")
public class MyPageController {

    private static final int POSTS_PAGE_SIZE = 10;
    private static final Set<String> POST_FILTER_TYPES =
            Set.of("all", "question", "tip", "course");

    private final MyPageService myPageService;
    private final BoardService boardService;
    private final MyPageCommentService myPageCommentService;
    private final MyPageBookmarkService myPageBookmarkService;

    @GetMapping
    public String myPage(@AuthenticationPrincipal CustomUserDetails userDetails,
                         Model model) {
        model.addAttribute("profile", myPageService.getProfile(userDetails.getId()));
        model.addAttribute("pageTitle", "마이페이지 | 여행일기");
        return "mypage/index";
    }

    @GetMapping("/posts")
    public String posts(@AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestParam(defaultValue = "all") String type,
                        @RequestParam(defaultValue = "1") int page,
                        Model model) {
        String safeType = normalizePostFilter(type);
        int safePage = Math.max(page, 1);
        Long userId = userDetails.getId();

        List<BoardListDto> posts = boardService.getBoardListByUserId(
                userId, safeType, safePage, POSTS_PAGE_SIZE);
        int totalCount = boardService.getBoardCountByUserId(userId, safeType);
        int totalPages = (int) Math.ceil((double) totalCount / POSTS_PAGE_SIZE);

        model.addAttribute("posts", posts);
        model.addAttribute("type", safeType);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageTitle", "내가 작성한 글 | 마이페이지");
        return "mypage/posts";
    }

    @GetMapping("/comments")
    public String comments(@AuthenticationPrincipal CustomUserDetails userDetails,
                           @RequestParam(defaultValue = "all") String type,
                           @RequestParam(defaultValue = "1") int page,
                           Model model) {
        MyPageCommentPageDto commentPage = myPageCommentService.getMyComments(
                userDetails.getId(), type, page);

        model.addAttribute("comments", commentPage.getComments());
        model.addAttribute("type", commentPage.getType());
        model.addAttribute("currentPage", commentPage.getCurrentPage());
        model.addAttribute("totalPages", commentPage.getTotalPages());
        model.addAttribute("pageTitle", "내가 작성한 댓글 | 마이페이지");
        return "mypage/comments";
    }

    @GetMapping("/bookmarks")
    public String bookmarks(@AuthenticationPrincipal CustomUserDetails userDetails,
                            @RequestParam(defaultValue = "destination") String section,
                            @RequestParam(defaultValue = "all") String scope,
                            @RequestParam(defaultValue = "all") String type,
                            @RequestParam(defaultValue = "1") int page,
                            Model model) {
        MyPageBookmarkPageDto bookmarkPage = myPageBookmarkService.getBookmarks(
                userDetails.getId(), section, scope, type, page);
        // 여행정보 북마크의 카테고리 이름만 현재 언어로 바꾼다. 필터·정렬은 그대로 둔다.
        myPageBookmarkService.localizeTravelInfoBookmarks(
                bookmarkPage.getBookmarks(),
                SupportedLanguage.fromLocale(LocaleContextHolder.getLocale())
                        .orElse(SupportedLanguage.KOREAN));

        model.addAttribute("bookmarks", bookmarkPage.getBookmarks());
        model.addAttribute("section", bookmarkPage.getSection());
        model.addAttribute("scope", bookmarkPage.getScope());
        model.addAttribute("type", bookmarkPage.getType());
        model.addAttribute("currentPage", bookmarkPage.getCurrentPage());
        model.addAttribute("totalPages", bookmarkPage.getTotalPages());
        model.addAttribute("totalCount", bookmarkPage.getTotalCount());
        model.addAttribute("pageTitle", "북마크 | 마이페이지");
        return "mypage/bookmarks";
    }

    @GetMapping("/profile")
    public String profileForm(@AuthenticationPrincipal CustomUserDetails userDetails,
                              Model model) {
        MyPageProfileDto profile = myPageService.getProfile(userDetails.getId());
        ProfileUpdateForm form = new ProfileUpdateForm();
        form.setNickname(profile.getNickname());
        prepareProfileModel(model, profile, form);
        return "mypage/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@ModelAttribute("profileForm") ProfileUpdateForm form,
                                BindingResult bindingResult,
                                @AuthenticationPrincipal CustomUserDetails userDetails,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        try {
            myPageService.updateProfile(userDetails.getId(), form);
        } catch (ProfileValidationException exception) {
            reject(bindingResult, exception);
            MyPageProfileDto profile = myPageService.getProfile(userDetails.getId());
            prepareProfileModel(model, profile, form);
            return "mypage/profile";
        }

        redirectAttributes.addFlashAttribute("profileMessage", "프로필이 변경되었습니다.");
        return "redirect:/mypage/profile";
    }

    @GetMapping("/profile/check-nickname")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkNickname(
            @RequestParam String nickname,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            NicknameCheckStatus status = myPageService.checkNickname(
                    userDetails.getId(), nickname);
            return ResponseEntity.ok(nicknameCheckResponse(status));
        } catch (NicknamePolicy.ViolationException exception) {
            NicknameCheckStatus status = exception.getViolationType()
                    == NicknamePolicy.ViolationType.FORBIDDEN
                    ? NicknameCheckStatus.FORBIDDEN
                    : NicknameCheckStatus.INVALID_FORMAT;
            return ResponseEntity.badRequest().body(nicknameCheckResponse(status));
        }
    }

    private Map<String, Object> nicknameCheckResponse(NicknameCheckStatus status) {
        return Map.of(
                "status", status.name(),
                "available", status.isAvailable(),
                "message", status.getMessage()
        );
    }

    private String normalizePostFilter(String type) {
        if (type == null) {
            return "all";
        }
        String normalized = type.toLowerCase(Locale.ROOT);
        return POST_FILTER_TYPES.contains(normalized) ? normalized : "all";
    }

    private void prepareProfileModel(Model model, MyPageProfileDto profile,
                                     ProfileUpdateForm form) {
        model.addAttribute("profile", profile);
        model.addAttribute("profileForm", form);
        model.addAttribute("pageTitle", "프로필 변경 | 마이페이지");
    }

    private void reject(BindingResult bindingResult, ProfileValidationException exception) {
        if (exception.getField() == null) {
            bindingResult.reject("profile.invalid", exception.getMessage());
            return;
        }
        bindingResult.rejectValue(exception.getField(), "profile.invalid", exception.getMessage());
    }
}
