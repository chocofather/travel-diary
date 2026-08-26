package com.example.travlediary.controller.travelplan;

import com.example.travlediary.dto.TravelPlanFinalizeCheckDto;
import com.example.travlediary.service.travelplan.TravelPlanFinalizeService;
import com.example.travlediary.service.travelplan.TravelPlanValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

/**
 * 여행 계획 확정.
 *
 * <p>이번 단계에는 "지금 확정할 수 있는가" 를 묻는 것 하나뿐이다.
 * 진행 상태를 바꾸거나 최종본을 만드는 경로는 아직 없다.
 *
 * <p>권한 확인은 전부 Service 가 한다. 여기서 참여 여부나 편집 상태를 직접 보지 않는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/travel-plans/{travelPlanId:\\d+}/finalize")
public class TravelPlanFinalizeController {

    private final TravelPlanFinalizeService travelPlanFinalizeService;

    /**
     * 확정하기 전에 한 번 물어본다.
     * 여기서 된다고 해도 실제로 확정할 때 서버가 같은 조건을 다시 본다.
     */
    @PostMapping("/check")
    public TravelPlanFinalizeCheckDto check(@PathVariable Long travelPlanId,
                                            Principal principal) {
        return travelPlanFinalizeService.checkFinalizable(principal, travelPlanId);
    }

    /**
     * 실제로 완료한다.
     *
     * <p>물어본 결과를 믿지 않고 여기서 자격과 편집 상태를 다시 본다.
     *
     * @param force 쓰고 있는 사람이 있어도 그대로 하겠다고 방장이 정한 경우.
     *              경고를 보여 준 뒤에만 온다.
     */
    @PostMapping
    public ResponseEntity<?> finalizePlan(@PathVariable Long travelPlanId,
                                          @RequestParam(defaultValue = "false") boolean force,
                                          Principal principal) {
        try {
            travelPlanFinalizeService.finalizePlan(principal, travelPlanId, force);
        } catch (TravelPlanValidationException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", exception.getMessage()));
        }
        return ResponseEntity.noContent().build();
    }
}
