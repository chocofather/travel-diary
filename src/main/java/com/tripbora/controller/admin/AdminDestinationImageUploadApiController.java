package com.tripbora.controller.admin;

import com.tripbora.model.DestinationImage;
import com.tripbora.service.destination.DestinationImageService;
import com.tripbora.service.destination.DestinationImageUploadReceipts;
import com.tripbora.service.file.DestinationCardThumbnailService;
import com.tripbora.service.file.UnsupportedImageFormatException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;

/**
 * 여행지 이미지 관리 화면의 직접 업로드를 사진 한 장씩 받는다.
 *
 * <p>여러 장을 한 요청에 담으면 요청 전체 한도(spring.servlet.multipart.max-request-size)에 걸려
 * 몇 장만 넘어도 통째로 거절된다. 화면이 사진을 한 장씩 차례로 보내므로 한도는 사진 한 장
 * (max-file-size, 20MB)에만 적용되고 사진 수에는 제한이 없다.
 * 저장은 기존 직접 업로드와 같은 서비스(검증·파일 저장·출처 저장)를 쓴다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/api/destinations")
public class AdminDestinationImageUploadApiController {

    private final DestinationImageService destinationImageService;
    private final DestinationImageUploadReceipts uploadReceipts;
    private final DestinationCardThumbnailService cardThumbnailService;

    /** 저장은 이미 끝났다. 썸네일 준비가 실패해도 업로드 결과에는 영향을 주지 않는다. */
    private void prewarmThumbnails(Long destinationId) {
        try {
            cardThumbnailService.prewarm(destinationImageService.getImages(destinationId).stream()
                    .map(DestinationImage::getImageUrl).toList());
        } catch (RuntimeException failure) {
            log.warn("Thumbnail prewarm could not be queued after upload: destinationId={}, failureType={}",
                    destinationId, failure.getClass().getSimpleName());
        }
    }

    @PostMapping("/{id}/images")
    public ResponseEntity<Map<String, Object>> uploadOne(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "uploadKey", required = false) String uploadKey,
            @RequestParam(value = "sourceName", required = false) String sourceName,
            @RequestParam(value = "photographer", required = false) String photographer,
            @RequestParam(value = "licenseType", required = false) String licenseType,
            @RequestParam(value = "licenseDetail", required = false) String licenseDetail,
            @RequestParam(value = "sourceUrl", required = false) String sourceUrl,
            @RequestParam(value = "commonSourceUrl", required = false) String commonSourceUrl,
            @RequestParam(value = "workPageUrl", required = false) String workPageUrl) {
        // 저장을 마친 뒤 응답만 끊겨 다시 보낸 사진은 새로 저장하지 않는다.
        Optional<Long> alreadySaved = uploadReceipts.savedImageId(id, uploadKey);
        if (alreadySaved.isPresent()) {
            return ResponseEntity.ok(Map.of("imageId", alreadySaved.get(), "alreadySaved", true));
        }
        try {
            Long imageId = destinationImageService.saveUploadedImage(id, file,
                    sourceName, photographer, licenseType, licenseDetail, sourceUrl,
                    commonSourceUrl, workPageUrl);
            uploadReceipts.remember(id, uploadKey, imageId);
            // 관리용 썸네일은 뒤에서 미리 만든다. 줄에 넣기만 하고 바로 응답한다(만들기를 기다리지 않는다).
            prewarmThumbnails(id);
            return ResponseEntity.ok(Map.of("imageId", imageId, "alreadySaved", false));
        } catch (UnsupportedImageFormatException | DestinationImageService.InvalidSourceUrlException exception) {
            // 이 사진의 입력 문제. 이유를 그대로 보여 주고 다른 사진은 계속 올린다.
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (RuntimeException exception) {
            log.warn("Destination image upload failed: destinationId={}, bytes={}, failureType={}",
                    id, file.getSize(), exception.getClass().getSimpleName(), exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "사진을 저장하지 못했습니다. 잠시 후 이 사진만 다시 올려 주세요."));
        }
    }
}
