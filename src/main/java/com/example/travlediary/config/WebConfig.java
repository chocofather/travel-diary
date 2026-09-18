package com.example.travlediary.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 주소만 알면 누구나 열어도 되는 업로드 폴더. 여기 적힌 폴더만 정적으로 열린다.
     *
     * <p>예전에는 {@code /uploads/**} 를 통째로 매핑해서 개인 다이어리 사진까지 함께 열렸다.
     * 개인 사진(diary-covers / diary-pages / diary-cover-elements / diary-cover-designs)은
     * 이 목록에 넣지 않는다 — 소유권과 PIN 을 확인하는 통제된 endpoint 로만 나간다.
     *
     * <p>폴더를 새로 늘릴 때는 그 안의 파일이 정말 공개되어도 되는지 먼저 확인한다.
     */
    static final List<String> PUBLIC_UPLOAD_DIRECTORIES = List.of(
            "events",          // 축제·행사 이미지와 포스터(events/posters)
            "destinations",    // 여행지 이미지
            "travel-info",     // 여행정보 썸네일
            "posts",           // 공개 게시글 이미지
            "editor",          // 리치 텍스트 에디터 이미지
            "comments",        // 여행지 댓글 이미지
            "post-comments",   // 게시글 댓글 이미지
            "course-comments", // 여행 코스 댓글 이미지
            "profiles",        // 프로필 이미지 (공개 프로필이 비로그인 공개다)
            "diary-stickers",  // 관리자 스티커 카탈로그 (공용 asset)
            "icons");          // 사이트 아이콘과 편의시설 아이콘(icons/amenities)

    @Value("${custom.upload-path}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadRoot = Paths.get(uploadPath).toAbsolutePath().normalize();
        for (String directory : PUBLIC_UPLOAD_DIRECTORIES) {
            registry.addResourceHandler("/uploads/" + directory + "/**")
                    .addResourceLocations("file:" + uploadRoot.resolve(directory) + "/")
                    .setCachePeriod(3600);
        }
    }
}
