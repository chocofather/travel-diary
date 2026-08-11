package com.example.travlediary.controller.faq;

import com.example.travlediary.service.faq.FaqService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    @GetMapping("/support/faq")
    public String list(Model model) {
        model.addAttribute("faqs", faqService.getPublicList());
        model.addAttribute("pageTitle", "자주 묻는 질문 | 고객센터");
        return "support/faq";
    }
}
