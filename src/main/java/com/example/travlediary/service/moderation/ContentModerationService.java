package com.example.travlediary.service.moderation;

import com.example.travlediary.dto.ContentModerationForm;
import com.example.travlediary.dto.ModeratedContentDto;
import com.example.travlediary.model.ContentModeration;
import com.example.travlediary.model.ModerationStatus;
import com.example.travlediary.model.ModerationTargetType;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.repository.moderation.ContentModerationMapper;
import com.example.travlediary.repository.user.UserMapper;
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
 * 관리자 콘텐츠 조치.
 * 숨김은 기존 deleted 플래그를 그대로 쓰고, 관리자 조치 여부는 content_moderations 로 구분한다.
 * 활성 조치 행이 없는 삭제 콘텐츠는 사용자가 직접 지운 것이므로 복구 대상이 아니다.
 */
@Service
@RequiredArgsConstructor
public class ContentModerationService {

    private static final Logger log = LoggerFactory.getLogger(ContentModerationService.class);
    private static final int MAX_REASON_LENGTH = 500;

    private final ContentModerationMapper contentModerationMapper;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public ContentModeration getActiveModeration(ModerationTargetType targetType, Long targetId) {
        return contentModerationMapper.findActiveByTarget(targetType, targetId);
    }

    @Transactional(readOnly = true)
    public List<ContentModeration> getModerationHistory(ModerationTargetType targetType,
                                                        Long targetId) {
        return contentModerationMapper.findByTarget(targetType, targetId);
    }

    /** 조치 중(ACTIVE)인 콘텐츠만 센다. 사용자 직접 삭제분은 포함되지 않는다. */
    @Transactional(readOnly = true)
    public long countModeratedContents(ModerationTargetType targetType, String keyword) {
        return contentModerationMapper.countModeratedContents(targetType, keyword);
    }

    @Transactional(readOnly = true)
    public List<ModeratedContentDto> getModeratedContents(ModerationTargetType targetType,
                                                          String keyword,
                                                          long offset,
                                                          int limit) {
        return contentModerationMapper.findModeratedContents(targetType, keyword, offset, limit);
    }

    /** 관리자 숨김. 아직 노출 중인 콘텐츠만 대상으로 한다. */
    @Transactional
    public void hide(ModerationTargetType targetType,
                     Long targetId,
                     ContentModerationForm form,
                     Long adminId) {
        requireAdmin(adminId);
        String reason = normalizeReason(form == null ? null : form.getReason(),
                "숨김 사유를 입력해 주세요.");
        String adminNote = normalizeOptional(form.getAdminNote());

        // 사용자가 이미 지운 콘텐츠에는 조치 행을 만들지 않는다.
        Long ownerId = contentModerationMapper.findActiveTargetOwnerId(targetType, targetId);
        if (ownerId == null) {
            throw new ModerationValidationException(null, "이미 삭제되었거나 조치된 콘텐츠입니다.");
        }
        if (contentModerationMapper.findActiveByTargetForUpdate(targetType, targetId) != null) {
            throw new ModerationValidationException(null, "이미 조치 중인 콘텐츠입니다.");
        }
        if (contentModerationMapper.hideTarget(targetType, targetId) != 1) {
            throw new ModerationValidationException(null, "이미 삭제되었거나 조치된 콘텐츠입니다.");
        }

        ContentModeration moderation = new ContentModeration();
        moderation.setTargetType(targetType);
        moderation.setTargetId(targetId);
        moderation.setTargetUserId(ownerId);
        moderation.setStatus(ModerationStatus.ACTIVE);
        moderation.setReason(reason);
        moderation.setAdminNote(adminNote);
        moderation.setCreatedBy(adminId);
        if (contentModerationMapper.insert(moderation) != 1) {
            throw new IllegalStateException("조치 이력을 저장하지 못했습니다.");
        }
        log.info("Content hidden by admin: type={}, id={}, adminId={}",
                targetType, targetId, adminId);
    }

    /** 관리자 조치 복구. 활성 조치 행이 있는 콘텐츠만 되돌린다. */
    @Transactional
    public void restore(ModerationTargetType targetType,
                        Long targetId,
                        ContentModerationForm form,
                        Long adminId) {
        requireAdmin(adminId);
        String restoreReason = normalizeOptional(form == null ? null : form.getReason());
        if (restoreReason != null && restoreReason.length() > MAX_REASON_LENGTH) {
            throw new ModerationValidationException(
                    "reason", "복구 사유는 " + MAX_REASON_LENGTH + "자 이하로 입력해 주세요.");
        }

        ContentModeration moderation =
                contentModerationMapper.findActiveByTargetForUpdate(targetType, targetId);
        if (moderation == null) {
            // 사용자가 직접 삭제한 콘텐츠는 여기로 들어오지 못한다.
            throw new ModerationValidationException(null, "복구할 수 있는 관리자 조치가 없습니다.");
        }
        if (contentModerationMapper.findHiddenTargetOwnerId(targetType, targetId) == null) {
            throw new ModerationValidationException(null, "복구할 콘텐츠를 찾을 수 없습니다.");
        }
        if (contentModerationMapper.restoreTarget(targetType, targetId) != 1) {
            throw new ModerationValidationException(null, "복구할 콘텐츠를 찾을 수 없습니다.");
        }
        if (contentModerationMapper.restoreModeration(moderation.getId(), LocalDateTime.now(),
                adminId, restoreReason) != 1) {
            throw new IllegalStateException("조치 이력을 갱신하지 못했습니다.");
        }
        log.info("Content restored by admin: type={}, id={}, adminId={}",
                targetType, targetId, adminId);
    }

    /** 화면 권한과 별개로 서비스에서도 관리자 권한을 최종 확인한다. */
    private void requireAdmin(Long adminId) {
        if (adminId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 조치할 수 있습니다.");
        }
        User admin = userMapper.findById(adminId);
        if (admin == null || admin.getUserRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 조치할 수 있습니다.");
        }
    }

    private String normalizeReason(String reason, String message) {
        String normalized = reason == null ? "" : reason.strip();
        if (normalized.isEmpty()) {
            throw new ModerationValidationException("reason", message);
        }
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new ModerationValidationException(
                    "reason", "사유는 " + MAX_REASON_LENGTH + "자 이하로 입력해 주세요.");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
