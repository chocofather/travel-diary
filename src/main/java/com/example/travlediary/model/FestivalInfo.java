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
    /**
     * 개최 시작 연도. 언제나 이 개최분 시작일의 연도이며 화면 입력값이 아니다.
     * 같은 TourAPI contentId 라도 연도가 다르면 다른 개최분이다.
     */
    private Integer eventYear;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
