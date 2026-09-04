package com.example.travlediary.controller.admin;

import com.example.travlediary.service.kto.KtoEnglishTourApiException;
import com.example.travlediary.service.kto.KtoEnglishTourService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/kto/tour")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminKtoEnglishTourController {

    private final KtoEnglishTourService ktoEnglishTourService;

    @GetMapping("/english-match")
    public ResponseEntity<?> match(@RequestParam(required = false) String title,
                                   @RequestParam(required = false) String mapX,
                                   @RequestParam(required = false) String mapY) {
        String koreanTitle = normalize(title);
        String longitude = normalize(mapX);
        String latitude = normalize(mapY);
        if (koreanTitle.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "국문 여행지명이 올바르지 않습니다.");
        }
        if (!isCoordinate(longitude, -180, 180) || !isCoordinate(latitude, -90, 90)) {
            return error(HttpStatus.BAD_REQUEST, "좌표가 올바르지 않습니다.");
        }
        try {
            return ResponseEntity.ok(ktoEnglishTourService.match(koreanTitle, longitude, latitude));
        } catch (KtoEnglishTourApiException exception) {
            return apiError(exception);
        }
    }

    /**
     * 영문 상세. contentTypeId 는 매칭 결과로 받은 영문 유형 코드이며,
     * 없으면 유형별 상세(detailIntro2) 없이 title/overview 만 돌려준다.
     */
    @GetMapping("/english-detail")
    public ResponseEntity<?> detail(@RequestParam(required = false) String contentId,
                                    @RequestParam(required = false) String contentTypeId) {
        String normalizedContentId = normalize(contentId);
        if (normalizedContentId.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "영문 관광정보 식별값이 올바르지 않습니다.");
        }
        try {
            return ResponseEntity.ok(ktoEnglishTourService.getDetail(
                    normalizedContentId, normalize(contentTypeId)));
        } catch (KtoEnglishTourApiException exception) {
            return apiError(exception);
        }
    }

    private boolean isCoordinate(String value, double minimum, double maximum) {
        if (value.isEmpty()) {
            return false;
        }
        try {
            double coordinate = Double.parseDouble(value);
            return Double.isFinite(coordinate) && coordinate >= minimum && coordinate <= maximum;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private ResponseEntity<ErrorResponse> apiError(KtoEnglishTourApiException exception) {
        HttpStatus status = exception.getKind() == KtoEnglishTourApiException.Kind.CONFIGURATION
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_GATEWAY;
        return error(status, exception.getMessage());
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }

    private record ErrorResponse(String message) {
    }
}
