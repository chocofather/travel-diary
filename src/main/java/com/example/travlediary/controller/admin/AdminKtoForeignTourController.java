package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.kto.KtoForeignTourMatchResponse;
import com.example.travlediary.service.kto.KtoFestivalCoordinates;
import com.example.travlediary.service.kto.KtoFestivalService;
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
    /** 축제 좌표 복구에만 쓴다. 국문 detailCommon2 호출을 새로 만들지 않고 그대로 빌려 쓴다. */
    private final KtoFestivalService ktoFestivalService;

    @GetMapping("/foreign-match")
    public ResponseEntity<?> match(@RequestParam(required = false) String language,
                                   @RequestParam(required = false) String title,
                                   @RequestParam(required = false) String mapX,
                                   @RequestParam(required = false) String mapY,
                                   @RequestParam(required = false) String festival,
                                   @RequestParam(required = false) String koreanContentId) {
        Optional<KtoForeignLanguage> foreignLanguage =
                KtoForeignLanguage.fromLanguageTag(normalize(language));
        if (foreignLanguage.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "지원하지 않는 언어입니다.");
        }
        String koreanTitle = normalize(title);
        if (koreanTitle.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "국문 여행지명이 올바르지 않습니다.");
        }
        // 축제·행사 화면만 festival=true 로 부른다. 여행지 화면은 예전 그대로 동작한다.
        boolean includeFestival = "true".equalsIgnoreCase(normalize(festival));
        Optional<KtoFestivalCoordinates> coordinates =
                resolveCoordinates(mapX, mapY, includeFestival, koreanContentId);
        if (coordinates.isEmpty()) {
            return includeFestival
                    // 좌표를 못 구한 축제(수기 등록 등)는 오류가 아니라 매칭 없음이다.
                    ? ResponseEntity.ok(KtoForeignTourMatchResponse.noMatch())
                    : error(HttpStatus.BAD_REQUEST, "좌표가 올바르지 않습니다.");
        }
        try {
            return ResponseEntity.ok(ktoForeignTourService.match(
                    foreignLanguage.get(), koreanTitle,
                    coordinates.get().mapX(), coordinates.get().mapY(), includeFestival));
        } catch (KtoForeignTourApiException exception) {
            return apiError(exception);
        }
    }

    /**
     * 매칭에 쓸 좌표를 정한다.
     *
     * <p>요청이 좌표를 직접 주면 그대로 쓴다. 축제·행사는 festival_info 에 좌표가 없어
     * 국문 KTO contentId 로 국문 detailCommon2 를 한 번 읽어 복구한다.
     * 이 contentId 는 좌표 복구에만 쓰며 외국어 상세 조회에 넘기지 않는다 —
     * 외국어 contentId 는 매칭 결과가 따로 알려 준다.
     */
    private Optional<KtoFestivalCoordinates> resolveCoordinates(String mapX, String mapY,
                                                                boolean includeFestival,
                                                                String koreanContentId) {
        String longitude = normalize(mapX);
        String latitude = normalize(mapY);
        if (isCoordinate(longitude, -180, 180) && isCoordinate(latitude, -90, 90)) {
            return Optional.of(new KtoFestivalCoordinates(longitude, latitude));
        }
        String sourceContentId = normalize(koreanContentId);
        if (!includeFestival || sourceContentId.isEmpty()) {
            return Optional.empty();
        }
        return ktoFestivalService.getCoordinates(sourceContentId)
                .filter(resolved -> isCoordinate(resolved.mapX(), -180, 180)
                        && isCoordinate(resolved.mapY(), -90, 90));
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
