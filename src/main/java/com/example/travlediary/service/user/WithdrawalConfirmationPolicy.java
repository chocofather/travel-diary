package com.example.travlediary.service.user;

import com.example.travlediary.config.i18n.SupportedLanguage;
import org.springframework.context.MessageSource;

import java.util.Locale;

/**
 * 회원탈퇴 확인 문구 검증.
 *
 * <p>화면이 보여준 문구를 그대로 입력해야 탈퇴가 접수된다.
 * 버튼 활성화는 클라이언트 편의일 뿐이고 실제 판단은 서버의 이 검사다.
 *
 * <p>입력 도중 언어를 바꾸는 경우가 있으므로 요청 locale 하나만 보지 않고
 * 지원 언어 5개의 문구를 모두 정답으로 인정한다.
 */
public final class WithdrawalConfirmationPolicy {

    /** 화면 안내와 서버 검증이 같은 문구를 쓰도록 key 를 한 곳에서 관리한다. */
    public static final String PHRASE_MESSAGE_CODE = "mypage.account.withdrawal.confirm.phrase";

    private WithdrawalConfirmationPolicy() {
    }

    public static boolean matches(String input, MessageSource messageSource) {
        String normalizedInput = normalize(input);
        if (normalizedInput.isEmpty()) {
            return false;
        }
        for (SupportedLanguage language : SupportedLanguage.all()) {
            String expected = normalize(
                    messageSource.getMessage(PHRASE_MESSAGE_CODE, null, language.getLocale()));
            if (!expected.isEmpty() && expected.equals(normalizedInput)) {
                return true;
            }
        }
        return false;
    }

    /** 앞뒤 공백과 중복 공백, 영문 대소문자 차이는 오타로 보지 않는다. */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
