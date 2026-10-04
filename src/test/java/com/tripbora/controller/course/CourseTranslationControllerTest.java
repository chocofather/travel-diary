package com.tripbora.controller.course;

import com.tripbora.security.CustomUserDetails;
import com.tripbora.service.translation.TitleContentTranslationResponse;
import com.tripbora.service.translation.TitleContentTranslationService;
import com.tripbora.service.translation.TranslatableContentType;
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

class CourseTranslationControllerTest {

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void usesCourseIdAndServerLocaleWithoutAcceptingSourceText() {
        TitleContentTranslationService service = mock(TitleContentTranslationService.class);
        CustomUserDetails user = mock(CustomUserDetails.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(user.getId()).thenReturn(7L);
        when(request.getRemoteAddr()).thenReturn("203.0.113.8");
        TitleContentTranslationResponse translated = TitleContentTranslationResponse.ready(
                "翻訳コース", "<p>翻訳説明</p>", false);
        when(service.translate(TranslatableContentType.COURSE, 9L,
                "ja", "203.0.113.8", 7L)).thenReturn(translated);
        LocaleContextHolder.setLocale(Locale.forLanguageTag("ja"));

        var response = new CourseTranslationController(service)
                .translateCourse(9L, user, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(translated);
        verify(service).translate(TranslatableContentType.COURSE, 9L,
                "ja", "203.0.113.8", 7L);
    }
}
