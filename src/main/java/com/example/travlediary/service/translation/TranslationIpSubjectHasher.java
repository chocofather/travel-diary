package com.example.travlediary.service.translation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

@Component
public class TranslationIpSubjectHasher {
    private static final String ALGORITHM = "HmacSHA256";

    private final String secret;

    public TranslationIpSubjectHasher(
            @Value("${translation.daily-character-limit.ip-hmac-secret:}") String secret) {
        this.secret = secret;
    }

    public byte[] hash(String ipAddress) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "번역 IP 식별 secret이 설정되지 않았습니다. "
                            + "GOOGLE_TRANSLATION_DAILY_IP_HMAC_SECRET 환경변수를 확인해주세요.");
        }
        if (ipAddress == null || ipAddress.isBlank()) {
            throw new IllegalStateException("번역 요청의 원격 IP 주소를 확인할 수 없습니다.");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(ipAddress.trim().getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("번역 IP 식별자를 생성할 수 없습니다.", exception);
        }
    }
}
