package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.DiaryStickerCategoryForm;
import com.example.travlediary.dto.DiaryStickerFilter;
import com.example.travlediary.dto.DiaryStickerForm;
import com.example.travlediary.model.DiaryStickerAccessTier;
import com.example.travlediary.model.DiaryStickerType;
import com.example.travlediary.service.diary.DiaryStickerAdminService;
import com.example.travlediary.service.diary.DiaryStickerValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/diary/stickers")
@RequiredArgsConstructor
public class AdminDiaryStickerController {
    private static final String LIST = "admin/diary-stickers/list";
    private static final String FORM = "admin/diary-stickers/form";
    private static final String CATEGORY_LIST = "admin/diary-stickers/category-list";
    private static final String CATEGORY_FORM = "admin/diary-stickers/category-form";
    private static final String REDIRECT_LIST = "redirect:/admin/diary/stickers";
    private static final String REDIRECT_CATEGORIES = "redirect:/admin/diary/stickers/categories";

    private final DiaryStickerAdminService service;

    @GetMapping
    public String list(@ModelAttribute("filter") DiaryStickerFilter filter,
                       @RequestParam(defaultValue = "false") boolean partial,
                       Model model) {
        var stickers = service.getStickers(filter);
        model.addAttribute("stickers", stickers);
        model.addAttribute("resultCount", stickers.size());
        if (partial) {
            return LIST + " :: results";
        }
        model.addAttribute("categories", service.getCategories());
        model.addAttribute("stickerTypes", DiaryStickerType.values());
        model.addAttribute("accessTiers", DiaryStickerAccessTier.values());
        model.addAttribute("pageTitle", "스티커 관리");
        return LIST;
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        prepareStickerForm(model, new DiaryStickerForm(), null);
        return FORM;
    }

    @PostMapping
    public String create(@ModelAttribute("stickerForm") DiaryStickerForm form,
                         BindingResult bindingResult, Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            service.create(form);
        } catch (DiaryStickerValidationException exception) {
            reject(bindingResult, exception);
            prepareStickerForm(model, form, null);
            return FORM;
        } catch (IllegalArgumentException exception) {
            bindingResult.reject("sticker.image", exception.getMessage());
            prepareStickerForm(model, form, null);
            return FORM;
        }
        redirectAttributes.addFlashAttribute("message", "스티커를 등록했습니다.");
        return REDIRECT_LIST;
    }

    @GetMapping("/{id:\\d+}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        prepareStickerForm(model, service.getStickerForm(id), id);
        model.addAttribute("currentImageUrl", service.getSticker(id).getImageUrl());
        return FORM;
    }

    @PostMapping("/{id:\\d+}/edit")
    public String update(@PathVariable Long id,
                         @ModelAttribute("stickerForm") DiaryStickerForm form,
                         BindingResult bindingResult, Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            service.update(id, form);
        } catch (DiaryStickerValidationException exception) {
            reject(bindingResult, exception);
            prepareStickerForm(model, form, id);
            model.addAttribute("currentImageUrl", service.getSticker(id).getImageUrl());
            return FORM;
        } catch (IllegalArgumentException exception) {
            bindingResult.reject("sticker.image", exception.getMessage());
            prepareStickerForm(model, form, id);
            model.addAttribute("currentImageUrl", service.getSticker(id).getImageUrl());
            return FORM;
        }
        redirectAttributes.addFlashAttribute("message", "스티커를 수정했습니다.");
        return REDIRECT_LIST;
    }

    @PostMapping("/{id:\\d+}/hide")
    public String hide(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        service.hide(id);
        redirectAttributes.addFlashAttribute("message", "스티커를 숨겼습니다. 기존 다이어리에는 계속 표시됩니다.");
        return REDIRECT_LIST;
    }

    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute("categories", service.getCategories());
        model.addAttribute("pageTitle", "스티커 카테고리 관리");
        return CATEGORY_LIST;
    }

    @GetMapping("/categories/new")
    public String createCategoryForm(Model model) {
        prepareCategoryForm(model, new DiaryStickerCategoryForm(), null);
        return CATEGORY_FORM;
    }

    @PostMapping("/categories")
    public String createCategory(
            @ModelAttribute("categoryForm") DiaryStickerCategoryForm form,
            BindingResult bindingResult, Model model,
            RedirectAttributes redirectAttributes) {
        try {
            service.createCategory(form);
        } catch (DiaryStickerValidationException exception) {
            reject(bindingResult, exception);
            prepareCategoryForm(model, form, null);
            return CATEGORY_FORM;
        }
        redirectAttributes.addFlashAttribute("message", "카테고리를 등록했습니다.");
        return REDIRECT_CATEGORIES;
    }

    @GetMapping("/categories/{id:\\d+}/edit")
    public String editCategoryForm(@PathVariable Long id, Model model) {
        prepareCategoryForm(model, service.getCategoryForm(id), id);
        return CATEGORY_FORM;
    }

    @PostMapping("/categories/{id:\\d+}/edit")
    public String updateCategory(@PathVariable Long id,
                                 @ModelAttribute("categoryForm") DiaryStickerCategoryForm form,
                                 BindingResult bindingResult, Model model,
                                 RedirectAttributes redirectAttributes) {
        try {
            service.updateCategory(id, form);
        } catch (DiaryStickerValidationException exception) {
            reject(bindingResult, exception);
            prepareCategoryForm(model, form, id);
            return CATEGORY_FORM;
        }
        redirectAttributes.addFlashAttribute("message", "카테고리를 수정했습니다.");
        return REDIRECT_CATEGORIES;
    }

    private void prepareStickerForm(Model model, DiaryStickerForm form, Long id) {
        boolean edit = id != null;
        model.addAttribute("stickerForm", form);
        model.addAttribute("categories", service.getCategories());
        model.addAttribute("stickerTypes", DiaryStickerType.values());
        model.addAttribute("accessTiers", DiaryStickerAccessTier.values());
        model.addAttribute("editMode", edit);
        model.addAttribute("stickerId", id);
        model.addAttribute("formAction", edit
                ? "/admin/diary/stickers/" + id + "/edit"
                : "/admin/diary/stickers");
        model.addAttribute("pageTitle", edit ? "스티커 수정" : "스티커 등록");
    }

    private void prepareCategoryForm(Model model, DiaryStickerCategoryForm form, Long id) {
        boolean edit = id != null;
        model.addAttribute("categoryForm", form);
        model.addAttribute("editMode", edit);
        model.addAttribute("categoryId", id);
        model.addAttribute("formAction", edit
                ? "/admin/diary/stickers/categories/" + id + "/edit"
                : "/admin/diary/stickers/categories");
        model.addAttribute("pageTitle", edit ? "스티커 카테고리 수정" : "스티커 카테고리 등록");
    }

    private void reject(BindingResult bindingResult, DiaryStickerValidationException exception) {
        if (exception.getField() == null) {
            bindingResult.reject("sticker.invalid", exception.getMessage());
        } else {
            bindingResult.rejectValue(exception.getField(), "sticker.invalid", exception.getMessage());
        }
    }
}
