package com.example.travlediary.controller.admin;

import com.example.travlediary.service.kto.KtoTourApiException;
import com.example.travlediary.dto.kto.KtoTourRegionMatchResponse;
import com.example.travlediary.service.kto.KtoTourRegionMatchService;
import com.example.travlediary.service.kto.KtoTourService;
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
public class AdminKtoTourController {

    private static final int MAX_NUM_OF_ROWS = 30;

    private final KtoTourService ktoTourService;
    private final KtoTourRegionMatchService ktoTourRegionMatchService;

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam(required = false) String keyword,
                                    @RequestParam(defaultValue = "1") int pageNo,
                                    @RequestParam(defaultValue = "10") int numOfRows) {
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "검색어를 입력해 주세요.");
        }
        if (pageNo < 1) {
            return error(HttpStatus.BAD_REQUEST, "pageNo는 1 이상이어야 합니다.");
        }
        if (numOfRows < 1 || numOfRows > MAX_NUM_OF_ROWS) {
            return error(HttpStatus.BAD_REQUEST,
                    "numOfRows는 1에서 " + MAX_NUM_OF_ROWS + " 사이여야 합니다.");
        }
        try {
            return ResponseEntity.ok(ktoTourService.search(normalizedKeyword, pageNo, numOfRows));
        } catch (KtoTourApiException exception) {
            return apiError(exception);
        }
    }

    @GetMapping("/detail")
    public ResponseEntity<?> detail(@RequestParam(required = false) String contentId,
                                    @RequestParam(required = false) String contentTypeId) {
        String normalizedContentId = normalize(contentId);
        String normalizedContentTypeId = normalize(contentTypeId);
        if (normalizedContentId.isEmpty() || normalizedContentTypeId.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "관광정보 식별값이 올바르지 않습니다.");
        }
        try {
            var detail = ktoTourService.getDetail(normalizedContentId, normalizedContentTypeId);
            return ResponseEntity.ok(detail.withRegionMatch(matchRegion(detail.address())));
        } catch (KtoTourApiException exception) {
            return apiError(exception);
        }
    }

    private KtoTourRegionMatchResponse matchRegion(String address) {
        try {
            return ktoTourRegionMatchService.match(address);
        } catch (RuntimeException exception) {
            return KtoTourRegionMatchResponse.unmatched();
        }
    }

    private ResponseEntity<ErrorResponse> apiError(KtoTourApiException exception) {
        HttpStatus status = exception.getKind() == KtoTourApiException.Kind.CONFIGURATION
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
