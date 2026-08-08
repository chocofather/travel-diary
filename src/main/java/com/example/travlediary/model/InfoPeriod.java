package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class InfoPeriod {
    private Long id; // 여행정보 기간 번호
    private LocalDate startDate; // 시작일
    private LocalDate endDate; // 종료일
    private Long infoId; // 여행정보 번호
}
