package com.example.travlediary.dto.kto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KtoFestivalApiResponse(Response response) {

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
            String eventstartdate,
            String eventenddate,
            String firstimage,
            String firstimage2,
            String addr1,
            String addr2,
            // 외국어 축제 매칭에 쓰는 좌표. 국문 detailCommon2 가 내려준다.
            String mapx,
            String mapy,
            String eventplace,
            String overview,
            String playtime,
            String usetimefestival,
            String sponsor1,
            String sponsor1tel,
            String sponsor2,
            String sponsor2tel,
            String homepage,
            String eventhomepage,
            String tel,
            String cpyrhtDivCd,
            String imgname,
            String originimgurl,
            String serialnum,
            String smallimageurl,
            String lclsSystm1,
            String lclsSystm2,
            String lclsSystm3
    ) {
    }
}
