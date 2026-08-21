package com.example.travlediary.dto.kto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KtoTourApiResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(Header header, Body body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(Integer numOfRows, Integer pageNo, Integer totalCount, JsonNode items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String contentid,
            String contenttypeid,
            String title,
            String addr1,
            String addr2,
            String mapx,
            String mapy,
            String overview,
            String homepage,
            String tel,
            String restdate,
            String usetime,
            String usefee,
            String infocenter,
            String expguide,
            String restdateculture,
            String usetimeculture,
            String infocenterculture,
            String restdateleports,
            String usetimeleports,
            String usefeeleports,
            String infocenterleports,
            String infocentertourcourse,
            String schedule,
            String restdateshopping,
            String opentime,
            String infocentershopping,
            // 음식점(39) detailIntro2 - 실제 응답 필드명 그대로, 값 변환 없이 문자열로 받는다
            String seat,
            String kidsfacility,
            String firstmenu,
            String treatmenu,
            String smoking,
            String packing,
            String infocenterfood,
            String scalefood,
            String parkingfood,
            String opendatefood,
            String opentimefood,
            String restdatefood,
            String discountinfofood,
            String chkcreditcardfood,
            String reservationfood,
            String lcnsno,
            // 숙박(32) detailIntro2 - 시설 플래그는 "0"/"1", roomcount 는 "192실" 형태의 문자열이다
            String roomcount,
            String roomtype,
            String refundregulation,
            String checkintime,
            String checkouttime,
            String chkcooking,
            String seminar,
            String sports,
            String sauna,
            String beauty,
            String beverage,
            String karaoke,
            String barbecue,
            String campfire,
            String bicycle,
            String fitness,
            String publicpc,
            String publicbath,
            String subfacility,
            String foodplace,
            String reservationurl,
            String pickup,
            String infocenterlodging,
            String parkinglodging,
            String reservationlodging,
            String scalelodging,
            String accomcountlodging
    ) {
    }
}
