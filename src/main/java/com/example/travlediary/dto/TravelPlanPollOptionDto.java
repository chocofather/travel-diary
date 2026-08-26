package com.example.travlediary.dto;

import com.example.travlediary.model.TravelPlanPollOption;

/** 화면으로 나가는 선택지 한 줄. 표 수는 아직 싣지 않는다(다음 단계). */
public record TravelPlanPollOptionDto(Long id, String content, Integer displayOrder) {

    public static TravelPlanPollOptionDto of(TravelPlanPollOption option) {
        return new TravelPlanPollOptionDto(
                option.getId(), option.getContent(), option.getDisplayOrder());
    }
}
