package com.example.travlediary.service.translation;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class TranslationSourceRegistry {
    private final Map<TranslatableContentType, TranslationSourceReader> readers;

    public TranslationSourceRegistry(List<TranslationSourceReader> readers) {
        Map<TranslatableContentType, TranslationSourceReader> byType =
                new EnumMap<>(TranslatableContentType.class);
        for (TranslationSourceReader reader : readers) {
            if (byType.put(reader.contentType(), reader) != null) {
                throw new IllegalStateException("번역 원문 reader가 중복 등록되었습니다: " + reader.contentType());
            }
        }
        this.readers = Map.copyOf(byType);
    }

    public TranslationSourceReader get(TranslatableContentType type) {
        TranslationSourceReader reader = readers.get(type);
        if (reader == null) {
            throw new IllegalArgumentException("지원하지 않는 번역 콘텐츠 유형입니다.");
        }
        return reader;
    }
}
