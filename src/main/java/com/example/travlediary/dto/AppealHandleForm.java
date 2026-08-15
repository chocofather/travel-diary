package com.example.travlediary.dto;

import lombok.Data;

/** 관리자 이의제기 승인/기각 입력값. */
@Data
public class AppealHandleForm {
    /** 회원에게 안내할 처리 사유. 승인·기각 모두 필수. */
    private String adminReply;
}
