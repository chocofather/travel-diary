package com.example.travlediary.model;

public enum InquiryStatus {
    PENDING("답변대기"),
    IN_PROGRESS("처리중"),
    ANSWERED("답변완료"),
    CANCELLED("취소됨");

    private final String displayName;

    InquiryStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
