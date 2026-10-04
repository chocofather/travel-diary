package com.tripbora.controller.admin;

import com.tripbora.service.file.DestinationThumbnailCacheCleanupService;
import com.tripbora.service.file.DestinationThumbnailCacheCleanupService.PlanChangedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 공공누리 제3유형(변경금지) 여행지 카드 썸네일 캐시 정리. 관리자가 주소로 직접 여는 유지보수 화면이다(메뉴 없음).
 *
 * <p>GET 은 dry-run 만 한다. 실제 삭제는 POST 로만, 화면에서 확인한 dry-run 결과(파일 수·용량)와
 * 명시적인 확인 값을 함께 보내야 한다. 그사이 대상이 바뀌었으면 아무것도 지우지 않는다.</p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/maintenance/no-derivative-thumbnails")
public class AdminDestinationThumbnailCleanupController {

    static final String CONFIRM_VALUE = "DELETE";
    private static final String VIEW = "admin/maintenance/no-derivative-thumbnails";

    private final DestinationThumbnailCacheCleanupService cleanupService;

    @GetMapping
    public String dryRun(Model model) {
        return withPlan(model);
    }

    @PostMapping("/execute")
    public String execute(@RequestParam int expectedFileCount,
                          @RequestParam long expectedTotalBytes,
                          @RequestParam(required = false) String confirm,
                          Model model) {
        if (!CONFIRM_VALUE.equals(confirm)) {
            model.addAttribute("errorMessage", "확인 칸에 " + CONFIRM_VALUE + " 를 입력해야 삭제합니다. 아무것도 지우지 않았습니다.");
            return withPlan(model);
        }
        try {
            model.addAttribute("result", cleanupService.execute(expectedFileCount, expectedTotalBytes));
        } catch (PlanChangedException changed) {
            model.addAttribute("errorMessage", changed.getMessage());
            model.addAttribute("plan", changed.getCurrentPlan());
        } catch (RuntimeException failure) {
            log.warn("No-derivatives thumbnail cleanup failed: failureType={}", failure.getClass().getSimpleName());
            model.addAttribute("errorMessage", "정리하지 못했습니다. 라이선스나 파일 목록을 확인하지 못해 멈췄습니다.");
        }
        model.addAttribute("confirmValue", CONFIRM_VALUE);
        return VIEW;
    }

    private String withPlan(Model model) {
        try {
            model.addAttribute("plan", cleanupService.plan());
        } catch (RuntimeException failure) {
            log.warn("No-derivatives thumbnail dry-run failed: failureType={}", failure.getClass().getSimpleName());
            model.addAttribute("errorMessage", "dry-run 을 만들지 못했습니다. 라이선스나 파일 목록을 확인하지 못해 멈췄습니다.");
        }
        model.addAttribute("confirmValue", CONFIRM_VALUE);
        return VIEW;
    }
}
