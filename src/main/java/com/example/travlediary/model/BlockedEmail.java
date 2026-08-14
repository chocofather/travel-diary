package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/** blocked_emails 한 행. 원본 이메일은 저장하지 않고 해시만 보관한다. */
@Data
@NoArgsConstructor
public class BlockedEmail {

    private Long id;
    private String emailHash;
    private Long userId;
    private Long sanctionId;
    private String reason;
    private Long createdBy;
    private Timestamp createdAt;
    private LocalDateTime releasedAt;
    private Long releasedBy;
}