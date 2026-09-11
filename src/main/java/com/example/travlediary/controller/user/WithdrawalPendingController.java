package com.example.travlediary.controller.user;

import com.example.travlediary.model.User;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.user.AccountRecoveryService;
import com.example.travlediary.service.user.AccountRecoveryService.RecoveryRequestOutcome;
import com.example.travlediary.service.user.EmailPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * 탈퇴 유예(WITHDRAWAL_PENDING) 회원 전용 안내 화면.
 *
 * <p>이 상태의 세션은 일반 서비스에서 격리되고 여기에서만 복구 절차를 밟는다.
 * 복구 가능 여부 판단은 화면의 남은 시간 표시가 아니라 언제나 서버의 purge_scheduled_at 이다.
 */
@Controller
@RequestMapping("/account/withdrawal-pending")
public class WithdrawalPendingController {

    private static final String VIEW = "account/withdrawal-pending";

    private static final Logger log = LoggerFactory.getLogger(WithdrawalPendingController.class);

    private final UserMapper userMapper;
    private final AccountRecoveryService accountRecoveryService;
    private final Clock clock;

    @Autowired
    public WithdrawalPendingController(UserMapper userMapper,
                                       AccountRecoveryService accountRecoveryService) {
        this(userMapper, accountRecoveryService, Clock.systemDefaultZone());
    }

    WithdrawalPendingController(UserMapper userMapper,
                                AccountRecoveryService accountRecoveryService,
                                Clock clock) {
        this.userMapper = userMapper;
        this.accountRecoveryService = accountRecoveryService;
        this.clock = clock;
    }

    @GetMapping
    public String withdrawalPending(@AuthenticationPrincipal CustomUserDetails userDetails,
                                    Locale locale,
                                    Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        User account = userMapper.findWithdrawalPendingById(userDetails.getId());
        if (account == null) {
            // 탈퇴 유예 상태가 아니면 격리 대상도 아니라 홈으로 돌려보내도 루프가 생기지 않는다.
            return "redirect:/";
        }

        prepareModel(model, account, locale);
        return VIEW;
    }

    /** 복구 링크 발송. 이메일은 인증된 회원의 가입 주소를 서버가 찾아 쓴다. */
    @PostMapping("/recovery-link")
    public String sendRecoveryLink(@AuthenticationPrincipal CustomUserDetails userDetails,
                                   RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        RecoveryRequestOutcome outcome;
        try {
            outcome = accountRecoveryService.requestRecoveryFor(userDetails.getId());
        } catch (RuntimeException exception) {
            log.error("Account recovery request could not be completed: exceptionType={}",
                    exception.getClass().getSimpleName());
            outcome = RecoveryRequestOutcome.NOT_ELIGIBLE;
        }
        redirectAttributes.addFlashAttribute("recoveryLinkOutcome", outcome.name());
        return "redirect:/account/withdrawal-pending";
    }

    private void prepareModel(Model model, User account, Locale locale) {
        LocalDateTime currentTime = LocalDateTime.now(clock);
        LocalDateTime purgeScheduledAt = account.getPurgeScheduledAt();
        boolean recoverable = purgeScheduledAt != null && purgeScheduledAt.isAfter(currentTime);

        model.addAttribute("maskedEmail", EmailPolicy.mask(account.getUserEmail()));
        model.addAttribute("withdrawalRequestedAt",
                formatted(account.getWithdrawalRequestedAt(), locale));
        model.addAttribute("purgeScheduledAt", formatted(purgeScheduledAt, locale));
        model.addAttribute("recoverable", recoverable);

        // 남은 시간은 화면 표시용이다. 실제 복구 허용 여부는 서버가 매 요청 다시 판단한다.
        Duration remaining = recoverable
                ? Duration.between(currentTime, purgeScheduledAt)
                : Duration.ZERO;
        model.addAttribute("remainingDays", remaining.toDays());
        model.addAttribute("remainingHours", remaining.toHoursPart());
        model.addAttribute("remainingMinutes", remaining.toMinutesPart());
    }

    /** 날짜 표기는 요청 언어의 관습을 그대로 따른다. */
    private String formatted(LocalDateTime value, Locale locale) {
        if (value == null) {
            return null;
        }
        return DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT)
                .withLocale(locale == null ? Locale.KOREAN : locale)
                .format(value);
    }
}
