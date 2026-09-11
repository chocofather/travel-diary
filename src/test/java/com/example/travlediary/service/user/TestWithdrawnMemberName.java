package com.example.travlediary.service.user;

import org.springframework.context.support.ResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;

/**
 * 댓글 서비스 단위 테스트용 {@link WithdrawnMemberName}.
 * 실제 messages 번들을 그대로 읽으므로 언어별 문구까지 같이 검증할 수 있다.
 */
public final class TestWithdrawnMemberName {

    private TestWithdrawnMemberName() {
    }

    public static WithdrawnMemberName real() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setFallbackToSystemLocale(false);
        return new WithdrawnMemberName(messageSource);
    }
}
