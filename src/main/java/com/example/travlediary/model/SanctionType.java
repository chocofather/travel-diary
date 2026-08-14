package com.example.travlediary.model;

/** 회원 이용제한 유형. */
public enum SanctionType {
    TEMPORARY("기간제한"),
    PERMANENT("영구제한");

    private final String displayName;

    SanctionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
