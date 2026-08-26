package com.example.travlediary.controller.travelplan;

import com.example.travlediary.dto.TravelPlanChatTimelineDto;
import com.example.travlediary.service.travelplan.TravelPlanChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

/**
 * 채팅 기록 조회.
 *
 * <p>상세 화면을 열었다고 대화 전체를 함께 내려보내지 않는다.
 * 채팅 패널을 처음 열 때와 [이전 메시지 보기] 를 누를 때만 여기로 온다.
 *
 * <p>권한 확인은 전부 Service 가 한다. 여기서 SQL 이나 참여 여부를 직접 보지 않는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/travel-plans/{travelPlanId:\\d+}/chat")
public class TravelPlanChatController {

    private final TravelPlanChatService travelPlanChatService;

    /**
     * 채팅창에 그릴 한 페이지. 오래된 것이 앞에 온다.
     * 대화와 "새 투표를 만들었어요" 알림이 시간 순서로 섞여 온다.
     *
     * <p>두 기준을 함께 받는다. 표가 달라 번호 하나로는 자를 수 없다.
     * 둘 다 없으면 가장 최근 페이지다.
     */
    @GetMapping("/timeline")
    public TravelPlanChatTimelineDto timeline(
            @PathVariable Long travelPlanId,
            @RequestParam(required = false) Long beforeMessageId,
            @RequestParam(required = false) Long beforePollId,
            Principal principal) {
        return travelPlanChatService.timeline(
                principal, travelPlanId, beforeMessageId, beforePollId);
    }

    /** 상단 채팅 버튼의 안 읽은 개수. 다시 연결됐을 때도 이 값을 다시 읽는다. */
    @GetMapping("/unread")
    public Map<String, Object> unread(@PathVariable Long travelPlanId, Principal principal) {
        return Map.of("unreadCount", travelPlanChatService.unreadCount(principal, travelPlanId));
    }
}
