package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.kto.KtoPhotoSearchResponse;
import com.example.travlediary.service.kto.KtoPhotoApiException;
import com.example.travlediary.service.kto.KtoPhotoGalleryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/kto/photos")
@RequiredArgsConstructor
public class AdminKtoPhotoController {

    private static final int MAX_NUM_OF_ROWS = 50;

    private final KtoPhotoGalleryService ktoPhotoGalleryService;

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam(required = false) String keyword,
                                    @RequestParam(defaultValue = "1") int pageNo,
                                    @RequestParam(defaultValue = "12") int numOfRows) {
        String normalizedKeyword = keyword == null ? "" : keyword.strip();
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
            return ResponseEntity.ok(ktoPhotoGalleryService.search(normalizedKeyword, pageNo, numOfRows));
        } catch (KtoPhotoApiException exception) {
            HttpStatus status = exception.getKind() == KtoPhotoApiException.Kind.CONFIGURATION
                    ? HttpStatus.SERVICE_UNAVAILABLE
                    : HttpStatus.BAD_GATEWAY;
            return error(status, exception.getMessage());
        }
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }

    private record ErrorResponse(String message) {
    }
}
