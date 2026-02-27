package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class FestivalPeriod {
    private Long id; // 축제기간번호
    private LocalDate startDate; // 시작일
    private LocalDate endDate; // 종료일
    private Long infoId; // 정보번호
}
