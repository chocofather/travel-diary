package com.example.travlediary.controller.admin;

import com.example.travlediary.config.InternalRedirectValidator;
import com.example.travlediary.dto.ContentModerationForm;
import com.example.travlediary.model.ModerationTargetType;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.moderation.ContentModerationService;
import com.example.travlediary.service.moderation.ModerationValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/admin/contents")
@RequiredArgsConstructor
public class AdminContentModerationController {

    private final ContentModerationService contentModerationService;

    private static final int PAGE_SIZE = 20;
    private static final String LIST_VIEW = "admin/contents/list";

    /** 조치 중인 콘텐츠 관리 목록. */
    @GetMapping
    public String list(@RequestParam(required = false) String targetType,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String page,
                       Model model) {
        ModerationTargetType normalizedType = normalizeFilterType(targetType);
        String normalizedKeyword = normalizeKeyword(keyword);
        int requestedPage = parsePage(page);

        long totalCount = contentModerationService.countModeratedContents(
                normalizedType, normalizedKeyword);
        int totalPages = totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / PAGE_SIZE);
        int currentPage = totalPages == 0 ? 1 : Math.min(requestedPage, totalPages);
        long offset = (long) (currentPage - 1) * PAGE_SIZE;

        model.addAttribute("contents", contentModerationService.getModeratedContents(
                normalizedType, normalizedKeyword, offset, PAGE_SIZE));
        model.addAttribute("targetTypes", ModerationTargetType.values());
        model.addAttribute("currentType", normalizedType == null ? "ALL" : normalizedType.name());
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageStart", Math.max(1, Math.min(currentPage - 2,
                Math.max(1, totalPages - 4))));
        model.addAttribute("pageEnd", Math.min(totalPages,
                Math.max(1, Math.min(currentPage - 2, Math.max(1, totalPages - 4))) + 4));
        model.addAttribute("pageTitle", "조치된 콘텐츠 관리");
        return LIST_VIEW;
    }

    @PostMapping("/{targetType}/{targetId:\\d+}/hide")
    public String hide(@PathVariable String targetType,
                       @PathVariable Long targetId,
                       @ModelAttribute ContentModerationForm form,
                       @AuthenticationPrincipal CustomUserDetails admin) {
        try {
            contentModerationService.hide(
                    parseTargetType(targetType), targetId, form, adminId(admin));
        } catch (ModerationValidationException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return redirect(form);
    }

    @PostMapping("/{targetType}/{targetId:\\d+}/restore")
    public String restore(@PathVariable String targetType,
                          @PathVariable Long targetId,
                          @ModelAttribute ContentModerationForm form,
                          @AuthenticationPrincipal CustomUserDetails admin) {
        try {
            contentModerationService.restore(
                    parseTargetType(targetType), targetId, form, adminId(admin));
        } catch (ModerationValidationException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        return redirect(form);
    }

    /** 조치 후에는 내부 경로로만 되돌아간다. */
    private String redirect(ContentModerationForm form) {
        String target = InternalRedirectValidator.normalize(form == null ? null : form.getRedirect());
        return "redirect:" + (target == null ? "/" : target);
    }

    private Long adminId(CustomUserDetails admin) {
        return admin == null ? null : admin.getId();
    }

    /** 목록 필터는 값이 잘못되면 전체로 되돌린다. */
    private ModerationTargetType normalizeFilterType(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            return null;
        }
        try {
            return ModerationTargetType.valueOf(targetType.strip().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.strip();
    }

    private int parsePage(String page) {
        if (page == null || page.isBlank()) {
            return 1;
        }
        try {
            return Math.max(Integer.parseInt(page.strip()), 1);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private ModerationTargetType parseTargetType(String targetType) {
        try {
            return ModerationTargetType.valueOf(targetType.strip().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "지원하지 않는 조치 대상입니다.");
        }
    }
}
