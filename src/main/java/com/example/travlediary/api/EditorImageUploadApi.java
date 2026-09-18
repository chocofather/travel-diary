package com.example.travlediary.api;

import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.file.UnsupportedImageFormatException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 리치 텍스트 에디터 이미지 업로드. 로그인 사용자만 쓸 수 있다. (SecurityConfig 의 /api/upload/**)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/upload")
public class EditorImageUploadApi {

    private final FileUploadService fileUploadService;

    @PostMapping("/editor-image")
    public ResponseEntity<Map<String, String>> uploadEditorImage(
            @RequestParam("image") MultipartFile imageFile) {
        // (image라는 name으로 POST)
        String url;
        try {
            // 실제 JPEG/PNG/WEBP 인지는 저장 서비스가 확인한다.
            url = fileUploadService.saveFile(imageFile, "editor");
        } catch (UnsupportedImageFormatException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        }
        if (url == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", FileUploadService.UNSUPPORTED_IMAGE_MESSAGE));
        }
        return ResponseEntity.ok(Map.of("url", url));
    }
}
