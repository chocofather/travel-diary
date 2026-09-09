package com.example.travlediary.service.translation;

import com.google.cloud.translate.v3.LocationName;
import com.google.cloud.translate.v3.TranslateTextRequest;
import com.google.cloud.translate.v3.TranslateTextResponse;
import com.google.cloud.translate.v3.Translation;
import com.google.cloud.translate.v3.TranslationServiceClient;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class GoogleCloudMachineTranslationClient implements MachineTranslationClient {
    private final boolean enabled;
    private final String projectId;
    private final String location;
    private volatile TranslationServiceClient client;

    public GoogleCloudMachineTranslationClient(
            @Value("${translation.google.enabled:false}") boolean enabled,
            @Value("${translation.google.project-id:}") String projectId,
            @Value("${translation.google.location:global}") String location) {
        this.enabled = enabled;
        this.projectId = projectId;
        this.location = location;
    }

    @Override
    public void prepare() {
        if (!enabled || projectId == null || projectId.isBlank()) {
            throw new MachineTranslationException("번역 provider가 설정되지 않았습니다.");
        }
        client();
    }

    @Override
    public MachineTranslation translate(String sourceText, String sourceLanguage, String targetLanguage) {
        return translate(sourceText, sourceLanguage, targetLanguage, "text/plain");
    }

    @Override
    public MachineTranslation translate(
            String sourceText, String sourceLanguage, String targetLanguage, String mimeType) {
        prepare();
        try {
            TranslateTextRequest.Builder request = TranslateTextRequest.newBuilder()
                    .setParent(LocationName.of(projectId, location).toString())
                    .setMimeType("text/html".equals(mimeType) ? "text/html" : "text/plain")
                    .setTargetLanguageCode(targetLanguage)
                    .addContents(sourceText);
            if (sourceLanguage != null && !sourceLanguage.isBlank() && !"und".equals(sourceLanguage)) {
                request.setSourceLanguageCode(sourceLanguage);
            }
            TranslateTextResponse response = client().translateText(request.build());
            if (response.getTranslationsCount() == 0) {
                throw new MachineTranslationException("번역 provider가 빈 결과를 반환했습니다.");
            }
            Translation translation = response.getTranslations(0);
            return new MachineTranslation(
                    translation.getTranslatedText(),
                    translation.getDetectedLanguageCode().isBlank()
                            ? null : translation.getDetectedLanguageCode());
        } catch (MachineTranslationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MachineTranslationException("번역 provider 요청에 실패했습니다.", e);
        }
    }

    private TranslationServiceClient client() {
        TranslationServiceClient current = client;
        if (current != null) return current;
        synchronized (this) {
            if (client == null) {
                try {
                    client = TranslationServiceClient.create();
                } catch (IOException e) {
                    throw new MachineTranslationException("번역 provider 초기화에 실패했습니다.", e);
                }
            }
            return client;
        }
    }

    @PreDestroy
    void close() {
        TranslationServiceClient current = client;
        if (current != null) current.close();
    }
}
