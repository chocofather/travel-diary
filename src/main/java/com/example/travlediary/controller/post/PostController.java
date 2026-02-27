package com.example.travlediary.controller.post;

import com.example.travlediary.model.PostImage;
import com.example.travlediary.model.UserPost;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.post.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/post")
public class PostController {

    private final PostService postService;
    private final FileUploadService fileUploadService;

    // 글쓰기 폼 페이지 (GET)
    @GetMapping("/write")
    public String postWritePage(Model model) {
        // 필요시 model.addAttribute로 카테고리 등 전달
        return "post/write"; // templates/post/write.html
    }

    // 글쓰기 저장 (POST)
    @PostMapping("/write")
    public String submitPost(
            @ModelAttribute UserPost post,
            @RequestParam(value = "images", required = false) List<MultipartFile> imageFiles,
            @AuthenticationPrincipal CustomUserDetails loginUser
    ) {
        post.setUserId(loginUser.getId());

        // 1. 이미지 파일 저장
        List<PostImage> imageList = new ArrayList<>();
        if (imageFiles != null && !imageFiles.isEmpty()) {
            for (MultipartFile file : imageFiles) {
                if (file != null && !file.isEmpty()) {
                    String imageUrl = fileUploadService.saveFile(file, "posts");
                    PostImage img = new PostImage();
                    img.setImageUrl(imageUrl);
                    imageList.add(img);
                }
            }
        }

        // 2. 서비스 호출 (글+이미지 등록)
        postService.createPost(post, imageList);

        // 3. 작성 후 목록/상세 페이지로 리다이렉트
        return "redirect:/board/list?boardType=post";
    }

}
