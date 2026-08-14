package com.example.travlediary.service.user;

import com.example.travlediary.dto.SanctionReleaseForm;
import com.example.travlediary.dto.UserSanctionForm;
import com.example.travlediary.model.BlockedEmail;
import com.example.travlediary.model.SanctionReleaseVia;
import com.example.travlediary.model.SanctionStatus;
import com.example.travlediary.model.SanctionType;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserSanction;
import com.example.travlediary.model.UserStatus;
import com.example.travlediary.repository.user.BlockedEmailMapper;
import com.example.travlediary.repository.user.UserMapper;
import com.example.travlediary.repository.user.UserSanctionMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 회원 이용제한/해제. users.status 와 user_sanctions 를 같은 트랜잭션에서 동기화한다.
 */
@Service
@RequiredArgsConstructor
public class UserSanctionService {

    private static final Logger log = LoggerFactory.getLogger(UserSanctionService.class);
    private static final int MAX_REASON_LENGTH = 500;

    /** 신규 제재는 정상 또는 휴면 회원에게만 적용한다. */
    private static final Set<UserStatus> SANCTIONABLE_STATUSES =
            EnumSet.of(UserStatus.ACTIVE, UserStatus.SUSPENDED);

    private final UserMapper userMapper;
    private final UserSanctionMapper userSanctionMapper;
    private final BlockedEmailMapper blockedEmailMapper;
    private final EmailHasher emailHasher;

    @Transactional(readOnly = true)
    public UserSanction getActiveSanction(Long userId) {
        return userSanctionMapper.findActiveByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<UserSanction> getSanctionHistory(Long userId) {
        return userSanctionMapper.findByUserId(userId);
    }

    /** 회원 이용제한 적용. */
    @Transactional
    public void restrict(Long userId, UserSanctionForm form, Long adminId) {
        if (adminId == null) {
            throw new IllegalArgumentException("관리자 정보를 확인할 수 없습니다.");
        }
        User target = requireUser(userId);
        requireNotAdminAccount(target);

        String reason = normalizeReason(form == null ? null : form.getReason());
        SanctionType type = form == null ? null : form.getType();
        if (type == null) {
            throw new SanctionValidationException("type", "이용제한 유형을 선택해 주세요.");
        }
        LocalDateTime expiresAt = resolveExpiresAt(type, form.getExpiresAt());

        if (!SANCTIONABLE_STATUSES.contains(target.getStatus())) {
            throw new SanctionValidationException(null, statusRejectionMessage(target.getStatus()));
        }
        if (userSanctionMapper.findActiveByUserIdForUpdate(userId) != null) {
            throw new SanctionValidationException(null, "이미 적용 중인 이용제한이 있습니다.");
        }

        UserSanction sanction = new UserSanction();
        sanction.setUserId(userId);
        sanction.setType(type);
        sanction.setStatus(SanctionStatus.ACTIVE);
        sanction.setReason(reason);
        sanction.setAdminNote(normalizeAdminNote(form.getAdminNote()));
        sanction.setPreviousStatus(target.getStatus());
        sanction.setStartsAt(LocalDateTime.now());
        sanction.setExpiresAt(expiresAt);
        sanction.setCreatedBy(adminId);
        if (userSanctionMapper.insert(sanction) != 1) {
            throw new IllegalStateException("이용제한을 저장하지 못했습니다.");
        }

        if (userMapper.updateStatusForAdmin(userId, UserStatus.RESTRICTED, target.getStatus()) != 1) {
            throw new IllegalStateException("회원 상태를 변경하지 못했습니다.");
        }

        if (type == SanctionType.PERMANENT) {
            blockRegistrationEmail(target, sanction, adminId);
        }
        log.info("User restricted: userId={}, type={}, adminId={}", userId, type, adminId);
    }

    /** 관리자 이용제한 해제. */
    @Transactional
    public void release(Long userId, SanctionReleaseForm form, Long adminId) {
        if (adminId == null) {
            throw new IllegalArgumentException("관리자 정보를 확인할 수 없습니다.");
        }
        User target = requireUser(userId);
        requireNotAdminAccount(target);

        UserSanction sanction = userSanctionMapper.findActiveByUserIdForUpdate(userId);
        if (sanction == null) {
            throw new SanctionValidationException(null, "적용 중인 이용제한이 없습니다.");
        }
        String releaseReason = normalizeReleaseReason(form == null ? null : form.getReleaseReason());

        finishSanction(target, sanction, SanctionStatus.LIFTED,
                SanctionReleaseVia.ADMIN, adminId, releaseReason);
        log.info("User sanction lifted: userId={}, sanctionId={}, adminId={}",
                userId, sanction.getId(), adminId);
    }

    /**
     * 기간이 지난 제재를 만료 처리한다.
     * 자동 만료 배치와 로그인 시 보조 확인이 함께 사용한다.
     */
    @Transactional
    public int expireDueSanctions(LocalDateTime now) {
        List<UserSanction> expired = userSanctionMapper.findExpiredActiveSanctions(now);
        int released = 0;
        for (UserSanction sanction : expired) {
            User target = userMapper.findByIdForUpdate(sanction.getUserId());
            if (target == null) {
                continue;
            }
            UserSanction locked = userSanctionMapper.findActiveByUserIdForUpdate(sanction.getUserId());
            if (locked == null || !locked.getId().equals(sanction.getId())) {
                continue;
            }
            finishSanction(target, locked, SanctionStatus.EXPIRED,
                    SanctionReleaseVia.SYSTEM, null, null);
            released++;
        }
        if (released > 0) {
            log.info("Expired sanctions released: count={}", released);
        }
        return released;
    }

    /**
     * 로그인 시 보조 확인. 만료된 기간제한이면 즉시 해제하고 true 를 돌려준다.
     * 배치가 멈춰 있어도 회원이 정상 로그인할 수 있게 하는 안전장치다.
     */
    @Transactional
    public boolean releaseIfExpired(Long userId) {
        UserSanction sanction = userSanctionMapper.findActiveByUserIdForUpdate(userId);
        if (sanction == null || sanction.getType() != SanctionType.TEMPORARY) {
            return false;
        }
        if (sanction.getExpiresAt() == null || sanction.getExpiresAt().isAfter(LocalDateTime.now())) {
            return false;
        }
        User target = userMapper.findByIdForUpdate(userId);
        if (target == null) {
            return false;
        }
        finishSanction(target, sanction, SanctionStatus.EXPIRED,
                SanctionReleaseVia.SYSTEM, null, null);
        return true;
    }

    /**
     * 제재 종료 공통 처리.
     * users.status 는 현재 RESTRICTED 일 때만 previous_status 로 복원한다.
     */
    private void finishSanction(User target,
                                UserSanction sanction,
                                SanctionStatus status,
                                SanctionReleaseVia via,
                                Long adminId,
                                String releaseReason) {
        LocalDateTime now = LocalDateTime.now();
        if (userSanctionMapper.release(sanction.getId(), status, now, adminId, via, releaseReason) != 1) {
            throw new IllegalStateException("이용제한 상태를 변경하지 못했습니다.");
        }
        if (target.getStatus() == UserStatus.RESTRICTED) {
            UserStatus restored = sanction.getPreviousStatus() == null
                    ? UserStatus.ACTIVE
                    : sanction.getPreviousStatus();
            if (userMapper.updateStatusForAdmin(target.getId(), restored, UserStatus.RESTRICTED) != 1) {
                throw new IllegalStateException("회원 상태를 복원하지 못했습니다.");
            }
        }
        if (sanction.getType() == SanctionType.PERMANENT) {
            blockedEmailMapper.releaseBySanctionId(sanction.getId(), now, adminId);
        }
    }

    /** 영구제한 회원은 같은 이메일로 재가입할 수 없도록 해시를 기록한다. */
    private void blockRegistrationEmail(User target, UserSanction sanction, Long adminId) {
        if (target.getUserEmail() == null || target.getUserEmail().isBlank()) {
            return;
        }
        BlockedEmail blockedEmail = new BlockedEmail();
        blockedEmail.setEmailHash(emailHasher.hash(target.getUserEmail()));
        blockedEmail.setUserId(target.getId());
        blockedEmail.setSanctionId(sanction.getId());
        blockedEmail.setReason(sanction.getReason());
        blockedEmail.setCreatedBy(adminId);
        blockedEmailMapper.insert(blockedEmail);
    }

    private User requireUser(Long userId) {
        User target = userMapper.findByIdForUpdate(userId);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다.");
        }
        return target;
    }

    /** 관리자 계정은 이용제한/해제 대상이 아니다. */
    private void requireNotAdminAccount(User target) {
        if (target.getUserRole() == UserRole.ADMIN) {
            throw new SanctionValidationException(null, "관리자 계정은 이용제한 대상이 아닙니다.");
        }
    }

    private LocalDateTime resolveExpiresAt(SanctionType type, LocalDateTime expiresAt) {
        if (type == SanctionType.PERMANENT) {
            return null;
        }
        if (expiresAt == null) {
            throw new SanctionValidationException("expiresAt", "기간제한은 종료일시를 입력해 주세요.");
        }
        if (!expiresAt.isAfter(LocalDateTime.now())) {
            throw new SanctionValidationException("expiresAt", "종료일시는 현재 시각보다 뒤여야 합니다.");
        }
        return expiresAt;
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.strip();
        if (normalized.isEmpty()) {
            throw new SanctionValidationException("reason", "제한 사유를 입력해 주세요.");
        }
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new SanctionValidationException(
                    "reason", "제한 사유는 " + MAX_REASON_LENGTH + "자 이하로 입력해 주세요.");
        }
        return normalized;
    }

    private String normalizeAdminNote(String adminNote) {
        if (adminNote == null || adminNote.isBlank()) {
            return null;
        }
        return adminNote.strip();
    }

    private String normalizeReleaseReason(String releaseReason) {
        if (releaseReason == null || releaseReason.isBlank()) {
            return null;
        }
        String normalized = releaseReason.strip();
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new SanctionValidationException(
                    "releaseReason", "해제 사유는 " + MAX_REASON_LENGTH + "자 이하로 입력해 주세요.");
        }
        return normalized;
    }

    private String statusRejectionMessage(UserStatus status) {
        return switch (status) {
            case RESTRICTED -> "이미 이용제한 상태인 회원입니다.";
            case DEACTIVATED -> "탈퇴한 회원은 이용제한 대상이 아닙니다.";
            case INACTIVE -> "이메일 인증을 마치지 않은 회원은 이용제한 대상이 아닙니다.";
            default -> "이용제한을 적용할 수 없는 회원 상태입니다.";
        };
    }
}