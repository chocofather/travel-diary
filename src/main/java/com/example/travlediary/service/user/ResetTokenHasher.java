package com.example.travlediary.service.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ResetTokenHasher {

    private static final String ALGORITHM = "SHA-256";

    private ResetTokenHasher() {
    }

    public static String hash(String rawToken) {
        if (rawToken == null) {
            throw new IllegalArgumentException("재설정 토큰이 필요합니다.");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("재설정 토큰을 안전하게 처리할 수 없습니다.", exception);
        }
    }
}
