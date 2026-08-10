package com.example.travlediary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TravelInfoPeriodDto {

    private LocalDate startDate;
    private LocalDate endDate;
}
