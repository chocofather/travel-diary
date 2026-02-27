package com.example.travlediary.controller.admin;

import com.example.travlediary.service.destination.DestinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/destinations")
@RequiredArgsConstructor
public class AdminDestinationViewController {

    private final DestinationService destinationService;

    @GetMapping("/{id}")
    public String viewDestination(@PathVariable Long id, Model model) {
        model.addAttribute("destination", destinationService.findById(id));
        return "admin/destination/view"; // 이 HTML 템플릿이 있어야 함
    }
}
