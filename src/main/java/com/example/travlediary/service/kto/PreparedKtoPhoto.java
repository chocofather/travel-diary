package com.example.travlediary.service.kto;

import java.sql.Timestamp;

public record PreparedKtoPhoto(
        String localImageUrl,
        String sourceImageUrl,
        String externalContentId,
        String title,
        String photographer,
        boolean isMain,
        Timestamp licenseCheckedAt
) {
}
