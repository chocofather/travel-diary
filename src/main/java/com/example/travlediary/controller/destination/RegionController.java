package com.example.travlediary.controller.destination;

import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.service.category.CountryCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
public class RegionController {

    private final CountryCategoryService countryCategoryService;

    @GetMapping
    public List<CountryCategory> getRegions(@RequestParam(required = false) Long parentId) {
        return countryCategoryService.getRegionsByParentId(parentId);
    }


}