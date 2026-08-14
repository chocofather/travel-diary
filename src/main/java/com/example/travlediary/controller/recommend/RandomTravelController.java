package com.example.travlediary.controller.recommend;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RandomTravelController {

    @GetMapping("/random-travel")
    public String randomTravel(Model model) {
        model.addAttribute("pageTitle", "랜덤 여행");
        return "random-travel";
    }
}
