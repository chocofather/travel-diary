package com.example.travlediary.model;

public enum TravelInfoContentType {
    GENERAL, FESTIVAL,
    /** 지역에 매이지 않는 공통 여행정보. scope 가 없고, content_type = GUIDE 인 전용 카테고리를 쓴다. */
    GUIDE
}
