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

    private final EventService eventService;

    /** 전체 이벤트 리스트 (진행중/예정/종료 탭 지원) */
    @GetMapping
    public String eventList(@RequestParam(required = false) String status, Model model) {
        // status: "ongoing", "upcoming", "ended" (필요없으면 null)
        List<Event> events = eventService.getEventsByStatus(status);
        model.addAttribute("eventList", events);
        model.addAttribute("selectedStatus", status);
        return "event/event-list"; // templates/event/event-list.html
    }

    /** 이벤트 상세 */
    @GetMapping("/{id}")
    public String eventDetail(@PathVariable Long id, Model model) {
        Event event = eventService.getEventDetail(id);
        model.addAttribute("event", event);
        return "event/event-detail"; // templates/event/event-detail.html
    }


}
