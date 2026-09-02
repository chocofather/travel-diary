package com.example.travlediary.service.user;

public class SocialProviderUnlinkException extends RuntimeException {

    public SocialProviderUnlinkException() {
        super("소셜 계정 연결 해제에 실패했습니다.");
    }

    public SocialProviderUnlinkException(Throwable cause) {
        super("소셜 계정 연결 해제에 실패했습니다.", cause);
    }
}
