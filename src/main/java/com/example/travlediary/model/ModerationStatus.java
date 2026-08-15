package com.example.travlediary.model;

/** 관리자 조치 상태. 대상별 ACTIVE 는 1건만 존재한다. */
public enum ModerationStatus {
    ACTIVE("조치중"),
    RESTORED("복구됨");

    private final String displayName;

    ModerationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
