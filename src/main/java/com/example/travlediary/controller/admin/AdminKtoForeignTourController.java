package com.example.travlediary.controller.admin;

import com.example.travlediary.service.kto.KtoForeignLanguage;
import com.example.travlediary.service.kto.KtoForeignTourApiException;
import com.example.travlediary.service.kto.KtoForeignTourService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 관리자 외국어 자동입력 API. 언어마다 경로를 따로 두지 않고 language 파라미터로 고른다.
 *
 * <p>화면 번역 탭이 쓰는 canonical 코드(en/ja/zh-CN/zh-TW)만 받는다.
 * 목록에 없는 값은 거부하며 국문이나 비슷한 언어로 대신하지 않는다.
 */
@RestController
@RequestMapping("/admin/api/kto/tour")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminKtoForeignTourController {

    private final KtoForeignTourService ktoForeignTourService;

    @GetMapping("/foreign-match")
    public ResponseEntity<?> match(@RequestParam(required = false) String language,
                                   @RequestParam(required = false) String title,
                                   @RequestParam(required = false) String mapX,
                                   @RequestParam(required = false) String mapY) {
        Optional<KtoForeignLanguage> foreignLanguage =
                KtoForeignLanguage.fromLanguageTag(normalize(language));
        if (foreignLanguage.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "지원하지 않는 언어입니다.");
        }
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
            return ResponseEntity.ok(ktoForeignTourService.match(
                    foreignLanguage.get(), koreanTitle, longitude, latitude));
        } catch (KtoForeignTourApiException exception) {
            return apiError(exception);
        }
    }

    /**
     * 외국어 상세. contentTypeId 는 매칭 결과로 받은 외국어 유형 코드이며,
     * 없으면 유형별 상세(detailIntro2) 없이 title/overview 만 돌려준다.
     */
    @GetMapping("/foreign-detail")
    public ResponseEntity<?> detail(@RequestParam(required = false) String language,
                                    @RequestParam(required = false) String contentId,
                                    @RequestParam(required = false) String contentTypeId) {
        Optional<KtoForeignLanguage> foreignLanguage =
                KtoForeignLanguage.fromLanguageTag(normalize(language));
        if (foreignLanguage.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "지원하지 않는 언어입니다.");
        }
        String normalizedContentId = normalize(contentId);
        if (normalizedContentId.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "외국어 관광정보 식별값이 올바르지 않습니다.");
        }
        try {
            return ResponseEntity.ok(ktoForeignTourService.getDetail(
                    foreignLanguage.get(), normalizedContentId, normalize(contentTypeId)));
        } catch (KtoForeignTourApiException exception) {
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

    private ResponseEntity<ErrorResponse> apiError(KtoForeignTourApiException exception) {
        HttpStatus status = exception.getKind() == KtoForeignTourApiException.Kind.CONFIGURATION
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
