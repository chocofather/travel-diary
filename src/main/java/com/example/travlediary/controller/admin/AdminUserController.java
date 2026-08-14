package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.AdminUserListItemDto;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.service.user.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private static final int PAGE_SIZE = 20;

    private final AdminUserService adminUserService;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String page,
                       Model model) {
        String normalizedKeyword = normalizeKeyword(keyword);
        UserStatus normalizedStatus = normalizeStatus(status);
        int requestedPage = parsePage(page);

        long totalCount = adminUserService.countUsers(normalizedKeyword, normalizedStatus);
        int totalPages = totalPages(totalCount);
        int currentPage = totalPages == 0 ? 1 : Math.min(requestedPage, totalPages);
        long offset = (long) (currentPage - 1) * PAGE_SIZE;
        List<AdminUserListItemDto> users = adminUserService.getUsers(
                normalizedKeyword, normalizedStatus, offset, PAGE_SIZE);

        int pageStart = Math.max(1, currentPage - 2);
        int pageEnd = Math.min(totalPages, pageStart + 4);
        pageStart = Math.max(1, pageEnd - 4);

        model.addAttribute("users", users);
        model.addAttribute("keyword", normalizedKeyword);
        model.addAttribute("currentStatus", normalizedStatus == null ? "ALL" : normalizedStatus.name());
        model.addAttribute("statuses", UserStatus.values());
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("pageStart", pageStart);
        model.addAttribute("pageEnd", pageEnd);
        model.addAttribute("pageTitle", "회원 관리");
        return "admin/users/list";
    }

    @GetMapping("/{id:\\d+}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("user", adminUserService.getUser(id));
        model.addAttribute("pageTitle", "회원 상세");
        return "admin/users/detail";
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.strip();
    }

    private UserStatus normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        try {
            return UserStatus.valueOf(rawStatus.strip());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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

    private int totalPages(long totalCount) {
        return totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / PAGE_SIZE);
    }
}