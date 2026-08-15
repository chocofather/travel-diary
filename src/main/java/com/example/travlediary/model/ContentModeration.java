package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/** content_moderations 한 행. 관리자 콘텐츠 조치 이력이다. */
@Data
@NoArgsConstructor
public class ContentModeration {

    private Long id;
    private ModerationTargetType targetType;
    private Long targetId;
    private Long targetUserId;      // 콘텐츠 작성자
    private ModerationStatus status;
    private String reason;          // 숨김 사유
    private String adminNote;       // 내부 메모(비공개)
    private Long createdBy;
    private Timestamp createdAt;
    private LocalDateTime restoredAt;
    private Long restoredBy;
    private String restoreReason;

    public boolean isActive() {
        return status == ModerationStatus.ACTIVE;
    }
}
