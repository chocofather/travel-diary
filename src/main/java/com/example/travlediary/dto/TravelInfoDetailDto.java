package com.example.travlediary.dto;

import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

@Data
public class TravelInfoDetailDto {

    private Long id;
    private String title;
    private TravelInfoScope scope;
    private TravelInfoContentType contentType;
    private String categoryName;
    private String content;
    private Integer views;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private List<TravelInfoPeriodDto> periods = List.of();

    public boolean isUpdated() {
        if (createdAt == null || updatedAt == null) {
            return false;
        }
        return updatedAt.toLocalDateTime().toLocalDate()
                .isAfter(createdAt.toLocalDateTime().toLocalDate());
    }
}
