package com.example.travlediary.dto.kto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 화면에서 선택한 관광사진. 출처와 라이선스는 받지 않고 서버가 이미지 주소와 출처별 근거로 다시 정한다.
 */
public record KtoSelectedPhotoRequest(
        @NotBlank @Size(max = 100) String externalContentId,
        @NotBlank @Size(max = 1000) String imageUrl,
        @Size(max = 255) String title,
        @Size(max = 100) String photographer,
        boolean isMain
) {
}
