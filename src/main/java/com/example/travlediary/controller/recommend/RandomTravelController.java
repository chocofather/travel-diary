package com.example.travlediary.controller.recommend;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Locale;

@Controller
public class RandomTravelController {

    private final MessageSource messageSource;

    public RandomTravelController(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @GetMapping("/random-travel")
    public String randomTravel(Model model, Locale locale) {
        model.addAttribute("pageTitle",
                messageSource.getMessage("random.travel.pageTitle", null, locale));
        return "random-travel";
    }
}
