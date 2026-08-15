package com.example.travlediary.service.user;

import com.example.travlediary.model.AppealStatus;
import com.example.travlediary.model.UserAppeal;
import com.example.travlediary.model.UserSanction;
import com.example.travlediary.repository.user.UserAppealMapper;
import com.example.travlediary.repository.user.UserSanctionMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 이용제한 회원의 이의제기 접수.
 * 대상 제재는 항상 로그인한 회원의 현재 ACTIVE 제재에서 서버가 직접 찾는다.
 */
@Service
@RequiredArgsConstructor
public class UserAppealService {

    private static final Logger log = LoggerFactory.getLogger(UserAppealService.class);
    private static final int MAX_CONTENT_LENGTH = 2000;

    private final UserSanctionMapper userSanctionMapper;
    private final UserAppealMapper userAppealMapper;

    /** 제한 안내 화면에 표시할 현재 제재의 최신 이의제기. */
    @Transactional(readOnly = true)
    public UserAppeal getLatestAppeal(Long sanctionId) {
        if (sanctionId == null) {
            return null;
        }
        return userAppealMapper.findLatestBySanctionId(sanctionId);
    }

    /**
     * 이의제기 제출.
     * sanctionId 를 입력값으로 받지 않으므로 다른 회원·다른 제재를 지정할 수 없다.
     */
    @Transactional
    public void submit(Long userId, String content) {
        if (userId == null) {
            throw new AppealValidationException(null, "로그인 정보를 확인할 수 없습니다.");
        }
        String normalizedContent = normalizeContent(content);

        UserSanction sanction = userSanctionMapper.findActiveByUserIdForUpdate(userId);
        if (sanction == null) {
            throw new AppealValidationException(null, "이의제기할 이용제한이 없습니다.");
        }
        if (userAppealMapper.findPendingBySanctionId(sanction.getId()) != null) {
            throw new AppealValidationException(null, "이미 접수된 이의제기가 처리 중입니다.");
        }

        UserAppeal appeal = new UserAppeal();
        appeal.setSanctionId(sanction.getId());
        appeal.setUserId(userId);
        appeal.setStatus(AppealStatus.PENDING);
        appeal.setContent(normalizedContent);
        appeal.setSubmittedAt(LocalDateTime.now());
        if (userAppealMapper.insert(appeal) != 1) {
            throw new IllegalStateException("이의제기를 접수하지 못했습니다.");
        }
        log.info("Appeal submitted: userId={}, sanctionId={}", userId, sanction.getId());
    }

    private String normalizeContent(String content) {
        String normalized = content == null ? "" : content.strip();
        if (normalized.isEmpty()) {
            throw new AppealValidationException("content", "이의제기 내용을 입력해 주세요.");
        }
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new AppealValidationException(
                    "content", "이의제기 내용은 " + MAX_CONTENT_LENGTH + "자 이하로 입력해 주세요.");
        }
        return normalized;
    }
}
