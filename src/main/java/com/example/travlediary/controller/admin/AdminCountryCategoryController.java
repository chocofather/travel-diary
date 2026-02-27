package com.example.travlediary.controller.admin;

import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.service.category.CountryCategoryService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/admin/region-categories")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCountryCategoryController {

    private final CountryCategoryService countryCategoryService;
    private static final Long KOREA_ID = 7L;

    /** 1) 국내/해외 + depth별 리스트 */
    @GetMapping
    public String list(
            @RequestParam(value = "type", defaultValue = "domestic") String type,
            @RequestParam(value = "depth", defaultValue = "3") int depth,
            @RequestParam(value = "parentId", required = false) Long parentId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            Model model
    ) {

        System.out.println("▶▶▶ list() called with type=" + type
                + ", depth=" + depth + ", parentId=" + parentId);
        int pageSize = 20;
        PageHelper.startPage(page, pageSize);

        List<CountryCategory> list;
        if ("domestic".equals(type)) {
            // 대한민국 하위(서울, 경기, 부산 등)
            list = countryCategoryService.getRegionsByDepthAndParent(depth, KOREA_ID);
            model.addAttribute("domestic", true);
        } else {
            // 해외: 대륙(parentId==null, depth=1), 국가/도시(parentId!=null)
            if (parentId == null && depth == 1) {
                list = countryCategoryService.getRegionsByDepth(1).stream()
                        .filter(c -> !c.getId().equals(7L))
                        .toList();
            } else if (parentId != null) {
                list = countryCategoryService.getRegionsByDepthAndParent(depth, parentId);
            } else {
                list = List.of();
            }
            model.addAttribute("domestic", false);
        }

        PageInfo<CountryCategory> pageInfo = new PageInfo<>(list);

        model.addAttribute("pageInfo", pageInfo);
        model.addAttribute("type", type);
        model.addAttribute("depth", depth);
        model.addAttribute("parentId", parentId);

        model.addAttribute("showSubregionLink", "overseas".equals(type) && depth < 3);


        return "admin/region/category-list";
    }

    /** 2) 아이콘 업로드 폼 */
    @GetMapping("/{id}/icon")
    public String showIconForm(@PathVariable Long id, Model model) {
        CountryCategory c = countryCategoryService.getById(id);
        model.addAttribute("category", c);
        return "admin/region/icon-upload";
    }

    /** 3) 아이콘 업로드 처리 */
    @PostMapping("/{id}/icon")
    public String uploadIcon(@PathVariable Long id,
                             @RequestParam("icon") MultipartFile icon) throws IOException {
        countryCategoryService.saveIcon(id, icon);
        CountryCategory c = countryCategoryService.getById(id);

        // 리다이렉트 파라미터 결정
        String type = (c.getParentId() != null && c.getParentId().equals(KOREA_ID)) ? "domestic" : "overseas";
        int depth = c.getDepth();
        Long parentId = c.getParentId();

        StringBuilder redirect = new StringBuilder("redirect:/admin/region-categories?type=")
                .append(type)
                .append("&depth=").append(depth);
        if (parentId != null) {
            redirect.append("&parentId=").append(parentId);
        }
        return redirect.toString();
    }
}
