package com.example.travlediary.dto;

import lombok.Data;

/**
 * A 일정 추가 폼.
 * 장소/시간을 나누지 않고 자유 텍스트 한 덩어리로 받는다.
 */
@Data
public class TravelPlanItemCreateForm {
    private String content;
}
