package com.example.travlediary.controller.destination;

import com.example.travlediary.service.file.DestinationCardThumbnailService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * 메인 여행지 카드 썸네일. 주소 규칙은 {@code DestinationCardThumbnails} 가 정한다.
 *
 * <p>주소에 규칙 버전과 원본 파일 이름(업로드마다 새로 붙는 이름)이 들어 있어 같은 주소의 내용이 바뀌지 않는다.
 * 그래서 원본 업로드 파일(1시간)보다 길게 브라우저가 들고 있게 한다.
 */
@Controller
@RequiredArgsConstructor
public class DestinationCardThumbnailController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofDays(30)).cachePublic();

    private final DestinationCardThumbnailService thumbnailService;

    @GetMapping("/thumbnails/destinations/{version}/{width}/{fileName:.+}")
    public ResponseEntity<Resource> thumbnail(@PathVariable String version,
                                              @PathVariable int width,
                                              @PathVariable String fileName) throws IOException {
        Optional<Path> file = thumbnailService.resolve(version, width, fileName);
        if (file.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Path path = file.get();
        MediaType type = MediaTypeFactory.getMediaType(path.getFileName().toString())
                .orElse(MediaType.IMAGE_JPEG);
        // Last-Modified 가 있으면 다시 묻는 요청에 304 로 답한다.
        // 썸네일 대신 원본을 보낸 경우(만들기 실패·시간 초과)는 오래 들고 있지 않게 해 다음에 썸네일을 받게 한다.
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(thumbnailService.isThumbnailFile(path) ? CACHE : CacheControl.noCache())
                .lastModified(Files.getLastModifiedTime(path).toMillis())
                .body(new FileSystemResource(path));
    }
}
