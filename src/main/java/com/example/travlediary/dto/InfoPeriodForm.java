package com.example.travlediary.dto;

import com.example.travlediary.model.InfoPeriod;
import lombok.Data;

import java.time.LocalDate;

@Data
public class InfoPeriodForm {

    private LocalDate startDate;
    private LocalDate endDate;

    public static InfoPeriodForm from(InfoPeriod period) {
        InfoPeriodForm form = new InfoPeriodForm();
        form.setStartDate(period.getStartDate());
        form.setEndDate(period.getEndDate());
        return form;
    }
}
