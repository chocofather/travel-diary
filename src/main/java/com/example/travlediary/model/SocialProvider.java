package com.example.travlediary.model;

import java.util.Optional;

public enum SocialProvider {
    GOOGLE,
    KAKAO,
    NAVER;

    public static Optional<SocialProvider> fromRegistrationId(String registrationId) {
        if (registrationId == null) {
            return Optional.empty();
        }
        return switch (registrationId) {
            case "google" -> Optional.of(GOOGLE);
            case "kakao" -> Optional.of(KAKAO);
            case "naver" -> Optional.of(NAVER);
            default -> Optional.empty();
        };
    }
}
