package com.example.travlediary.controller.travelplan;

import com.example.travlediary.dto.TravelPlanInvitePreviewDto;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.travelplan.TravelPlanInvitationService;
import com.example.travlediary.service.travelplan.TravelPlanValidationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Optional;

/**
 * 초대 링크. 발급/재발급/비활성화는 OWNER 전용이고,
 * 링크를 여는 미리보기 GET 은 비로그인도 볼 수 있다.
 * 권한 판단은 모두 Service 가 한다.
 */
@Controller
@RequestMapping("/travel-plans")
@RequiredArgsConstructor
public class TravelPlanInvitationController {

    private static final String PREVIEW_VIEW = "travelplan/invitation-preview";
    /** 발급 직후 한 번만 화면에 실어 보내는 raw 링크 */
    private static final String ISSUED_URL_ATTRIBUTE = "travelPlanInviteUrl";

    private final TravelPlanInvitationService travelPlanInvitationService;

    // 첫 초대 링크 발급 (ACTIVE OWNER 전용)
    @PostMapping("/{travelPlanId:\\d+}/invitations")
    public String createInvitation(@PathVariable Long travelPlanId,
                                   @AuthenticationPrincipal CustomUserDetails userDetails,
                                   HttpServletRequest request,
                                   RedirectAttributes redirectAttributes) {
        try {
            String rawToken = travelPlanInvitationService.createInvitation(
                    userDetails.getId(), travelPlanId);
            issued(redirectAttributes, request, rawToken);
        } catch (TravelPlanValidationException exception) {
            redirectAttributes.addFlashAttribute("travelPlanError", exception.getMessage());
        }
        return redirectToPlan(travelPlanId);
    }

    // 재발급. 기존 링크는 즉시 쓸 수 없게 된다 (ACTIVE OWNER 전용)
    @PostMapping("/{travelPlanId:\\d+}/invitations/regenerate")
    public String regenerateInvitation(@PathVariable Long travelPlanId,
                                       @AuthenticationPrincipal CustomUserDetails userDetails,
                                       HttpServletRequest request,
                                       RedirectAttributes redirectAttributes) {
        try {
            String rawToken = travelPlanInvitationService.regenerateInvitation(
                    userDetails.getId(), travelPlanId);
            issued(redirectAttributes, request, rawToken);
        } catch (TravelPlanValidationException exception) {
            redirectAttributes.addFlashAttribute("travelPlanError", exception.getMessage());
        }
        return redirectToPlan(travelPlanId);
    }

    // 비활성화 (ACTIVE OWNER 전용)
    @PostMapping("/{travelPlanId:\\d+}/invitations/disable")
    public String disableInvitation(@PathVariable Long travelPlanId,
                                    @AuthenticationPrincipal CustomUserDetails userDetails,
                                    RedirectAttributes redirectAttributes) {
        travelPlanInvitationService.disableInvitation(userDetails.getId(), travelPlanId);
        redirectAttributes.addFlashAttribute("travelPlanMessage", "초대 링크를 껐어요.");
        return redirectToPlan(travelPlanId);
    }

    /**
     * 초대 링크 미리보기.
     * 비로그인도 열 수 있고, 이미 들어와 있는 사람은 방으로 바로 보낸다.
     * 끊긴 링크는 어디서 걸렸는지 구분하지 않고 같은 안내로 처리한다.
     */
    @GetMapping("/invitations/{rawToken}")
    public String preview(@PathVariable String rawToken,
                          @AuthenticationPrincipal CustomUserDetails userDetails,
                          Model model) {
        Long userId = userDetails == null ? null : userDetails.getId();
        Optional<TravelPlanInvitePreviewDto> preview =
                travelPlanInvitationService.resolvePreview(userId, rawToken);

        if (preview.isEmpty()) {
            // 유효하지 않은 링크도 안내 화면으로 끝낸다 (오류 페이지로 넘기지 않는다)
            model.addAttribute("travelPlanInviteInvalid", true);
            return PREVIEW_VIEW;
        }

        TravelPlanInvitePreviewDto invitePreview = preview.get();
        if (invitePreview.isAlreadyMember()) {
            return redirectToPlan(invitePreview.getTravelPlanId());
        }
        model.addAttribute("travelPlanInvitePreview", invitePreview);
        return PREVIEW_VIEW;
    }

    /**
     * 발급된 링크는 이 응답에서만 볼 수 있다.
     * 운영 도메인을 코드에 박지 않고 지금 요청 기준으로 절대 URL 을 만든다.
     */
    private void issued(RedirectAttributes redirectAttributes, HttpServletRequest request,
                        String rawToken) {
        String inviteUrl = ServletUriComponentsBuilder.fromContextPath(request)
                .path("/travel-plans/invitations/")
                .path(rawToken)
                .toUriString();
        redirectAttributes.addFlashAttribute(ISSUED_URL_ATTRIBUTE, inviteUrl);
        redirectAttributes.addFlashAttribute("travelPlanMessage", "초대 링크가 만들어졌어요.");
    }

    private String redirectToPlan(Long travelPlanId) {
        return "redirect:/travel-plans/" + travelPlanId;
    }
}
