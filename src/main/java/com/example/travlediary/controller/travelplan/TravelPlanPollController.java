package com.example.travlediary.controller.travelplan;

import com.example.travlediary.dto.TravelPlanPollCountsDto;
import com.example.travlediary.dto.TravelPlanPollCreateForm;
import com.example.travlediary.dto.TravelPlanPollDetailDto;
import com.example.travlediary.dto.TravelPlanPollDto;
import com.example.travlediary.dto.TravelPlanPollSummaryDto;
import com.example.travlediary.dto.TravelPlanPollVoteForm;
import com.example.travlediary.service.travelplan.TravelPlanPollService;
import com.example.travlediary.service.travelplan.TravelPlanValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * 투표 만들기와 진행 중인 투표 조회.
 *
 * <p>저장은 WebSocket 이 아니라 기존 HTTP 경로다.
 * 저장된 뒤의 알림만 Service 가 커밋 뒤에 내보낸다.
 *
 * <p>권한 확인과 검증은 전부 Service 가 한다. 여기서 SQL 이나 참여 여부를 직접 보지 않는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/travel-plans/{travelPlanId:\\d+}/polls")
public class TravelPlanPollController {

    private final TravelPlanPollService travelPlanPollService;

    /**
     * 투표 만들기.
     * 방 번호는 URL 에서만 온다. 본문의 어떤 값도 작성자나 방을 정하지 못한다.
     */
    @PostMapping
    public ResponseEntity<?> create(@PathVariable Long travelPlanId,
                                    @RequestBody TravelPlanPollCreateForm form,
                                    Principal principal) {
        try {
            TravelPlanPollDto poll =
                    travelPlanPollService.createPoll(principal, travelPlanId, form);
            return ResponseEntity.status(HttpStatus.CREATED).body(poll);
        } catch (TravelPlanValidationException exception) {
            // 입력한 값은 화면에 그대로 남는다. 사유만 알린다.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", exception.getMessage()));
        }
    }

    /** 투표 센터의 [진행 중] 탭. 목록에서는 선택지를 펼치지 않는다. */
    @GetMapping("/open")
    public Map<String, List<TravelPlanPollSummaryDto>> openPolls(@PathVariable Long travelPlanId,
                                                                 Principal principal) {
        return Map.of("polls", travelPlanPollService.openPolls(principal, travelPlanId));
    }

    /**
     * 투표 센터의 [지난 투표] 탭.
     * 마감 기능이 아직 없어 지금은 대개 비어 있다. 비어 있는 것이 정상이다.
     */
    @GetMapping("/closed")
    public Map<String, List<TravelPlanPollSummaryDto>> closedPolls(@PathVariable Long travelPlanId,
                                                                   Principal principal) {
        return Map.of("polls", travelPlanPollService.closedPolls(principal, travelPlanId));
    }

    /**
     * 투표 센터 탭에 붙는 숫자.
     * 목록을 열지 않아도 두 숫자가 맞아야 해서 따로 둔다.
     */
    @GetMapping("/counts")
    public TravelPlanPollCountsDto counts(@PathVariable Long travelPlanId, Principal principal) {
        return travelPlanPollService.pollCounts(principal, travelPlanId);
    }

    /** 카드를 눌러 들어간 상세. 선택지와 지금까지의 표, 내가 고른 것이 함께 온다. */
    @GetMapping("/{pollId:\\d+}")
    public TravelPlanPollDetailDto poll(@PathVariable Long travelPlanId,
                                        @PathVariable Long pollId,
                                        Principal principal) {
        return travelPlanPollService.pollDetail(principal, travelPlanId, pollId);
    }

    /**
     * 투표하기. 이미 투표했다면 그 선택을 바꾼다.
     * 누가 투표하는지는 URL 도 본문도 아닌 로그인 정보에서만 나온다.
     */
    @PostMapping("/{pollId:\\d+}/vote")
    public ResponseEntity<?> vote(@PathVariable Long travelPlanId,
                                  @PathVariable Long pollId,
                                  @RequestBody TravelPlanPollVoteForm form,
                                  Principal principal) {
        try {
            travelPlanPollService.submitVote(principal, travelPlanId, pollId,
                    form == null ? null : form.getOptionIds());
        } catch (TravelPlanValidationException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", exception.getMessage()));
        }
        // 저장된 뒤의 값은 상세를 다시 읽어 그대로 돌려준다. 화면이 숫자를 짐작하지 않는다.
        return ResponseEntity.ok(
                travelPlanPollService.pollDetail(principal, travelPlanId, pollId));
    }

    /** 직접 마감. 만든 사람만 할 수 있고, 그 확인은 Service 가 한다. */
    @PostMapping("/{pollId:\\d+}/close")
    public ResponseEntity<?> close(@PathVariable Long travelPlanId,
                                   @PathVariable Long pollId,
                                   Principal principal) {
        try {
            travelPlanPollService.closePoll(principal, travelPlanId, pollId);
        } catch (TravelPlanValidationException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", exception.getMessage()));
        }
        return ResponseEntity.ok(
                travelPlanPollService.pollDetail(principal, travelPlanId, pollId));
    }
}
