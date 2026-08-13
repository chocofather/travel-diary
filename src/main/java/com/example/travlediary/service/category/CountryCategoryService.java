package com.example.travlediary.service.category;

import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.repository.category.CountryCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CountryCategoryService {

    private final CountryCategoryMapper mapper;

    @Value("${custom.upload-path}")
    private String uploadPath;

    // 1. depth1(최상위): 대륙 or 대한민국만
    public List<CountryCategory> getRootRegions() {
        return mapper.selectDepth1();
    }

    // 2. 특정 부모의 하위 지역 리스트
    public List<CountryCategory> getRegionsByParentId(Long parentId) {
        return mapper.selectByParentId(parentId);
    }

    // 3. 특정 depth 전체 리스트
    public List<CountryCategory> getRegionsByDepth(int depth) {
        // parentId 없이 전체를 뽑을 때는 null로
        return mapper.findByDepth(depth, null);
    }

    public List<CountryCategory> getCourseCountries() {
        return mapper.selectCourseCountries();
    }

    // 4. 카테고리 ID로 조회
    public CountryCategory getById(Long id) {
        return mapper.selectById(id);
    }

    // 5. 아이콘 저장/업데이트
    public void saveIcon(Long id, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return;

        Path iconsDir = Paths.get(uploadPath, "icons");
        Files.createDirectories(iconsDir);

        String filename = UUID.randomUUID() + "_" + StringUtils.cleanPath(file.getOriginalFilename());
        Path dest = iconsDir.resolve(filename);
        file.transferTo(dest.toFile());

        String iconPath = "/uploads/icons/" + filename;
        mapper.updateIconPath(id, iconPath);
    }

    // 6. 특정 지역의 모든 하위 지역(자손까지) ID 반환
    public List<Long> getAllRegionIdsUnder(Long parentId) {
        // RECURSIVE 쿼리 지원하면 아래 주석 해제
        // return mapper.findAllRegionIdsUnder(parentId);

        // 직접 재귀 구현 (성능/정확도 모두 OK)
        List<Long> ids = new ArrayList<>();
        collectRegionIds(parentId, ids);
        return ids;
    }
    private void collectRegionIds(Long parentId, List<Long> result) {
        result.add(parentId);
        List<CountryCategory> children = mapper.selectByParentId(parentId);
        for (CountryCategory child : children) {
            collectRegionIds(child.getId(), result);
        }
    }

    // 7. 특정 parent에서 depth까지의 지역 반환 (예: parent=서울, depth=4 -> 구)
    public List<CountryCategory> getSubregions(Long parentId, int depth) {
        return mapper.selectByParentIdAndDepth(parentId, depth);
    }

    // 8. [국내] 대한민국 root id만 반환 (통일된 List<Long> 타입)
    public List<Long> getDomesticRootIds() {
        return List.of(7L); // 대한민국 ID
    }

    // 9. [해외] 대륙 루트 id 복수 반환
    public List<Long> getOverseasRootIds() {
        // 아시아, 유럽, 북미, 남미, 아프리카, 오세아니아 등
        return mapper.findByDepth(1, null).stream()
                .filter(c -> !c.getId().equals(7L)) // 대한민국(국내)만 제외
                .map(CountryCategory::getId)
                .toList();
    }

    // 10. depth1 전체(대륙/대한민국) 반환
    public List<CountryCategory> getContinentRoots() {
        // parentId 조건 없이 전체 depth=1 뽑으려면 null 넘겨야 함
        return mapper.findByDepth(1, null);
    }
    // depth + parentId 조합으로 리스트
    public List<CountryCategory> getRegionsByDepthAndParent(int depth, Long parentId) {
        return mapper.findByDepth(depth, parentId);
    }

    // 대한민국 뽑기
    public Long getKoreaRootId() {
        CountryCategory korea = mapper.selectByRegionNameAndDepth("대한민국", 1);
        return korea != null ? korea.getId() : null;
    }

    // region_id로 code 반환
    public String getCodeById(Long regionId) {
        return mapper.getCodeById(regionId);
    }

    public List<CountryCategory> getOverseasContinentRegions() {
        // 기존 하드코딩 or 필요시 is_domestic 컬럼 추가해 분기
        return mapper.findByDepth(1, null).stream()
                .filter(cat -> !cat.getId().equals(7L))
                .toList();
        // 또는 id != 7L
    }

    public List<CountryCategory> getRegionsByIds(List<Long> ids) {
        return mapper.selectByIds(ids);
    }

    public List<CountryCategory> findRandomOverseasCountries(int limit) {
        return mapper.findRandomOverseasCountries(limit);
    }
}
