package com.example.travlediary.service.diary;

public class DiaryCoverLibraryReportException extends RuntimeException {

    public enum Reason {
        DUPLICATE,
        UNAVAILABLE,
        INVALID_PHOTO,
        INVALID_REQUEST
    }

    private final Reason reason;

    public DiaryCoverLibraryReportException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
