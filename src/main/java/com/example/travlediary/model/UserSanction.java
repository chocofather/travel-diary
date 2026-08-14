package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/** user_sanctions 한 행. 회원 이용제한 이력이다. */
@Data
@NoArgsConstructor
public class UserSanction {

    private Long id;
    private Long userId;
    private SanctionType type;
    private SanctionStatus status;
    private String reason;          // 회원 안내용 사유
    private String adminNote;       // 내부 메모(비공개)
    private UserStatus previousStatus; // 제재 직전 users.status (해제 시 복원)
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;   // TEMPORARY 만 값이 있다
    private LocalDateTime releasedAt;
    private Long releasedBy;
    private SanctionReleaseVia releasedVia;
    private String releaseReason;
    private Long createdBy;         // 조치 관리자
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public boolean isPermanent() {
        return type == SanctionType.PERMANENT;
    }

    public boolean isActive() {
        return status == SanctionStatus.ACTIVE;
    }
}