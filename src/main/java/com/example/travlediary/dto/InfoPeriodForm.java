package com.example.travlediary.dto;

import com.example.travlediary.model.InfoPeriod;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class InfoPeriodForm {

    // input type="date" 는 ISO(yyyy-MM-dd) 값만 읽고 쓴다. 없으면 화면 언어에 맞춘
    // 짧은 날짜로 찍혀 브라우저가 값을 버리고 빈 칸으로 보인다.
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    public static InfoPeriodForm from(InfoPeriod period) {
        InfoPeriodForm form = new InfoPeriodForm();
        form.setStartDate(period.getStartDate());
        form.setEndDate(period.getEndDate());
        return form;
    }
}
