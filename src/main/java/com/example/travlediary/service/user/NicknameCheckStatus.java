package com.example.travlediary.service.user;

public enum NicknameCheckStatus {
    AVAILABLE(true, "사용 가능한 닉네임입니다."),
    CURRENT(true, "현재 사용 중인 닉네임입니다."),
    INVALID_FORMAT(false, NicknamePolicy.INVALID_MESSAGE),
    FORBIDDEN(false, NicknamePolicy.FORBIDDEN_MESSAGE),
    DUPLICATE(false, "이미 사용 중인 닉네임입니다.");

    private final boolean available;
    private final String message;

    NicknameCheckStatus(boolean available, String message) {
        this.available = available;
        this.message = message;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getMessage() {
        return message;
    }
}
