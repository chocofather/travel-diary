package com.example.travlediary.dto;

import com.example.travlediary.model.FestivalInfo;
import lombok.Getter;
import lombok.Setter;

/** Home 표시용. 상태는 조회에 사용한 애플리케이션 날짜와 함께 결정한다. */
@Getter
@Setter
public class HomeFestivalDto extends TravelInfoListItemDto {
    private FestivalInfo festivalInfo;
    private String location;
    private String eventStatus;
}
