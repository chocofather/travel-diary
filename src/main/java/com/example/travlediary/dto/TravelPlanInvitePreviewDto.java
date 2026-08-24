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
    /** 방 정원. 화면과 정원 검사가 같은 값을 쓰도록 서버가 내려 준다. */
    private int memberLimit;
    /** 이 방에서 OWNER 가 쓰는 표시 이름 */
    private String ownerDisplayName;
    /**
     * 링크를 연 사람이 이미 이 방의 ACTIVE 참여자인지.
     * 참이면 미리보기 대신 방으로 바로 보낸다.
     */
    private boolean alreadyMember;
    /**
     * 나갔거나 내보내진 기록이 있어 이 링크로는 다시 들어올 수 없는지.
     * 재참여 정책은 다음 단계에서 다룬다.
     */
    private boolean joinBlocked;

    /** 정원이 찼는지. 화면이 참여 버튼 대신 안내를 보여 줄 때 쓴다. */
    public boolean isFull() {
        return memberCount >= memberLimit;
    }
}
