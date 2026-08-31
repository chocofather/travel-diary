package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
public class FestivalInfo {
    private Long infoId;
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
    private String sourceType;
    private String externalContentId;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
