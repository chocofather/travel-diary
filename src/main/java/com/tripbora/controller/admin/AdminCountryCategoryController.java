package com.tripbora.controller.admin;

import com.tripbora.dto.CountryCategoryForm;
import com.tripbora.model.CountryCategory;
import com.tripbora.service.category.CountryCategoryAdminService;
import com.tripbora.service.category.CountryCategoryBulkCreateService;
import com.tripbora.service.category.CountryCategoryDeleteBlockedException;
import com.tripbora.service.category.CountryCategoryService;
import com.tripbora.service.category.CountryCategoryValidationException;
import com.tripbora.service.file.UnsupportedImageFormatException;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Controller
@RequestMapping("/admin/region-categories")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminCountryCategoryController {

    private static final int PAGE_SIZE = 20;

    private final CountryCategoryService countryCategoryService;
    private final CountryCategoryAdminService countryCategoryAdminService;
    private final CountryCategoryBulkCreateService countryCategoryBulkCreateService;
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
        List<CountryCategory> list;
        if ("domestic".equals(type)) {
            // 대한민국 하위(서울, 경기, 부산 등). parentId 가 있으면 그 시/도의 하위 지역
            list = countryCategoryService.getRegionsByDepthAndParent(depth, parentId != null ? parentId : KOREA_ID);
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

        /*
          지역 목록은 캐시에서 오므로 PageHelper 로 자르지 않고 여기서 자른다.
          PageHelper 는 다음 SQL 한 번을 자르는데, 캐시가 응답하면 그 설정이 남아 엉뚱한 조회를 자르고
          캐시가 비어 있으면 잘린 목록이 그대로 캐시에 담긴다.
        */
        PageInfo<CountryCategory> pageInfo = new PageInfo<>(pageOf(list, page));

        model.addAttribute("pageInfo", pageInfo);
        // 수정 대화상자가 현재 일본어·중국어 이름을 채우는 데 쓴다.
        model.addAttribute("regionTranslations", countryCategoryAdminService.translationsByRegion(pageInfo.getList()));
        model.addAttribute("type", type);
        model.addAttribute("depth", depth);
        model.addAttribute("parentId", parentId);
        model.addAttribute("parentCategory", parentId == null ? null : countryCategoryService.getById(parentId));

        boolean domesticTop = parentId == null || countryCategoryService.getDomesticRootIds().contains(parentId);
        model.addAttribute("showSubregionLink",
                ("overseas".equals(type) && depth < 3) || ("domestic".equals(type) && domesticTop));

        // 지역 등록 대화상자. 검증 실패로 돌아왔으면 입력값과 사유를 그대로 다시 보여 준다.
        if (!model.containsAttribute("regionForm")) {
            model.addAttribute("regionForm", new CountryCategoryForm());
        }
        if (!model.containsAttribute("editForm")) {
            model.addAttribute("editForm", new CountryCategoryForm());
        }
        CountryCategoryForm regionForm = (CountryCategoryForm) model.getAttribute("regionForm");
        model.addAttribute("parentOptionGroups", countryCategoryAdminService.getParentOptionGroups());
        model.addAttribute("selectedParentId", regionForm != null && regionForm.getParentId() != null
                ? regionForm.getParentId()
                : defaultParentId(type, depth, parentId));

        return "admin/region/category-list";
    }

    /** 2) 지역 등록 */
    @PostMapping
    public String create(@ModelAttribute("regionForm") CountryCategoryForm form,
                         @RequestParam(value = "listType", defaultValue = "domestic") String listType,
                         @RequestParam(value = "listDepth", defaultValue = "3") int listDepth,
                         @RequestParam(value = "listParentId", required = false) Long listParentId,
                         RedirectAttributes redirectAttributes) {
        CountryCategory created;
        try {
            created = countryCategoryAdminService.create(form);
        } catch (CountryCategoryValidationException exception) {
            redirectAttributes.addFlashAttribute("regionForm", form);
            redirectAttributes.addFlashAttribute("regionFormError", exception.getMessage());
            return listRedirect(listType, listDepth, listParentId, 1);
        }

        redirectAttributes.addFlashAttribute("message", "'" + created.getRegionName() + "' 지역을 등록했습니다.");
        // 새 지역이 보이는 목록(같은 부모 아래, 마지막 쪽)으로 보낸다.
        boolean domestic = countryCategoryService.getDomesticRootIds().contains(rootIdOf(created));
        Long listParent = domestic && countryCategoryService.getDomesticRootIds().contains(created.getParentId())
                ? null
                : created.getParentId();
        int count = countryCategoryService.getRegionsByDepthAndParent(created.getDepth(), created.getParentId()).size();
        int lastPage = Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
        return listRedirect(domestic ? "domestic" : "overseas", created.getDepth(), listParent, lastPage);
    }

    /** 2-1) 지역 일괄 등록. 행마다 따로 저장하고 결과를 대화상자에 다시 보여 준다. */
    @PostMapping("/bulk")
    public String bulkCreate(@RequestParam(value = "json", required = false) String json,
                             @RequestParam(value = "listType", defaultValue = "domestic") String listType,
                             @RequestParam(value = "listDepth", defaultValue = "3") int listDepth,
                             @RequestParam(value = "listParentId", required = false) Long listParentId,
                             @RequestParam(value = "listPage", defaultValue = "1") int listPage,
                             RedirectAttributes redirectAttributes) {
        CountryCategoryBulkCreateService.Result result = countryCategoryBulkCreateService.create(json);
        redirectAttributes.addFlashAttribute("bulkResult", result);
        // 전부 등록됐으면 입력란을 비우고, 아니면 고쳐서 다시 보낼 수 있게 남긴다.
        if (result.fileError() != null || result.created() < result.total()) {
            redirectAttributes.addFlashAttribute("bulkJson", json);
        }
        return listRedirect(listType, listDepth, listParentId, listPage);
    }

    /** 2-2) 지역 이름·번역 수정. id·부모·계층·코드는 바꾸지 않는다. */
    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @ModelAttribute("editForm") CountryCategoryForm form,
                         @RequestParam(value = "listType", defaultValue = "domestic") String listType,
                         @RequestParam(value = "listDepth", defaultValue = "3") int listDepth,
                         @RequestParam(value = "listParentId", required = false) Long listParentId,
                         @RequestParam(value = "listPage", defaultValue = "1") int listPage,
                         RedirectAttributes redirectAttributes) {
        try {
            CountryCategory updated = countryCategoryAdminService.update(id, form);
            redirectAttributes.addFlashAttribute("message", "'" + updated.getRegionName() + "' 지역을 수정했습니다.");
        } catch (CountryCategoryValidationException exception) {
            redirectAttributes.addFlashAttribute("editForm", form);
            redirectAttributes.addFlashAttribute("editRegionId", id);
            redirectAttributes.addFlashAttribute("editFormError", exception.getMessage());
        }
        return listRedirect(listType, listDepth, listParentId, listPage);
    }

    /** 3) 지역 삭제. 지울 수 없으면 이유를 목록에 표시한다. */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam(value = "listType", defaultValue = "domestic") String listType,
                         @RequestParam(value = "listDepth", defaultValue = "3") int listDepth,
                         @RequestParam(value = "listParentId", required = false) Long listParentId,
                         @RequestParam(value = "listPage", defaultValue = "1") int listPage,
                         RedirectAttributes redirectAttributes) {
        try {
            CountryCategory deleted = countryCategoryAdminService.delete(id);
            redirectAttributes.addFlashAttribute("message", "'" + deleted.getRegionName() + "' 지역을 삭제했습니다.");
        } catch (CountryCategoryDeleteBlockedException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return listRedirect(listType, listDepth, listParentId, listPage);
    }

    /** 4) 아이콘 업로드 폼 */
    @GetMapping("/{id}/icon")
    public String showIconForm(@PathVariable Long id, Model model) {
        CountryCategory c = countryCategoryService.getById(id);
        model.addAttribute("category", c);
        return "admin/region/icon-upload";
    }

    /**
     * 5) 아이콘 업로드 처리
     *
     * <p>올린 파일은 {@code /uploads/icons/**} 에서 그대로 공개되므로 실제로 펼쳐지는 이미지만
     * 받는다. 형식이 맞지 않으면 저장하지 않고 업로드 화면으로 돌아가 이유를 알려 준다.
     */
    @PostMapping("/{id}/icon")
    public String uploadIcon(@PathVariable Long id,
                             @RequestParam("icon") MultipartFile icon,
                             Model model) {
        try {
            countryCategoryService.saveIcon(id, icon);
        } catch (UnsupportedImageFormatException exception) {
            model.addAttribute("category", countryCategoryService.getById(id));
            model.addAttribute("iconError", exception.getMessage());
            return "admin/region/icon-upload";
        }
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

    private static Page<CountryCategory> pageOf(List<CountryCategory> all, int requestedPage) {
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int pageNum = Math.min(Math.max(1, requestedPage), pages);
        Page<CountryCategory> page = new Page<>(pageNum, PAGE_SIZE);
        page.setTotal(all.size());
        int from = (pageNum - 1) * PAGE_SIZE;
        page.addAll(all.subList(Math.min(from, all.size()), Math.min(from + PAGE_SIZE, all.size())));
        return page;
    }

    /** 지금 보고 있는 목록의 부모를 등록 대화상자의 기본 부모로 쓴다. 대륙 목록에서는 고르지 않는다. */
    private Long defaultParentId(String type, int depth, Long parentId) {
        if (parentId != null) {
            return parentId;
        }
        if ("domestic".equals(type)) {
            return countryCategoryService.getKoreaRootId();
        }
        return null;
    }

    private Long rootIdOf(CountryCategory region) {
        List<CountryCategory> path = countryCategoryService.getRegionPath(region.getId());
        return path.isEmpty() ? null : path.get(0).getId();
    }

    private static String listRedirect(String type, int depth, Long parentId, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/admin/region-categories")
                .queryParam("type", "overseas".equals(type) ? "overseas" : "domestic")
                .queryParam("depth", depth);
        if (parentId != null) {
            builder.queryParam("parentId", parentId);
        }
        if (page > 1) {
            builder.queryParam("page", page);
        }
        return "redirect:" + builder.toUriString();
    }
}
