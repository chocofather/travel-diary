package com.example.travlediary.api;

import com.example.travlediary.service.file.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/upload")
public class EditorImageUploadApi {

    private final FileUploadService fileUploadService;

    @PostMapping("/editor-image")
    public ResponseEntity<Map<String, String>> uploadEditorImage(
            @RequestParam("image") MultipartFile imageFile) {
        // (image라는 name으로 POST)
        String url = fileUploadService.saveFile(imageFile, "editor");
        return ResponseEntity.ok(Map.of("url", url));
    }
}
