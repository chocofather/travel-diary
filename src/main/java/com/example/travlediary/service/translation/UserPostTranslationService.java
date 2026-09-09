package com.example.travlediary.service.translation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserPostTranslationService {
    private final TitleContentTranslationService translationService;

    public TitleContentTranslationResponse translate(
            Long postId, String targetLanguage, String ipAddress, Long userId) {
        return translationService.translate(
                TranslatableContentType.USER_POST, postId,
                targetLanguage, ipAddress, userId);
    }
}
