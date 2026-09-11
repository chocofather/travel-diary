package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.kto.KtoTourBulkImportRequest;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.kto.KtoTourApiException;
import com.example.travlediary.service.kto.KtoTourBulkImportService;
import com.example.travlediary.service.kto.KtoTourCandidateRegistrationFilter;
import com.example.travlediary.service.kto.KtoTourService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 국내 여행지 TourAPI 선택 일괄등록 API.
 * 후보 조회(GET)는 저장하지 않고, 관리자가 고른 항목만 등록(POST)한다.
 */
@RestController
@RequestMapping("/admin/api/kto/tour/bulk")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminKtoTourBulkImportController {

    private static final int MAX_NUM_OF_ROWS = 50;

    private final KtoTourService ktoTourService;
    private final KtoTourBulkImportService ktoTourBulkImportService;

    /** 법정동 기준 시/도 목록, regionCode 를 주면 그 시/도의 시/군/구 목록. */
    @GetMapping("/areas")
    public ResponseEntity<?> areas(@RequestParam(required = false) String regionCode) {
        try {
            return ResponseEntity.ok(ktoTourService.getLegalDongAreas(normalize(regionCode)));
        } catch (KtoTourApiException exception) {
            return apiError(exception);
        }
    }

    @GetMapping("/candidates")
    public ResponseEntity<?> candidates(@RequestParam(required = false) String regionCode,
                                        @RequestParam(required = false) String subRegionCode,
                                        @RequestParam(required = false) String contentTypeId,
                                        @RequestParam(required = false) String registrationFilter,
                                        @RequestParam(defaultValue = "1") int pageNo,
                                        @RequestParam(defaultValue = "20") int numOfRows) {
        String normalizedRegionCode = normalize(regionCode);
        if (normalizedRegionCode == null) {
            return error(HttpStatus.BAD_REQUEST, "지역을 선택해 주세요.");
        }
        if (pageNo < 1) {
            return error(HttpStatus.BAD_REQUEST, "pageNo는 1 이상이어야 합니다.");
        }
        if (numOfRows < 1 || numOfRows > MAX_NUM_OF_ROWS) {
            return error(HttpStatus.BAD_REQUEST,
                    "numOfRows는 1에서 " + MAX_NUM_OF_ROWS + " 사이여야 합니다.");
        }
        try {
            return ResponseEntity.ok(ktoTourBulkImportService.findCandidates(
                    normalizedRegionCode, normalize(subRegionCode), normalize(contentTypeId),
                    KtoTourCandidateRegistrationFilter.from(registrationFilter),
                    pageNo, numOfRows));
        } catch (KtoTourApiException exception) {
            return apiError(exception);
        }
    }

    @PostMapping("/import")
    public ResponseEntity<?> importSelected(
            @Valid @RequestBody KtoTourBulkImportRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return error(HttpStatus.UNAUTHORIZED, "로그인 정보를 확인할 수 없습니다.");
        }
        return ResponseEntity.ok(
                ktoTourBulkImportService.importSelected(request.items(), userDetails.getId()));
    }

    private ResponseEntity<ErrorResponse> apiError(KtoTourApiException exception) {
        HttpStatus status = exception.getKind() == KtoTourApiException.Kind.CONFIGURATION
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_GATEWAY;
        return error(status, exception.getMessage());
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }

    private record ErrorResponse(String message) {
    }
}
