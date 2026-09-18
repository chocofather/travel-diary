package com.example.travlediary.controller.diary;

import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.diary.DiaryPrivatePhotoService;
import com.example.travlediary.service.file.DiaryPrivatePhotoStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;

/**
 * 회원 개인 다이어리 사진을 내보내는 유일한 문.
 *
 * <p>예전에는 이 사진들이 {@code /uploads/**} 정적 매핑 아래에 있어서 주소만 알면 로그인도
 * PIN 도 없이 열렸다. 이제 실제 파일은 공개 업로드 폴더 밖에 있고, 여기를 지나야만 나간다.
 *
 * <p>요청에는 <b>DB 번호만</b> 담긴다. 파일 이름이나 저장 키를 파라미터로 받지 않으므로
 * 경로를 지어내 다른 파일을 꺼낼 길이 없다. 실제 경로는 소유권을 확인한 뒤 DB 에서 나온다.
 *
 * <p>표지 라이브러리 공유 사진은 이 문을 쓰지 않는다. 그쪽은 여러 회원이 함께 보는 자산이라
 * {@code /diaries/cover-library/assets/{assetId}} 를 그대로 쓴다.
 */
@Controller
@RequestMapping("/diaries")
@RequiredArgsConstructor
public class DiaryPrivatePhotoController {

    /** 개인 사진이라 공유 캐시에 남기지 않는다. 브라우저 안에서만 잠깐 재사용한다. */
    private static final CacheControl PRIVATE_CACHE = CacheControl
            .maxAge(Duration.ofSeconds(60))
            .mustRevalidate()
            .cachePrivate();

    private final DiaryPrivatePhotoService privatePhotoService;

    /** 다이어리 대표 이미지 */
    @GetMapping("/{diaryId:\\d+}/cover-image")
    @ResponseBody
    public ResponseEntity<Resource> coverImage(
            @PathVariable Long diaryId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return photoResponse(privatePhotoService.getCoverImage(diaryId, userId(userDetails)));
    }

    /** 페이지에 붙인 사진 */
    @GetMapping("/{diaryId:\\d+}/pages/{pageId:\\d+}/elements/{elementId:\\d+}/photo")
    @ResponseBody
    public ResponseEntity<Resource> pageElementPhoto(
            @PathVariable Long diaryId,
            @PathVariable Long pageId,
            @PathVariable Long elementId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return photoResponse(privatePhotoService
                .getPageElementPhoto(diaryId, pageId, elementId, userId(userDetails)));
    }

    /** 다이어리에 실제로 적용된 표지의 사진 */
    @GetMapping("/{diaryId:\\d+}/cover/elements/{elementId:\\d+}/photo")
    @ResponseBody
    public ResponseEntity<Resource> coverElementPhoto(
            @PathVariable Long diaryId,
            @PathVariable Long elementId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return photoResponse(privatePhotoService
                .getCoverElementPhoto(diaryId, elementId, userId(userDetails)));
    }

    /** 보관함 "내 표지 디자인"의 사진 */
    @GetMapping("/cover-designs/{designId:\\d+}/elements/{elementId:\\d+}/photo")
    @ResponseBody
    public ResponseEntity<Resource> coverDesignElementPhoto(
            @PathVariable Long designId,
            @PathVariable Long elementId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return photoResponse(privatePhotoService
                .getCoverDesignElementPhoto(designId, elementId, userId(userDetails)));
    }

    /**
     * 사진 한 장의 응답. 표지 라이브러리 asset 응답과 같은 머리말을 쓴다.
     *
     * <p>Content-Type 은 DB 값이 아니라 저장소가 파일 앞머리로 판별한 형식에서 나오고,
     * nosniff 를 함께 붙여 브라우저가 다른 형식으로 해석하지 않게 한다.
     * 파일 이름은 내보내지 않는다 — 화면에 그대로 그려질 사진이라 inline 이면 충분하다.
     */
    private ResponseEntity<Resource> photoResponse(DiaryPrivatePhotoStorage.StoredPhotoFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.contentLength())
                .cacheControl(PRIVATE_CACHE)
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", ContentDisposition.inline().build().toString())
                .body(new FileSystemResource(file.path()));
    }

    /** 현재 사용자. 인증은 SecurityConfig 가 이미 요구하지만 값이 없으면 서비스가 막는다. */
    private Long userId(CustomUserDetails userDetails) {
        return userDetails == null ? null : userDetails.getId();
    }
}
