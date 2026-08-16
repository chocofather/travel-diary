package com.example.travlediary.controller.event;

import com.example.travlediary.model.Event;
import com.example.travlediary.service.event.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RequiredArgsConstructor
@Controller
@RequestMapping("/events")
public class EventController {

    private static final int DEFAULT_PAGE_SIZE = 9;
    private static final int MAX_PAGE_SIZE = 48;

    private final EventService eventService;

    /** 이벤트 리스트 (진행중/예정/종료 탭 + 페이징 지원) */
    @GetMapping
    public String eventList(@RequestParam(required = false) String status,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "9") int size,
                            Model model) {
        String selectedStatus = normalizeStatus(status);
        int safeSize = normalizeSize(size);
        long totalCount = eventService.countEventsByStatus(selectedStatus);
        int totalPages = totalCount == 0
                ? 0
                : (int) Math.ceil((double) totalCount / safeSize);
        int safePage = Math.max(page, 1);
        if (totalPages > 0) {
            safePage = Math.min(safePage, totalPages);
        }
        long offset = (long) (safePage - 1) * safeSize;

        List<Event> events = eventService.getEventsByStatus(selectedStatus, offset, safeSize);
        int pageStart = Math.max(1, safePage - 2);
        int pageEnd = Math.min(totalPages, pageStart + 4);
        pageStart = Math.max(1, pageEnd - 4);

        model.addAttribute("eventList", events);
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("pageSize", safeSize);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("pageStart", pageStart);
        model.addAttribute("pageEnd", pageEnd);
        return "event/event-list";
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /** 이벤트 상세 */
    @GetMapping("/{id}")
    public String eventDetail(@PathVariable Long id, Model model) {
        Event event = eventService.getEventDetail(id);
        model.addAttribute("event", event);
        return "event/event-detail"; // templates/event/event-detail.html
    }

    private String normalizeStatus(String status) {
        return switch (status == null ? "" : status) {
            case "ongoing", "upcoming", "ended" -> status;
            default -> "ongoing";
        };
    }


}
