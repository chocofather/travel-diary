package com.example.travlediary.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 투표하기 화면이 보내는 값.
 *
 * <p>고른 선택지 번호가 전부다.
 * 누가 투표하는지는 로그인 정보에서만 나오고, 방·투표는 URL 에서만 온다.
 * 넘어온 선택지가 정말 그 투표의 것인지도 서버가 다시 본다.
 */
@Data
@NoArgsConstructor
public class TravelPlanPollVoteForm {
    private List<Long> optionIds = new ArrayList<>();
}
