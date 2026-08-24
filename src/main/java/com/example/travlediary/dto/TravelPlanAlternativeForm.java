package com.example.travlediary.dto;

import lombok.Data;

/**
 * 대안(B/C) 추가/수정 폼.
 * conditionLabel 은 "비가 많이 올 때" 같은 조건이며 비워 둘 수 있다.
 * version 은 수정에서만 쓰고, 화면이 들고 있던 값을 서버가 낙관적 잠금 조건으로 쓴다.
 */
@Data
public class TravelPlanAlternativeForm {
    private String conditionLabel;
    private String content;
    private Integer version;
}
