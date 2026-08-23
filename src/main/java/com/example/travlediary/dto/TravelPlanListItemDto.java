package com.example.travlediary.dto;

import com.example.travlediary.model.TravelPlanRole;
import lombok.Data;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * "함께 계획하기" 목록 한 줄.
 * memberCount 처럼 화면에만 필요한 값이 있어 도메인 모델 대신 별도 DTO 로 읽는다.
 */
@Data
public class TravelPlanListItemDto {
    private Long travelPlanId;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private String representativeImageUrl;
    /** 이 방에서의 현재 사용자 역할 */
    private TravelPlanRole role;
    /** ACTIVE 상태인 참여자 수 */
    private int memberCount;
    private Timestamp lastActivityAt;

    /** 시작일과 종료일을 포함한 여행 일수. 화면 표시용 파생값이다. */
    public int getDayCount() {
        if (startDate == null || endDate == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }
}
