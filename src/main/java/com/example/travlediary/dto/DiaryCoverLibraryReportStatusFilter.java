package com.example.travlediary.dto;

import com.example.travlediary.model.DiaryCoverLibraryReportStatus;

import java.util.Locale;

public enum DiaryCoverLibraryReportStatusFilter {
    PENDING("대기 중", DiaryCoverLibraryReportStatus.PENDING),
    RESOLVED("처리 완료", DiaryCoverLibraryReportStatus.RESOLVED),
    REJECTED("기각", DiaryCoverLibraryReportStatus.REJECTED),
    ALL("전체", null);

    private final String label;
    private final DiaryCoverLibraryReportStatus status;

    DiaryCoverLibraryReportStatusFilter(String label,
                                        DiaryCoverLibraryReportStatus status) {
        this.label = label;
        this.status = status;
    }

    public String getLabel() {
        return label;
    }

    public DiaryCoverLibraryReportStatus getStatus() {
        return status;
    }

    public static DiaryCoverLibraryReportStatusFilter of(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return PENDING;
        }
    }
}
