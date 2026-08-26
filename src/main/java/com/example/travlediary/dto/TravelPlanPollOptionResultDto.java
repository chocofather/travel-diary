package com.example.travlediary.dto;

/**
 * 상세 화면의 선택지 한 줄.
 *
 * @param voteCount 이 선택지를 고른 사람 수.
 *                  여러 개 선택 투표에서는 이 값들의 합이 참여 인원과 다를 수 있다.
 *                  결과를 마감 뒤에 공개하는 투표라면 진행 중에는 null 이다.
 *                  0 으로 내리면 "아무도 안 골랐다" 로 읽히므로 아예 담지 않는다.
 */
public record TravelPlanPollOptionResultDto(Long id, String content, Integer voteCount) {

    /** 아직 공개할 때가 아닌 선택지. 무엇이 있는지만 알린다. */
    public static TravelPlanPollOptionResultDto hidden(Long id, String content) {
        return new TravelPlanPollOptionResultDto(id, content, null);
    }
}
