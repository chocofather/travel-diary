package com.example.travlediary.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    // 검색 결과 페이지
    @GetMapping("/search")
    public String searchPage() {
        return "search"; // => templates/search.html 렌더링
    }
}
