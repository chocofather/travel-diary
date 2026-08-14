package com.example.travlediary.dto;

import lombok.Data;

/** 관리자 이용제한 해제 입력값. */
@Data
public class SanctionReleaseForm {
    private String releaseReason; // 선택
}
