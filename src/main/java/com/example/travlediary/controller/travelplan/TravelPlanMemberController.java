package com.example.travlediary.controller.travelplan;

import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.travelplan.TravelPlanMemberService;
import com.example.travlediary.service.travelplan.TravelPlanValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 참여자 상태 변경. 권한 판단은 모두 Service 가 한다.
 * 대상은 travel_plan_members.id 만 받고 role / status 는 화면에서 받지 않는다.
 */
@Controller
@RequestMapping("/travel-plans")
@RequiredArgsConstructor
public class TravelPlanMemberController {

    private final TravelPlanMemberService travelPlanMemberService;

    // MEMBER 가 스스로 나가기
    @PostMapping("/{travelPlanId:\\d+}/members/leave")
    public String leave(@PathVariable Long travelPlanId,
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        RedirectAttributes redirectAttributes) {
        try {
            travelPlanMemberService.leave(userDetails.getId(), travelPlanId);
        } catch (TravelPlanValidationException exception) {
            // 방장은 아직 바로 나갈 수 없다. 그 방에 그대로 머문다.
            redirectAttributes.addFlashAttribute("travelPlanError", exception.getMessage());
            return "redirect:/travel-plans/" + travelPlanId;
        }
        // 더 이상 ACTIVE 참여자가 아니라 그 방에 남아 있을 수 없다.
        redirectAttributes.addFlashAttribute("travelPlanMessage", "여행 계획에서 나왔어요.");
        return "redirect:/travel-plans";
    }

    // OWNER 가 다른 ACTIVE MEMBER 에게 방장을 넘기기
    @PostMapping("/{travelPlanId:\\d+}/members/{memberId:\\d+}/transfer-owner")
    public String transferOwnership(@PathVariable Long travelPlanId,
                                    @PathVariable Long memberId,
                                    @AuthenticationPrincipal CustomUserDetails userDetails,
                                    RedirectAttributes redirectAttributes) {
        travelPlanMemberService.transferOwnership(userDetails.getId(), travelPlanId, memberId);
        redirectAttributes.addFlashAttribute("travelPlanMessage", "방장을 넘겼어요.");
        // 넘긴 사람도 방에 그대로 남으므로 같은 플래너로 돌아간다.
        return "redirect:/travel-plans/" + travelPlanId;
    }

    // OWNER 가 MEMBER 를 내보내기
    @PostMapping("/{travelPlanId:\\d+}/members/{memberId:\\d+}/remove")
    public String remove(@PathVariable Long travelPlanId,
                         @PathVariable Long memberId,
                         @AuthenticationPrincipal CustomUserDetails userDetails,
                         RedirectAttributes redirectAttributes) {
        travelPlanMemberService.removeMember(userDetails.getId(), travelPlanId, memberId);
        redirectAttributes.addFlashAttribute("travelPlanMessage", "참여자를 내보냈어요.");
        return "redirect:/travel-plans/" + travelPlanId;
    }
}
