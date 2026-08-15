package com.example.travlediary.dto;

import com.example.travlediary.model.AppealStatus;
import com.example.travlediary.model.SanctionStatus;
import com.example.travlediary.model.SanctionType;
import lombok.Data;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/** 관리자 이의제기 목록·상세에 필요한 정보. */
@Data
public class AdminAppealDto {
    private Long id;
    private AppealStatus status;
    private String content;
    private LocalDateTime submittedAt;
    private String adminReply;
    private LocalDateTime handledAt;
    private String handledAdminName;

    /* 신청 회원 */
    private Long userId;
    private String username;
    private String nickname;
    private String userEmail;

    /* 연결된 제재 */
    private Long sanctionId;
    private SanctionType sanctionType;
    private SanctionStatus sanctionStatus;
    private String sanctionReason;
    private LocalDateTime sanctionStartsAt;
    private LocalDateTime sanctionExpiresAt;
    private Timestamp createdAt;

    public boolean isPending() {
        return status == AppealStatus.PENDING;
    }

    /** 제재가 아직 적용 중일 때만 승인으로 해제할 수 있다. */
    public boolean isSanctionActive() {
        return sanctionStatus == SanctionStatus.ACTIVE;
    }

    public boolean isPermanentSanction() {
        return sanctionType == SanctionType.PERMANENT;
    }
}
