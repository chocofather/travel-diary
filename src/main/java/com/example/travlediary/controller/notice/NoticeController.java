package com.example.travlediary.controller.notice;

import com.example.travlediary.dto.NoticeDetailDto;
import com.example.travlediary.dto.NoticeListItemDto;
import com.example.travlediary.service.notice.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class NoticeController {

    private static final int PAGE_SIZE = 10;

    private final NoticeService noticeService;

    @GetMapping("/support/notices")
    public String list(@RequestParam(required = false) String page, Model model) {
        int requestedPage = parsePage(page);
        long totalCount = noticeService.countPublicList();
        int totalPages = totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / PAGE_SIZE);
        int currentPage = totalPages == 0 ? 1 : Math.min(requestedPage, totalPages);
        long offset = (long) (currentPage - 1) * PAGE_SIZE;
        List<NoticeListItemDto> notices = noticeService.getPublicList(offset, PAGE_SIZE);

        int pageStart = Math.max(1, currentPage - 2);
        int pageEnd = Math.min(totalPages, pageStart + 4);
        pageStart = Math.max(1, pageEnd - 4);

        model.addAttribute("notices", notices);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("pageStart", pageStart);
        model.addAttribute("pageEnd", pageEnd);
        model.addAttribute("pageTitle", "공지사항 | 고객센터");
        return "support/notices/list";
    }

    @GetMapping("/support/notices/{id:\\d+}")
    public String detail(@PathVariable Long id, Model model) {
        NoticeDetailDto notice = noticeService.getPublicDetail(id);
        model.addAttribute("notice", notice);
        model.addAttribute("pageTitle", notice.getTitle() + " | 공지사항");
        return "support/notices/detail";
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
}
