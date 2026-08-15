package com.example.travlediary.model;

/** 이의제기 상태. 로그인 경로 제출은 바로 PENDING 으로 접수된다. */
public enum AppealStatus {
    DRAFT("작성중"),
    PENDING("접수됨"),
    APPROVED("승인됨"),
    REJECTED("기각됨");

    private final String displayName;

    AppealStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
