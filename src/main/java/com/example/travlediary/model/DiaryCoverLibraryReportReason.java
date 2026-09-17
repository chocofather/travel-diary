package com.example.travlediary.model;

public enum DiaryCoverLibraryReportReason {
    COPYRIGHT("저작권 침해"),
    PORTRAIT_PRIVACY("초상권·개인정보 침해"),
    INAPPROPRIATE("부적절한 콘텐츠"),
    SPAM("스팸·광고"),
    OTHER("기타");

    private final String displayName;

    DiaryCoverLibraryReportReason(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
