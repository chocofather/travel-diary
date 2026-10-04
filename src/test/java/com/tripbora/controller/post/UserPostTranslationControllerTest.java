package com.tripbora.controller.post;

import com.tripbora.security.CustomUserDetails;
import com.tripbora.service.translation.TitleContentTranslationResponse;
import com.tripbora.service.translation.UserPostTranslationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPostTranslationControllerTest {

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void usesServerLocaleAndNeverAcceptsSourceTextOrTargetLanguage() {
        UserPostTranslationService service = mock(UserPostTranslationService.class);
        CustomUserDetails user = mock(CustomUserDetails.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(user.getId()).thenReturn(7L);
        when(request.getRemoteAddr()).thenReturn("203.0.113.8");
        TitleContentTranslationResponse translated = TitleContentTranslationResponse.ready(
                "翻訳タイトル", "<p>翻訳本文</p>", false);
        when(service.translate(9L, "ja", "203.0.113.8", 7L)).thenReturn(translated);
        LocaleContextHolder.setLocale(Locale.forLanguageTag("ja"));

        var response = new UserPostTranslationController(service)
                .translatePost(9L, user, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(translated);
        verify(service).translate(9L, "ja", "203.0.113.8", 7L);
    }
}
