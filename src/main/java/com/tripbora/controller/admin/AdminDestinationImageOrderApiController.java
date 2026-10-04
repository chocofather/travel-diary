package com.tripbora.controller.admin;

import com.tripbora.service.destination.DestinationImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 여행지 이미지 관리 화면의 '순서 편집' 저장.
 *
 * <p>화면에서 끌어 옮기거나 위치를 골라 바꾼 순서를 '순서 저장'을 누를 때 한 번에 받는다.
 * 대표 이미지·슬라이드 지정은 바꾸지 않는다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/api/destinations")
public class AdminDestinationImageOrderApiController {

    private final DestinationImageService destinationImageService;

    /** @param request {@code {"imageIds": [3, 1, 2]}} — 이 여행지의 사진 전부를 원하는 순서대로 */
    @PostMapping("/{id}/images/order")
    public ResponseEntity<Map<String, Object>> saveOrder(@PathVariable Long id,
                                                         @RequestBody(required = false) OrderRequest request) {
        List<Long> imageIds = request == null ? null : request.imageIds();
        try {
            destinationImageService.saveImageOrder(id, imageIds);
            return ResponseEntity.ok(Map.of("saved", imageIds.size()));
        } catch (DestinationImageService.InvalidImageOrderException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (RuntimeException exception) {
            log.warn("Destination image order could not be saved: destinationId={}, images={}, failureType={}",
                    id, imageIds == null ? 0 : imageIds.size(), exception.getClass().getSimpleName(), exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "순서를 저장하지 못했습니다. 잠시 후 다시 저장해 주세요."));
        }
    }

    public record OrderRequest(List<Long> imageIds) {
    }
}
