package com.example.travlediary.dto;

import com.example.travlediary.model.TravelInfoScope;
import lombok.Data;

import java.time.LocalDate;

@Data
public class FestivalCreateForm {

    private String title;
    private String content;
    private TravelInfoScope scope = TravelInfoScope.DOMESTIC;
    private Long categoryId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String eventPlace;
    private String address;
    private String playTime;
    private String useTime;
    private String sponsor1;
    private String sponsor1Tel;
    private String sponsor2;
    private String sponsor2Tel;
    private String contactTel;
    private String homepageUrl;
    private String ktoFestivalContentId;
    private String ktoThumbnailImageSelection;
}
