package com.example.travlediary.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * 최종 탈퇴(DEACTIVATED) 회원의 작성자 표시 이름.
 *
 * <p>users.nickname 은 UNIQUE 충돌을 피하려고 바꿔 둔 내부 익명값이라 화면에 그대로 쓰면 안 된다.
 * 서버가 HTML 을 그리는 화면은 템플릿에서 {@code #{member.withdrawn}} 으로 직접 고르고,
 * JSON 으로 내려가 브라우저가 그리는 댓글은 응답을 만들 때 여기에서 현재 언어 문구로 바꾼다.
 * 그래야 익명 닉네임이 응답에도 실리지 않는다.
 */
@Component
@RequiredArgsConstructor
public class WithdrawnMemberName {

    /** 5개 언어 번들에 모두 있는 공통 키. */
    public static final String MESSAGE_CODE = "member.withdrawn";

    private final MessageSource messageSource;

    /** 현재 요청 언어의 "탈퇴한 회원" 문구. */
    public String current() {
        return messageSource.getMessage(MESSAGE_CODE, null, LocaleContextHolder.getLocale());
    }

    /**
     * @param withdrawn users.status = DEACTIVATED 인지. 닉네임 문자열로 판정하지 않는다.
     * @return 최종 탈퇴 회원이면 공통 문구, 아니면 원래 닉네임 그대로
     */
    public String orNickname(String nickname, boolean withdrawn) {
        if (!withdrawn || nickname == null) {
            // 삭제된 댓글처럼 애초에 닉네임을 내려주지 않는 경우는 그대로 둔다.
            return nickname;
        }
        return current();
    }
}
