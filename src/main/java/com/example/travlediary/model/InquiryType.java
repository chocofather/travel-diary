package com.example.travlediary.model;

public enum InquiryType {
    ACCOUNT("회원/계정"),
    TRAVEL_INFO("여행정보"),
    COMMUNITY("커뮤니티"),
    ERROR("오류/장애"),
    OTHER("기타");

    private final String displayName;

    InquiryType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
