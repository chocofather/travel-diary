package com.example.travlediary.controller.admin;

import com.example.travlediary.dto.CategoryForm;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.service.category.CategoryInUseException;
import com.example.travlediary.service.category.CategoryService;
import com.example.travlediary.service.category.CategoryValidationException;
import com.example.travlediary.service.category.DuplicateCategoryNameException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/categories")
public class AdminCategoryController {

    private static final String CREATE_VIEW = "admin/categories/create";
    private static final String EDIT_VIEW = "admin/categories/edit";
    private static final String REDIRECT_LIST = "redirect:/admin/categories";

    /** 체크박스 표시용 라벨. 값 자체는 DestinationType enum 에서 온다. */
    private static final Map<DestinationType, String> DESTINATION_TYPE_LABELS = Map.of(
            DestinationType.ATTRACTION, "여행지",
            DestinationType.ACCOMMODATION, "숙소",
            DestinationType.RESTAURANTS, "식당",
            DestinationType.CAFE, "카페",
            DestinationType.SHOP, "쇼핑",
            DestinationType.ACTIVITY, "액티비티"
    );

    /** 목록의 badge 는 enum 이름 문자열로 조회하므로 같은 라벨을 이름 키로도 제공한다. */
    private static final Map<String, String> DESTINATION_TYPE_LABELS_BY_NAME =
            DESTINATION_TYPE_LABELS.entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(
                            entry -> entry.getKey().name(), Map.Entry::getValue));

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

   // 카테고리 리스트
    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", categoryService.getAll()); // 전체 카테고리 조회
        // 적용 대상 badge 는 기존 태그 맵(카테고리 -> "ATTRACTION SHOP")을 그대로 쓴다.
        model.addAttribute("categoryTypeTags", categoryService.getCategoryDestinationTypeTags());
        model.addAttribute("destinationTypeLabelsByName", DESTINATION_TYPE_LABELS_BY_NAME);
        // 목록 상단 필터 버튼도 enum 에서 그린다.
        addFormOptions(model);
        return "admin/categories/list";
    }

    // 등록폼
    @GetMapping("/create")
    public String showForm(Model model) {
        model.addAttribute("categoryForm", new CategoryForm());
        addFormOptions(model);
        return CREATE_VIEW;
    }

    // 등록 처리 (카테고리 이름 + 적용 대상 통합)
    @PostMapping
    public String create(@ModelAttribute("categoryForm") CategoryForm form,
                         BindingResult bindingResult,
                         Model model) {
        try {
            categoryService.createCategory(form);
        } catch (DuplicateCategoryNameException exception) {
            bindingResult.rejectValue("name", "duplicate", exception.getMessage());
            addFormOptions(model);
            return CREATE_VIEW;
        } catch (CategoryValidationException exception) {
            bindingResult.rejectValue(exception.getField(), "invalid", exception.getMessage());
            addFormOptions(model);
            return CREATE_VIEW;
        }
        return REDIRECT_LIST;
    }

    // 수정 폼
    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("categoryForm")) {
            model.addAttribute("categoryForm", categoryService.getCategoryForm(id));
        }
        addFormOptions(model);
        model.addAttribute("categoryId", id);
        return EDIT_VIEW;
    }

    // 수정 처리 (이름 + 적용 대상)
    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @ModelAttribute("categoryForm") CategoryForm form,
                         BindingResult bindingResult,
                         Model model) {
        form.setId(id);
        try {
            categoryService.updateCategory(form);
        } catch (DuplicateCategoryNameException exception) {
            bindingResult.rejectValue("name", "duplicate", exception.getMessage());
            return prepareEditModel(model, id);
        } catch (CategoryValidationException exception) {
            bindingResult.rejectValue(exception.getField(), "invalid", exception.getMessage());
            return prepareEditModel(model, id);
        }
        return REDIRECT_LIST;
    }

    /** 검증 실패로 다시 그릴 때도 선택지는 다시 채우고, 입력값은 폼 그대로 둔다. */
    private String prepareEditModel(Model model, Long id) {
        addFormOptions(model);
        model.addAttribute("categoryId", id);
        return EDIT_VIEW;
    }

    private void addFormOptions(Model model) {
        model.addAttribute("destinationTypes", DestinationType.values());
        model.addAttribute("destinationTypeLabels", DESTINATION_TYPE_LABELS);
    }


    // 삭제 처리 (여행지에서 사용 중이면 차단하고 목록에 사유를 표시한다)
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            categoryService.deleteCategory(id);
        } catch (CategoryInUseException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/categories";
    }
}
