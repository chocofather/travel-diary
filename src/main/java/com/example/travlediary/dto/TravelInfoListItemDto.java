package com.example.travlediary.dto;

import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import lombok.Data;

import java.sql.Timestamp;
import java.time.LocalDate;

@Data
public class TravelInfoListItemDto {

    private Long id;
    private String title;
    private TravelInfoScope scope;
    private TravelInfoContentType contentType;
    private Long categoryId;
    private String categoryName;
    private String thumbnailUrl;
    private int views;
    private Timestamp createdAt;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean bookmarked;

    /**
     * 카드에 찍을 개최 상태. 축제·행사가 아니면 null 이다.
     *
     * <p>목록 조회가 이미 고른 <b>대표 행사기간</b>(startDate/endDate)만 보고 정한다.
     * 상태 필터·행사일순 정렬이 쓰는 그 한 줄이라 화면과 필터 결과가 어긋나지 않는다.
     * 같은 판정을 SQL 에 한 번 더 쓰지 않으려고 여기서만 계산한다.
     */
    public String getEventStatus() {
        if (contentType != TravelInfoContentType.FESTIVAL
                || startDate == null || endDate == null) {
            return null;
        }
        LocalDate today = LocalDate.now();
        if (startDate.isAfter(today)) {
            return "upcoming";
        }
        if (endDate.isBefore(today)) {
            return "ended";
        }
        return "ongoing";
    }
}
