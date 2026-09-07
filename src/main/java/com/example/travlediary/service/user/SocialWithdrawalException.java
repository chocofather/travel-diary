package com.example.travlediary.service.user;

/**
 * 소셜 계정 탈퇴 처리 오류.
 *
 * <p>계정 관리 화면에 그대로 노출되는 오류만 메시지 코드를 함께 들고 다닌다.
 * 코드가 없거나 번들에 없으면 여기 담긴 한국어 문구가 그대로 쓰인다.
 */
public class SocialWithdrawalException extends RuntimeException {

    private final String messageCode;

    public SocialWithdrawalException(String message) {
        this(null, message);
    }

    public SocialWithdrawalException(String messageCode, String message) {
        super(message);
        this.messageCode = messageCode;
    }

    public String getMessageCode() {
        return messageCode;
    }
}
