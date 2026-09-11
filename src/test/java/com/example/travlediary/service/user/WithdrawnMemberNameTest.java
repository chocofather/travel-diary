package com.example.travlediary.service.user;

import com.example.travlediary.config.i18n.SupportedLanguage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.i18n.LocaleContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 최종 탈퇴 회원의 작성자 표시 문구.
 * 판정은 users.status 로 하고, 문구는 messages 번들에서만 고른다.
 */
class WithdrawnMemberNameTest {

    private final WithdrawnMemberName withdrawnMemberName = TestWithdrawnMemberName.real();

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ko    | 탈퇴한 회원",
            "en    | Former member",
            "ja    | 退会した会員",
            "zh-CN | 已注销会员",
            "zh-TW | 已註銷會員"
    })
    void theLabelExistsInEverySupportedLanguage(String languageTag, String expected) {
        LocaleContextHolder.setLocale(java.util.Locale.forLanguageTag(languageTag));

        assertThat(withdrawnMemberName.current()).isEqualTo(expected);
        assertThat(withdrawnMemberName.orNickname("탈퇴b62167acec", true)).isEqualTo(expected);
    }

    /** 지원 언어 목록이 늘어나도 키가 빠지지 않도록 전부 확인한다. */
    @Test
    void everySupportedLanguageResolvesTheKey() {
        for (SupportedLanguage language : SupportedLanguage.all()) {
            LocaleContextHolder.setLocale(language.getLocale());
            assertThat(withdrawnMemberName.current())
                    .as("%s", language.getLanguageTag())
                    .isNotBlank();
        }
    }

    /** 탈퇴하지 않은 회원은 기존 닉네임 그대로다. */
    @Test
    void anOrdinaryMemberKeepsTheirNickname() {
        assertThat(withdrawnMemberName.orNickname("여행자민준", false)).isEqualTo("여행자민준");
    }

    /** 삭제된 댓글처럼 애초에 닉네임이 없는 경우는 그대로 둔다. */
    @Test
    void aMissingNicknameStaysNull() {
        assertThat(withdrawnMemberName.orNickname(null, true)).isNull();
        assertThat(withdrawnMemberName.orNickname(null, false)).isNull();
    }
}
