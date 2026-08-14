package com.example.travlediary.service.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * 재가입 차단용 이메일 식별 해시.
 * 원본 이메일을 보관하지 않기 위해 HMAC-SHA-256 결과만 저장한다.
 * secret 은 환경변수로만 주입하며 코드나 설정파일에 값을 두지 않는다.
 */
@Component
public class EmailHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final String secret;

    public EmailHasher(@Value("${custom.email-hash-secret:}") String secret) {
        this.secret = secret;
    }

    /**
     * 가입/중복검사와 동일한 정규화 규칙을 쓰기 위해
     * EmailPolicy.normalizeAndValidate() 결과를 그대로 해시한다.
     */
    public String hash(String email) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "이메일 해시 secret이 설정되지 않았습니다. EMAIL_HASH_SECRET 환경변수를 확인해주세요.");
        }
        String normalized = EmailPolicy.normalizeAndValidate(email);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("이메일 해시를 생성할 수 없습니다.", exception);
        }
    }
}