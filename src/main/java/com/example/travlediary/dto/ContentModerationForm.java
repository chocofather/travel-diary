package com.example.travlediary.dto;

import lombok.Data;

/** 관리자 콘텐츠 숨김/복구 입력값. */
@Data
public class ContentModerationForm {
    private String reason;     // 숨김 사유(필수) 또는 복구 사유(선택)
    private String adminNote;  // 선택
    private String redirect;   // 처리 후 돌아갈 내부 경로
}
