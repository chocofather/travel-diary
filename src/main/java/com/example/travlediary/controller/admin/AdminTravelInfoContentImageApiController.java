package com.example.travlediary.controller.admin;

import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.file.UnsupportedImageFormatException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 여행정보 구조화 콘텐츠(STRUCTURED) 블록에 넣을 본문 이미지를 한 장씩 받는다.
 *
 * <p>로그인 사용자용 {@code /api/upload/editor-image}(Quill)와 따로 둔다. 관리자만 쓰고({@code /admin/**}),
 * CSRF 도 기존 정책 그대로다. 응답의 url / width / height 를 블록 JSON 의 image 에 그대로 넣으면 된다.
 * 크기는 서버가 저장한 그림에서 읽은 값이라 화면이 보낼 필요가 없다.
 *
 * <p>여기서는 파일만 저장하고 DB 에는 아무것도 쓰지 않는다. 여행정보를 저장할 때 블록이 이 url 을 가리키게 된다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/api/travel-info")
public class AdminTravelInfoContentImageApiController {

    private final FileUploadService fileUploadService;

    @PostMapping("/content-images")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam(value = "image", required = false) MultipartFile image) {
        try {
            FileUploadService.StoredContentImage stored = fileUploadService.saveTravelInfoContentImage(image);
            return ResponseEntity.ok(Map.of(
                    "url", stored.url(),
                    "width", stored.width(),
                    "height", stored.height()));
        } catch (UnsupportedImageFormatException exception) {
            // 이 이미지의 입력 문제. 이유를 그대로 보여 준다.
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (RuntimeException exception) {
            log.warn("Travel info content image upload failed: bytes={}, failureType={}",
                    image == null ? 0 : image.getSize(), exception.getClass().getSimpleName(), exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "이미지를 저장하지 못했습니다. 잠시 후 다시 올려 주세요."));
        }
    }
}
