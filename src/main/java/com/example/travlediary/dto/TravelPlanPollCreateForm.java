package com.example.travlediary.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 투표 만들기 화면이 보내는 값.
 *
 * <p>여기 없는 것은 서버가 정한다.
 * 만든 사람(memberId), 진행 상태, 결과 공개 시점, 마감 방식은 클라이언트에서 받지 않는다.
 * 방 번호도 이 폼이 아니라 URL 에서만 온다.
 */
@Data
@NoArgsConstructor
public class TravelPlanPollCreateForm {
    /** 투표 질문. travel_plan_polls.title 로 저장된다. */
    private String question;
    /** SINGLE / MULTIPLE. 서버가 허용값인지 다시 본다. */
    private String selectionType;
    private List<String> options = new ArrayList<>();
}
