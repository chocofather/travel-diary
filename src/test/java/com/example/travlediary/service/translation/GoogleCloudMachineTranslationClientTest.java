package com.example.travlediary.service.translation;

import com.google.cloud.translate.v3.TranslateTextRequest;
import com.google.cloud.translate.v3.TranslateTextResponse;
import com.google.cloud.translate.v3.Translation;
import com.google.cloud.translate.v3.TranslationServiceClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleCloudMachineTranslationClientTest {

    @Test
    void forwardsProviderDetectedSourceLanguageWhenGoogleReturnsIt() {
        TranslationServiceClient translationService = mock(TranslationServiceClient.class);
        when(translationService.translateText(any(TranslateTextRequest.class))).thenReturn(response("en"));
        GoogleCloudMachineTranslationClient client = clientWith(translationService);

        MachineTranslation result = client.translate("Hello", "und", "ko");

        assertThat(result.translatedText()).isEqualTo("안녕하세요");
        assertThat(result.detectedSourceLanguage()).isEqualTo("en");
    }

    @Test
    void keepsProviderDetectedSourceLanguageNullWhenGoogleDidNotDetectIt() {
        TranslationServiceClient translationService = mock(TranslationServiceClient.class);
        when(translationService.translateText(any(TranslateTextRequest.class))).thenReturn(response(""));
        GoogleCloudMachineTranslationClient client = clientWith(translationService);

        MachineTranslation result = client.translate("Hello", "en", "ko");

        assertThat(result.detectedSourceLanguage()).isNull();
        ArgumentCaptor<TranslateTextRequest> request = ArgumentCaptor.forClass(TranslateTextRequest.class);
        verify(translationService).translateText(request.capture());
        assertThat(request.getValue().getSourceLanguageCode()).isEqualTo("en");
    }

    private GoogleCloudMachineTranslationClient clientWith(TranslationServiceClient translationService) {
        GoogleCloudMachineTranslationClient client = new GoogleCloudMachineTranslationClient(true, "project-id", "global");
        ReflectionTestUtils.setField(client, "client", translationService);
        return client;
    }

    private TranslateTextResponse response(String detectedLanguageCode) {
        Translation translation = Translation.newBuilder()
                .setTranslatedText("안녕하세요")
                .setDetectedLanguageCode(detectedLanguageCode)
                .build();
        return TranslateTextResponse.newBuilder().addTranslations(translation).build();
    }
}
