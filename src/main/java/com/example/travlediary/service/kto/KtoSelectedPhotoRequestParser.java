package com.example.travlediary.service.kto;

import com.example.travlediary.dto.kto.KtoSelectedPhotoRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class KtoSelectedPhotoRequestParser {

    private static final int MAX_SELECTED_PHOTOS = 30;

    private final ObjectReader reader;
    private final Validator validator;

    public KtoSelectedPhotoRequestParser(ObjectMapper objectMapper, Validator validator) {
        this.reader = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readerFor(new TypeReference<List<KtoSelectedPhotoRequest>>() {
                });
        this.validator = validator;
    }

    public List<KtoSelectedPhotoRequest> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            List<KtoSelectedPhotoRequest> photos = reader.readValue(json);
            validate(photos);
            return List.copyOf(photos);
        } catch (InvalidKtoSelectedPhotosException exception) {
            throw exception;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidKtoSelectedPhotosException(exception);
        }
    }

    private void validate(List<KtoSelectedPhotoRequest> photos) {
        if (photos == null || photos.size() > MAX_SELECTED_PHOTOS) {
            throw new InvalidKtoSelectedPhotosException();
        }

        int mainCount = 0;
        Set<List<String>> selectionKeys = new HashSet<>();
        for (KtoSelectedPhotoRequest photo : photos) {
            if (photo == null || !validator.validate(photo).isEmpty()) {
                throw new InvalidKtoSelectedPhotosException();
            }

            List<String> selectionKey = List.of(
                    photo.externalContentId().strip(),
                    photo.imageUrl().strip()
            );
            if (!selectionKeys.add(selectionKey)) {
                throw new InvalidKtoSelectedPhotosException();
            }

            if (photo.isMain() && ++mainCount > 1) {
                throw new InvalidKtoSelectedPhotosException();
            }
        }
    }
}
