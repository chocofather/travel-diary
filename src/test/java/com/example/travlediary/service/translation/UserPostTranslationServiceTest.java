package com.example.travlediary.service.translation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPostTranslationServiceTest {

    @Test
    void delegatesUserPostTitleAndContentToTheSharedCoordinator() {
        TitleContentTranslationService coordinator = mock(TitleContentTranslationService.class);
        TitleContentTranslationResponse translated = TitleContentTranslationResponse.ready(
                "번역 제목", "<p>번역 본문</p>", true);
        when(coordinator.translate(TranslatableContentType.USER_POST, 9L,
                "ko", "203.0.113.1", 7L)).thenReturn(translated);

        TitleContentTranslationResponse response = new UserPostTranslationService(coordinator)
                .translate(9L, "ko", "203.0.113.1", 7L);

        assertThat(response).isSameAs(translated);
        verify(coordinator).translate(TranslatableContentType.USER_POST, 9L,
                "ko", "203.0.113.1", 7L);
    }
}
