package com.example.travlediary.dto;

import com.example.travlediary.model.SanctionType;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/** 관리자 회원 이용제한 입력값. */
@Data
public class UserSanctionForm {
    private SanctionType type = SanctionType.TEMPORARY;
    private String reason;      // 필수
    private String adminNote;   // 선택

    /** TEMPORARY 일 때만 사용한다. datetime-local 입력을 그대로 받는다. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime expiresAt;
}
