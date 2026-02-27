package com.example.travlediary.controller.course;

import com.example.travlediary.model.Course;
import com.example.travlediary.model.CourseDestination;
import com.example.travlediary.model.CourseImage;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.course.CourseService;
import com.example.travlediary.service.file.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/course")
public class CourseController {

    private final CourseService courseService;
    private final FileUploadService fileUploadService;

    // 글쓰기 폼 페이지 (GET)
    @GetMapping("/write")
    public String courseWritePage(Model model) {
        // 필요시 모델에 미리 채울 값 세팅
        return "course/write"; // templates/course/write.html
    }

    // 글쓰기 저장 (POST)
    @PostMapping("/write")
    public String submitCourse(
            @ModelAttribute Course course,
            @RequestParam(value = "images", required = false) List<MultipartFile> imageFiles,
            @RequestParam(value = "destinationIds", required = false) List<Long> destinationIds,
            @AuthenticationPrincipal CustomUserDetails principal   // 로그인한 사용자 정보
    ) {
        // --- 필수: 로그인 유저 ID 세팅 ---
        course.setUserId(principal.getId());

        // 1. 이미지 업로드(파일 → url 생성)
        List<CourseImage> imageList = new java.util.ArrayList<>();
        if (imageFiles != null) {
            for (MultipartFile file : imageFiles) {
                if (!file.isEmpty()) {
                    String imageUrl = fileUploadService.saveFile(file, "courses");
                    CourseImage img = new CourseImage();
                    img.setImageUrl(imageUrl);
                    imageList.add(img);
                }
            }
        }

        // 2. 여행지 리스트 매핑
        List<CourseDestination> destList = new java.util.ArrayList<>();
        if (destinationIds != null) {
            int order = 1;
            for (Long destId : destinationIds) {
                CourseDestination cd = new CourseDestination();
                cd.setDestinationId(destId);
                cd.setVisitOrder(order++);
                destList.add(cd);
            }
        }

        // 3. 서비스 호출
        courseService.createCourse(course, imageList, destList);

        // 4. 글 작성 완료 후 목록 페이지로 리다이렉트
        return "redirect:/board/list?boardType=course";
    }
}
