package com.example.travlediary.service.user;

import com.example.travlediary.dto.AdminAppealDto;
import com.example.travlediary.model.AppealStatus;
import com.example.travlediary.model.UserAppeal;
import com.example.travlediary.repository.user.UserAppealMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 이의제기 처리.
 * 승인 시 이용제한 해제는 UserSanctionService 의 기존 해제 처리를 그대로 재사용한다.
 */
@Service
@RequiredArgsConstructor
public class AdminAppealService {

    private static final Logger log = LoggerFactory.getLogger(AdminAppealService.class);
    private static final int MAX_REPLY_LENGTH = 1000;

    private final UserAppealMapper userAppealMapper;
    private final UserSanctionService userSanctionService;

    @Transactional(readOnly = true)
    public long countAppeals(AppealStatus status, String keyword) {
        return userAppealMapper.countAdminAppeals(status, keyword);
    }

    @Transactional(readOnly = true)
    public List<AdminAppealDto> getAppeals(AppealStatus status, String keyword,
                                           long offset, int limit) {
        return userAppealMapper.findAdminAppeals(status, keyword, offset, limit);
    }

    @Transactional(readOnly = true)
    public long countPendingAppeals() {
        return userAppealMapper.countAdminAppeals(AppealStatus.PENDING, null);
    }

    @Transactional(readOnly = true)
    public AdminAppealDto getAppeal(Long id) {
        AdminAppealDto appeal = userAppealMapper.findAdminAppealById(id);
        if (appeal == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "이의제기를 찾을 수 없습니다.");
        }
        return appeal;
    }

    /** 승인: 이용제한을 해제하고 이의제기를 APPROVED 로 마감한다. */
    @Transactional
    public void approve(Long appealId, String adminReply, Long adminId) {
        UserAppeal appeal = requirePendingAppeal(appealId, adminId);
        String reply = normalizeReply(adminReply);

        // 해제·영구제재 차단 해제·users.status 복원은 기존 제재 서비스가 담당한다.
        userSanctionService.releaseByAppeal(
                appeal.getUserId(), appeal.getSanctionId(), reply, adminId);

        finish(appeal.getId(), AppealStatus.APPROVED, adminId, reply);
        log.info("Appeal approved: appealId={}, userId={}, adminId={}",
                appealId, appeal.getUserId(), adminId);
    }

    /** 기각: 현재 이용제한은 그대로 두고 이의제기만 REJECTED 로 마감한다. */
    @Transactional
    public void reject(Long appealId, String adminReply, Long adminId) {
        UserAppeal appeal = requirePendingAppeal(appealId, adminId);
        String reply = normalizeReply(adminReply);

        finish(appeal.getId(), AppealStatus.REJECTED, adminId, reply);
        log.info("Appeal rejected: appealId={}, userId={}, adminId={}",
                appealId, appeal.getUserId(), adminId);
    }

    private UserAppeal requirePendingAppeal(Long appealId, Long adminId) {
        if (adminId == null) {
            throw new IllegalArgumentException("관리자 정보를 확인할 수 없습니다.");
        }
        UserAppeal appeal = userAppealMapper.findByIdForUpdate(appealId);
        if (appeal == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "이의제기를 찾을 수 없습니다.");
        }
        if (appeal.getStatus() != AppealStatus.PENDING) {
            throw new AppealValidationException(null, "이미 처리된 이의제기입니다.");
        }
        return appeal;
    }

    private void finish(Long appealId, AppealStatus status, Long adminId, String reply) {
        if (userAppealMapper.handle(appealId, status, adminId, reply, LocalDateTime.now()) != 1) {
            throw new AppealValidationException(null, "이미 처리된 이의제기입니다.");
        }
    }

    private String normalizeReply(String adminReply) {
        String normalized = adminReply == null ? "" : adminReply.strip();
        if (normalized.isEmpty()) {
            throw new AppealValidationException("adminReply", "처리 사유를 입력해 주세요.");
        }
        if (normalized.length() > MAX_REPLY_LENGTH) {
            throw new AppealValidationException(
                    "adminReply", "처리 사유는 " + MAX_REPLY_LENGTH + "자 이하로 입력해 주세요.");
        }
        return normalized;
    }
}
