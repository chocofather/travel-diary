package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.CategoryForm;
import com.example.travlediary.service.category.CategoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/categories")
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

   // 카테고리 리스트
    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", categoryService.getAll()); // 전체 카테고리 조회
        return "admin/categories/list";
    }

    // 등록폼
    @GetMapping("/create")
    public String showForm(Model model) {
        model.addAttribute("categoryForm", new CategoryForm());
        return "admin/categories/create";
    }

    // 등록 처리
    @PostMapping
    public String create(@ModelAttribute CategoryForm form) {
        categoryService.createCategory(form);
        return "redirect:/admin/categories";
    }

    // 삭제 처리
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return "redirect:/admin/categories";
    }
}
