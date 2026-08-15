package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/** user_appeals 한 행. 이용제한 회원의 이의제기다. */
@Data
@NoArgsConstructor
public class UserAppeal {

    private Long id;
    private Long sanctionId;
    private Long userId;
    private AppealStatus status;
    private String content;
    private LocalDateTime submittedAt;
    private Long adminId;
    private String adminReply;
    private LocalDateTime handledAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public boolean isPending() {
        return status == AppealStatus.PENDING;
    }
}
