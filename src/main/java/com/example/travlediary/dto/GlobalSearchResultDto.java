package com.example.travlediary.dto;

import lombok.Data;

import java.sql.Timestamp;
import java.time.LocalDate;

@Data
public class GlobalSearchResultDto {

    private String type;
    private Long id;
    private String title;
    private String summary;
    private Timestamp createdAt;
    private String detailUrl;
    private String thumbnailUrl;
    private LocalDate startDate;
    private LocalDate endDate;

    public String getTypeLabel() {
        return switch (type == null ? "" : type) {
            case "destination" -> "여행지";
            case "community" -> "커뮤니티";
            case "course" -> "여행코스";
            case "travel-info" -> "여행정보";
            case "event" -> "이벤트";
            case "notice" -> "공지사항";
            default -> "검색 결과";
        };
    }
}
