package com.example.travlediary.controller.event;

import com.example.travlediary.model.Event;
import com.example.travlediary.service.event.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventApiController {

    private final EventService eventService;

    @GetMapping("/slide")
    public List<Event> slideEvents() {
        // 프런트 슬라이더에서는 img 경로만 쓰므로 필요하다면
        // DTO 변환해서 최소 데이터만 내려줘도 됨

        log.info("✅ /api/events/slide 호출됨");

        return eventService.getSlideEvents();
    }

  /*  @GetMapping("/slide")
    public List<Map<String, String>> slideEvents() {
        log.info("✅ 테스트용 slideEvents 호출됨");

        Map<String, String> dummy = new HashMap<>();
        dummy.put("title", "더미 이벤트");
        dummy.put("description", "테스트 설명입니다");
        dummy.put("eventImg", "/images/test.jpg");

        return List.of(dummy); // 하드코딩된 리스트 반환
    }*/

}
