package com.example.travlediary.service.travelplan;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * 살아 있는 초대 링크를 OWNER 에게 다시 보여 주기 위한 raw token 암복호화.
 *
 * <p>토큰 생성과 검증용 해시는 {@link TravelPlanInviteToken} 이 맡는다.
 * 링크 검증은 계속 SHA-256 해시로만 하고, 여기서 만든 값은 재표시에만 쓴다.
 * 평문 token 은 DB 에 저장하지 않는다.
 *
 * <p>저장 형식은 {@code Base64url(IV ‖ ciphertext ‖ GCM tag)} 한 덩어리다.
 * 키는 환경변수로만 주입하며 값 자체는 어디에도 남기지 않는다.
 */
@Component
public class TravelPlanInviteTokenCipher {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    /** GCM 권장 IV 길이. 암호화마다 새로 뽑는다. */
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    /** AES-256 */
    private static final int KEY_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /** 설정되지 않았으면 null. 이 경우에도 앱은 뜨고 초대 발급에서만 막힌다. */
    private final SecretKey key;

    public TravelPlanInviteTokenCipher(
            @Value("${custom.invite-token-encryption-key:}") String base64Key) {
        this.key = readKey(base64Key);
    }

    /**
     * @return 저장할 Base64url 문자열
     * @throws IllegalStateException 키가 설정되지 않았거나 쓸 수 없을 때.
     *                               메시지에 키나 토큰 값을 담지 않는다.
     */
    public String encrypt(String rawToken) {
        if (rawToken == null) {
            throw new IllegalArgumentException("초대 토큰이 필요합니다.");
        }
        requireKey();

        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));

            // IV 를 앞에 붙여 한 문자열로 저장한다. IV 는 비밀이 아니다.
            byte[] stored = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, stored, 0, iv.length);
            System.arraycopy(encrypted, 0, stored, iv.length, encrypted.length);
            return ENCODER.encodeToString(stored);
        } catch (GeneralSecurityException exception) {
            // 원인 예외에 평문이 실려 나가지 않도록 메시지만 남긴다.
            throw new IllegalStateException("초대 링크를 안전하게 보관할 수 없습니다.");
        }
    }

    /**
     * 저장된 값을 raw token 으로 되돌린다.
     * 키가 없거나, 값이 깨졌거나, 다른 키로 만든 값이면 비어 있는 결과를 준다.
     * 재표시용이라 실패해도 화면이 죽지 않고 안내로 넘어가야 하기 때문이다.
     */
    public Optional<String> decrypt(String encryptedToken) {
        if (key == null || encryptedToken == null || encryptedToken.isBlank()) {
            return Optional.empty();
        }

        try {
            byte[] stored = DECODER.decode(encryptedToken);
            if (stored.length <= IV_BYTES) {
                return Optional.empty();
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES));
            byte[] rawToken = cipher.doFinal(stored, IV_BYTES, stored.length - IV_BYTES);
            return Optional.of(new String(rawToken, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            // 변조되었거나 키가 다르면 GCM 인증에서 걸린다. 값은 남기지 않는다.
            return Optional.empty();
        }
    }

    /** 지금 초대 링크를 새로 보관할 수 있는 상태인지. */
    public boolean isConfigured() {
        return key != null;
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException(
                    "초대 링크 암호화 키가 설정되지 않았습니다. "
                            + "환경변수 TRAVEL_PLAN_INVITE_ENCRYPTION_KEY 를 지정해 주세요.");
        }
    }

    /** 값이 비었으면 null 을 돌려주어 앱 기동 자체는 막지 않는다. */
    private SecretKey readKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            return null;
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "초대 링크 암호화 키를 읽을 수 없습니다. Base64 로 인코딩한 32바이트 값이어야 합니다.");
        }
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "초대 링크 암호화 키 길이가 올바르지 않습니다. 32바이트 키가 필요합니다.");
        }
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
}
