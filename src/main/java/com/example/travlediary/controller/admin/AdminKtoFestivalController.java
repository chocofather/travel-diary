package com.example.travlediary.controller.admin;

import com.example.travlediary.service.kto.KtoFestivalService;
import com.example.travlediary.service.kto.KtoTourApiException;
import com.example.travlediary.dto.kto.KtoFestivalThumbnailCandidatesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/admin/api/kto/festivals")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminKtoFestivalController {

    private static final int MAX_NUM_OF_ROWS = 30;

    private final KtoFestivalService ktoFestivalService;

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam(required = false) String eventStartDate,
                                    @RequestParam(required = false) String eventEndDate,
                                    @RequestParam(defaultValue = "1") int pageNo,
                                    @RequestParam(defaultValue = "10") int numOfRows) {
        LocalDate startDate = parseDate(eventStartDate);
        if (startDate == null) {
            return error(HttpStatus.BAD_REQUEST, "행사 시작일을 yyyy-MM-dd 형식으로 입력해 주세요.");
        }

        String normalizedEndDate = normalize(eventEndDate);
        LocalDate endDate = normalizedEndDate.isEmpty() ? null : parseDate(normalizedEndDate);
        if (!normalizedEndDate.isEmpty() && endDate == null) {
            return error(HttpStatus.BAD_REQUEST, "행사 종료일을 yyyy-MM-dd 형식으로 입력해 주세요.");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            return error(HttpStatus.BAD_REQUEST, "행사 종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (pageNo < 1) {
            return error(HttpStatus.BAD_REQUEST, "pageNo는 1 이상이어야 합니다.");
        }
        if (numOfRows < 1 || numOfRows > MAX_NUM_OF_ROWS) {
            return error(HttpStatus.BAD_REQUEST,
                    "numOfRows는 1에서 " + MAX_NUM_OF_ROWS + " 사이여야 합니다.");
        }

        try {
            return ResponseEntity.ok(ktoFestivalService.search(startDate, endDate, pageNo, numOfRows));
        } catch (KtoTourApiException exception) {
            return apiError(exception);
        }
    }

    @GetMapping("/detail")
    public ResponseEntity<?> detail(@RequestParam(required = false) String contentId) {
        String normalizedContentId = normalize(contentId);
        if (normalizedContentId.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "축제 정보 식별값이 올바르지 않습니다.");
        }
        try {
            return ResponseEntity.ok(ktoFestivalService.getDetail(normalizedContentId));
        } catch (KtoTourApiException exception) {
            return apiError(exception);
        }
    }

    @GetMapping("/images")
    public ResponseEntity<?> images(@RequestParam(required = false) String contentId) {
        String normalizedContentId = normalize(contentId);
        if (normalizedContentId.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "축제 정보 식별값이 올바르지 않습니다.");
        }
        try {
            return ResponseEntity.ok(new KtoFestivalThumbnailCandidatesResponse(
                    normalizedContentId, ktoFestivalService.getThumbnailCandidates(normalizedContentId)));
        } catch (KtoTourApiException exception) {
            return apiError(exception);
        }
    }

    @GetMapping("/search-by-keyword")
    public ResponseEntity<?> searchByKeyword(@RequestParam(required = false) String keyword,
                                             @RequestParam(defaultValue = "1") int pageNo,
                                             @RequestParam(defaultValue = "10") int numOfRows) {
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "축제·행사명을 입력해 주세요.");
        }
        if (pageNo < 1) {
            return error(HttpStatus.BAD_REQUEST, "pageNo는 1 이상이어야 합니다.");
        }
        if (numOfRows < 1 || numOfRows > MAX_NUM_OF_ROWS) {
            return error(HttpStatus.BAD_REQUEST,
                    "numOfRows는 1에서 " + MAX_NUM_OF_ROWS + " 사이여야 합니다.");
        }
        try {
            return ResponseEntity.ok(ktoFestivalService.searchByKeyword(normalizedKeyword, pageNo, numOfRows));
        } catch (KtoTourApiException exception) {
            return apiError(exception);
        }
    }

    private LocalDate parseDate(String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(normalizedValue);
        } catch (DateTimeParseException exception) {
            return null;
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
