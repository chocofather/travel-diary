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

    /** 결과 종류 배지는 화면에서 현재 언어로 읽는다. */
    public String getTypeMessageKey() {
        return switch (type == null ? "" : type) {
            case "destination", "community", "course", "travel-info", "event", "notice" ->
                    "search.type." + type;
            default -> "search.type.unknown";
        };
    }
}
