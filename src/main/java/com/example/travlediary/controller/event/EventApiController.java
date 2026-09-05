package com.example.travlediary.controller.event;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.EventSlideDto;
import com.example.travlediary.service.event.EventLocalizationService;
import com.example.travlediary.service.event.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
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
    private final EventLocalizationService eventLocalizationService;

    /**
     * 메인 홈 슬라이더용. 노출 대상과 정렬은 기존 조회 그대로 두고,
     * 화면에 찍을 제목·설명만 요청 언어로 바꿔 슬라이더가 쓰는 값만 내려보낸다.
     */
    @GetMapping("/slide")
    public List<EventSlideDto> slideEvents() {
        log.info("✅ /api/events/slide 호출됨");

        // 번역은 이 목록 전체를 한 번에 읽는다. (슬라이드마다 조회하지 않는다)
        return eventLocalizationService
                .localizeAll(eventService.getSlideEvents(), requestedLanguage())
                .stream()
                .map(EventSlideDto::from)
                .toList();
    }

    /** 슬라이더도 공개 화면과 같은 언어를 쓴다. 지원하지 않는 locale 이면 한국어로 본다. */
    private SupportedLanguage requestedLanguage() {
        return SupportedLanguage.fromLocale(LocaleContextHolder.getLocale())
                .orElse(SupportedLanguage.KOREAN);
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
