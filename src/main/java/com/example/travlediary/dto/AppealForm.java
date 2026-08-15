package com.example.travlediary.dto;

import lombok.Data;

/** 이용제한 회원의 이의제기 입력값. 대상 제재는 서버가 결정한다. */
@Data
public class AppealForm {
    private String content;
}
