package com.example.travlediary.controller.travelplan;

import com.example.travlediary.dto.TravelPlanChatMessageDto;
import com.example.travlediary.service.travelplan.TravelPlanChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
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
     * 최근 대화. 오래된 것이 앞에 온다.
     *
     * @param before 있으면 그 메시지보다 앞선 것들을 가져온다([이전 메시지 보기])
     */
    @GetMapping("/messages")
    public Map<String, Object> messages(@PathVariable Long travelPlanId,
                                        @RequestParam(required = false) Long before,
                                        Principal principal) {
        List<TravelPlanChatMessageDto> messages = before == null
                ? travelPlanChatService.recentMessages(principal, travelPlanId)
                : travelPlanChatService.messagesBefore(principal, travelPlanId, before);

        return Map.of(
                "messages", messages,
                // 한 페이지를 꽉 채워 왔다면 그 앞에 더 있을 수 있다.
                "hasMore", messages.size() >= TravelPlanChatService.PAGE_SIZE);
    }

    /** 상단 채팅 버튼의 안 읽은 개수. 다시 연결됐을 때도 이 값을 다시 읽는다. */
    @GetMapping("/unread")
    public Map<String, Object> unread(@PathVariable Long travelPlanId, Principal principal) {
        return Map.of("unreadCount", travelPlanChatService.unreadCount(principal, travelPlanId));
    }
}
