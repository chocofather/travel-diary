package com.example.travlediary.model;

/** 이용제한 이력의 상태. 회원당 ACTIVE 는 1건만 존재한다. */
public enum SanctionStatus {
    ACTIVE("적용중"),
    EXPIRED("기간만료"),
    LIFTED("해제됨");

    private final String displayName;

    SanctionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
