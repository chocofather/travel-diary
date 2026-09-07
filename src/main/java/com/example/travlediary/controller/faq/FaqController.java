package com.example.travlediary.controller.faq;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.FaqListItemDto;
import com.example.travlediary.service.faq.FaqService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;
    private final MessageSource messageSource;

    @GetMapping("/support/faq")
    public String list(Model model) {
        List<FaqListItemDto> faqs = faqService.getPublicList();
        faqService.localizePublicList(faqs, requestedLanguage());
        model.addAttribute("faqs", faqs);
        model.addAttribute("pageTitle", message("support.faq.pageTitle"));
        return "support/faq";
    }

    /** 화면 문구는 다른 공개 화면과 같은 방식으로 메시지 번들에서 가져온다. */
    private String message(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    /** 다른 공개 화면과 같은 방식으로 요청 언어를 정한다. (쿠키 locale, 기본 한국어) */
    private SupportedLanguage requestedLanguage() {
        return SupportedLanguage.fromLocale(LocaleContextHolder.getLocale())
                .orElse(SupportedLanguage.KOREAN);
    }
}
