    package com.example.travlediary.controller.admin;

    import com.example.travlediary.model.Event;
    import com.example.travlediary.security.CustomUserDetails;   // ★ 추가
    import com.example.travlediary.service.event.EventService;
    import com.example.travlediary.service.file.FileUploadService;
    import lombok.RequiredArgsConstructor;
    import org.springframework.security.core.Authentication;
    import org.springframework.stereotype.Controller;
    import org.springframework.ui.Model;
    import org.springframework.web.bind.annotation.*;
    import org.springframework.web.multipart.MultipartFile;
    import com.example.travlediary.service.event.EventService;


    import java.io.IOException;
    import java.util.List;

    @RequiredArgsConstructor
    @Controller
    @RequestMapping("/admin/event")
    public class AdminEventController {

        private final EventService     eventService;
        private final FileUploadService fileUploadService;

        /* ---------- 신규 ---------- */
        /** 이벤트 등록 폼 */
        @GetMapping("/new")
        public String newForm(Model model) {
            model.addAttribute("event", new Event());   // 빈 객체(폼 바인딩용)
            return "admin/event/event-form";                  // templates/admin/event-form.html
        }

        /* ---------- 기존 POST ---------- */
        @PostMapping
        public String create(@ModelAttribute Event event,
                             @RequestParam("imageFile") MultipartFile imageFile,
                             @RequestParam(value = "posterFile", required = false) MultipartFile posterFile,
                             Authentication auth) throws IOException {

            // 대표 이미지 저장
            String savedPath = fileUploadService.saveFile(imageFile, "events");
            event.setEventImg(savedPath);

            // 포스터 이미지 저장 (선택)
            if (posterFile != null && !posterFile.isEmpty()) {
                String posterPath = fileUploadService.saveFile(posterFile, "events/posters");
                event.setPosterImg(posterPath);  // Event 엔티티에 필드 있어야 함!
            }

            // 로그인 회원 PK
            CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();
            event.setUserId(user.getId());

            eventService.save(event);
            return "redirect:/admin/event/list";
        }



        /** ✅ 이벤트 삭제 */
        @PostMapping("/{id}/delete")
        public String delete(@PathVariable Long id) {
            eventService.deleteEventById(id);
            return "redirect:/admin"; // 이벤트 목록 or 관리자 메인 페이지
        }

        @GetMapping("/list")
        public String eventList(Model model) {
            List<Event> events = eventService.selectAllEvents();
            model.addAttribute("eventList", events);
            return "admin/event/event-list"; // templates/admin/event-list.html
        }


    }
