package com.example.travlediary.controller.faq;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.FaqCategoryFilterDto;
import com.example.travlediary.dto.FaqListItemDto;
import com.example.travlediary.service.faq.FaqService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;
    private final MessageSource messageSource;

    /**
     * 공개 FAQ 목록. FAQ 는 페이징 없이 공개 질문 전체를 한 번에 읽으므로
     * 카테고리 필터도 그 전체 목록에서 고른다. (관리자 노출 순서 유지)
     */
    @GetMapping("/support/faq")
    public String list(@RequestParam(value = "category", required = false) String category,
                       Model model) {
        SupportedLanguage language = requestedLanguage();
        List<FaqListItemDto> faqs = faqService.getPublicList();
        faqService.localizePublicList(faqs, language);
        List<FaqCategoryFilterDto> categories = faqService.getPublicCategoryFilters(faqs, language);
        Long selectedCategoryId = selectedCategoryId(category, categories);

        model.addAttribute("faqs", selectedCategoryId == null
                ? faqs
                : faqs.stream().filter(faq -> selectedCategoryId.equals(faq.getCategoryId())).toList());
        model.addAttribute("hasPublicFaqs", !faqs.isEmpty());
        model.addAttribute("faqCategories", categories);
        model.addAttribute("selectedCategoryId", selectedCategoryId);
        model.addAttribute("pageTitle", message("support.faq.pageTitle"));
        return "support/faq";
    }

    /** 없는 번호나 숫자가 아닌 값은 오류 대신 '전체'로 본다. */
    private Long selectedCategoryId(String category, List<FaqCategoryFilterDto> categories) {
        if (category == null || category.isBlank()) {
            return null;
        }
        try {
            Long id = Long.valueOf(category.trim());
            return categories.stream().anyMatch(filter -> id.equals(filter.id())) ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
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
