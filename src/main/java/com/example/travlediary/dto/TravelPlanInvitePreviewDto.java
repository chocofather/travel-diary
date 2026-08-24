package com.example.travlediary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

/**
 * 초대 링크를 연 사람에게 보여 주는 방 미리보기.
 * 회원 개인정보(이메일/아이디/닉네임)는 담지 않는다.
 * OWNER 이름은 users 가 아니라 travel_plan_members.display_name 에서 온다.
 */
@Data
@AllArgsConstructor
public class TravelPlanInvitePreviewDto {
    private Long travelPlanId;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    /** 없으면 화면에서 단색 자리를 쓴다. */
    private String representativeImageUrl;
    /** 현재 ACTIVE 참여자 수 */
    private int memberCount;
    /** 이 방에서 OWNER 가 쓰는 표시 이름 */
    private String ownerDisplayName;
    /**
     * 링크를 연 사람이 이미 이 방의 ACTIVE 참여자인지.
     * 참이면 미리보기 대신 방으로 바로 보낸다.
     */
    private boolean alreadyMember;
}
