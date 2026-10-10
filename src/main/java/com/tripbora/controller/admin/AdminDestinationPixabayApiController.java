package com.tripbora.controller.admin;

import com.tripbora.service.destination.DestinationPixabayImageManagementService;
import com.tripbora.service.pixabay.PixabayApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행지 이미지 관리 화면의 Pixabay 검색 전용 API. 브라우저는 Pixabay를 직접 부르지 않고 이 API만 부른다.
 * 저장은 이미지 관리 화면의 추가 폼(사진 ID만 전송)으로 한다.
 */
@RestController
@RequestMapping("/admin/api/destinations/{id}/pixabay")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDestinationPixabayApiController {

    private final DestinationPixabayImageManagementService pixabayImageManagementService;

    /** offset 은 이미 화면에 보인 사진 수. 0이면 처음 30장, 그 밖에는 이어서 20장. */
    @GetMapping("/photos")
    public ResponseEntity<?> photos(@PathVariable Long id,
                                    @RequestParam(required = false) String query,
                                    @RequestParam(defaultValue = "0") int offset) {
        try {
            return ResponseEntity.ok(pixabayImageManagementService.search(id, query, offset));
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
        } catch (PixabayApiException exception) {
            HttpStatus status = switch (exception.reason()) {
                case NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
                case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
                case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
                case UPSTREAM -> HttpStatus.BAD_GATEWAY;
            };
            return error(status, exception.reason().name(), exception.getMessage());
        }
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String reason, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(reason, message));
    }

    private record ErrorResponse(String reason, String message) {
    }
}
