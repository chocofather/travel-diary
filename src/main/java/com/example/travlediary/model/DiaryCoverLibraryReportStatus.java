package com.example.travlediary.model;

public enum DiaryCoverLibraryReportStatus {
    PENDING("대기 중"),
    RESOLVED("처리 완료"),
    REJECTED("기각");

    private final String displayName;

    DiaryCoverLibraryReportStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
