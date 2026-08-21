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
            String infocentershopping
    ) {
    }
}
