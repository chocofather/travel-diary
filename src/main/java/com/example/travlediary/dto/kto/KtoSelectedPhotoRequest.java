package com.example.travlediary.dto.kto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KtoSelectedPhotoRequest(
        @NotBlank @Size(max = 100) String externalContentId,
        @NotBlank @Size(max = 1000) String imageUrl,
        @Size(max = 255) String title,
        @Size(max = 100) String photographer,
        boolean isMain
) {
}
