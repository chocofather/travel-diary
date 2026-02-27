package com.example.travlediary.service.recommend;

import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.service.category.CountryCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roulette-region")
@RequiredArgsConstructor
public class RouletteRegionController {
    private final CountryCategoryService countryCategoryService;

    // 1. 특정 parent의 하위 지역 뽑기 (ex. 국내/대륙/각국가)
    @GetMapping("/children/{parentId}")
    public List<CountryCategory> getChildren(@PathVariable Long parentId) {
        return countryCategoryService.getRegionsByParentId(parentId);
    }

    // 2. 대륙/대한민국만 (depth=1, parent=null)
    @GetMapping("/roots")
    public List<CountryCategory> getContinentRoots() {
        return countryCategoryService.getContinentRoots();
    }

    // 3. (선택) 국내/해외 구분 (root id list)
    @GetMapping("/domestic-roots")
    public List<Long> getDomesticRootIds() {
        return countryCategoryService.getDomesticRootIds();
    }
    @GetMapping("/overseas-roots")
    public List<Long> getOverseasRootIds() {
        return countryCategoryService.getOverseasRootIds();
    }

    @GetMapping("/random-overseas")
    public List<CountryCategory> getRandomOverseasCountries(@RequestParam(defaultValue = "15") int size) {
        // 1. 해외 대륙 루트 id들(대한민국 제외)
        List<Long> overseasRoots = countryCategoryService.getOverseasRootIds();

        // 2. 모든 해외 하위 지역 id들 재귀로 다 모으기
        List<Long> regionIds = new java.util.ArrayList<>();
        for (Long rootId : overseasRoots) {
            regionIds.addAll(countryCategoryService.getAllRegionIdsUnder(rootId));
        }

        // 3. 모든 해외 지역 리스트 한 번에 조회
        List<CountryCategory> allRegions = countryCategoryService.getRegionsByIds(regionIds);

        // 4. ***국가(나라)만 골라내기***
        // 예시: depth==2 또는 parent가 대륙(root)인 것만 남김 (DB 설계에 따라 다름)
        List<CountryCategory> onlyCountries = new java.util.ArrayList<>();
        for (CountryCategory region : allRegions) {
            // depth==2만 국가로 취급 (ex: 아시아→일본(2), 유럽→프랑스(2))
            // 만약 다르면, parentId가 대륙(root) id와 일치하면 국가로 인식
            if (region.getDepth() == 2) {
                onlyCountries.add(region);
            }
            // 또는 parentId가 overseasRoots에 포함된 것
            // if (overseasRoots.contains(region.getParentId())) {
            //     onlyCountries.add(region);
            // }
        }

        // 5. 랜덤 섞어서 size만큼만 리턴
        java.util.Collections.shuffle(onlyCountries);
        if (onlyCountries.size() > size) {
            onlyCountries = onlyCountries.subList(0, size);
        }
        return onlyCountries;
    }

}
