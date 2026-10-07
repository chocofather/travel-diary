package com.tripbora.service.destinationimport;

import com.tripbora.dto.destinationimport.DestinationImportIssue;
import com.tripbora.dto.destinationimport.DestinationImportItem;
import com.tripbora.model.Amenity;
import com.tripbora.model.Category;
import com.tripbora.model.CountryCategory;
import com.tripbora.model.DestinationType;
import com.tripbora.service.amenity.AmenityService;
import com.tripbora.service.category.CategoryService;
import com.tripbora.service.category.CountryCategoryService;
import com.tripbora.service.destination.DestinationService;
import com.tripbora.service.kto.KtoTourRegionMatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JSON 의 사람이 읽는 값(지역 이름·카테고리 이름·편의시설 code)을 현재 DB 의 ID 로 바꾼다.
 *
 * <ul>
 *   <li>지역: 이름이 정확히 같은 곳만. parent_id 를 따라 국가 → city → district 순서로 내려간다.
 *       국내 시·도는 TourAPI 주소 매칭과 같은 정식 명칭 별칭("서울특별시" → "서울")만 받는다.
 *       하위 지역이 있으면 그 단계까지 반드시 지정해야 한다</li>
 *   <li>카테고리: categories.name. 등록폼처럼 그 유형(음식점·카페는 두 유형 합집합)에 매핑된 것만</li>
 *   <li>편의시설: amenities.code(대소문자 무시). 그 유형(음식점·카페는 합집합)에 매핑된 것만</li>
 * </ul>
 * 비슷한 이름으로 억지로 맞추지 않는다. 틀린 곳에 조용히 연결되는 것보다 오류가 낫다.
 */
@Component
@RequiredArgsConstructor
public class DestinationImportMasterResolver {

    private static final int MAX_LISTED_NAMES = 40;
    private static final List<String> REGION_LEVELS = List.of("city", "district");

    private final CountryCategoryService countryCategoryService;
    private final CategoryService categoryService;
    private final AmenityService amenityService;

    /**
     * 매핑 결과.
     *
     * @param domestic        국내면 true, 해외면 false, 국가를 찾지 못했으면 null
     * @param regionPath      국가부터 매핑한 지역까지의 이름
     * @param mainCategoryId  실제 저장될 대표 카테고리(JSON 에 없으면 기존 규칙으로 고른 값)
     */
    public record Resolution(Long regionId,
                             Boolean domestic,
                             List<String> regionPath,
                             List<Long> categoryIds,
                             Long mainCategoryId,
                             String mainCategoryName,
                             Long requestedMainCategoryId,
                             List<Integer> amenityIds,
                             List<DestinationImportIssue> errors) {
        public String regionLabel() {
            return regionPath.isEmpty() ? null : String.join(" > ", regionPath);
        }
    }

    /** 편의시설 한 건(내보내기에도 쓴다). */
    public record AmenityRef(Integer id, String code, String name) {
    }

    /** 등록폼과 같이 음식점·카페는 한 입력 화면과 같은 저장 테이블을 쓰므로 두 유형을 합쳐 본다. */
    public static List<DestinationType> typeGroup(DestinationType type) {
        return type == DestinationType.RESTAURANTS || type == DestinationType.CAFE
                ? List.of(DestinationType.RESTAURANTS, DestinationType.CAFE)
                : List.of(type);
    }

    /** 요청 한 번(미리보기 또는 등록 한 건) 동안 쓰는 기준 데이터를 한 번에 읽는다. */
    public MasterData load() {
        Map<DestinationType, List<Category>> categories = new EnumMap<>(DestinationType.class);
        Map<DestinationType, List<AmenityRef>> amenities = new EnumMap<>(DestinationType.class);
        for (DestinationType type : DestinationType.values()) {
            DestinationType[] group = typeGroup(type).toArray(DestinationType[]::new);
            categories.put(type, categoryService.getByDestinationTypes(group));
            amenities.put(type, amenityService.getAmenityTranslationsByDestinationTypes(
                            DestinationImportFields.KOREAN, group).stream()
                    .filter(Objects::nonNull)
                    .map(translation -> new AmenityRef(translation.getAmenityId(), translation.getCode(),
                            translation.getName()))
                    .toList());
        }
        Map<String, Category> allCategories = new HashMap<>();
        for (Category category : nonNull(categoryService.getAll())) {
            String name = DestinationImportParser.clean(category.getName());
            if (name != null) allCategories.putIfAbsent(name, category);
        }
        Set<String> allAmenityCodes = new HashSet<>();
        for (Amenity amenity : nonNull(amenityService.getAllAmenities())) {
            if (amenity != null && amenity.getCode() != null) allAmenityCodes.add(codeKey(amenity.getCode()));
        }
        List<CountryCategory> domesticRoots = countryCategoryService.getDomesticRootIds().stream()
                .map(countryCategoryService::getById).filter(Objects::nonNull).toList();
        List<CountryCategory> overseasRoots = countryCategoryService.getOverseasRootIds().stream()
                .map(countryCategoryService::getById).filter(Objects::nonNull).toList();
        return new MasterData(categories, amenities, allCategories, allAmenityCodes, domesticRoots, overseasRoots);
    }

    public Resolution resolve(DestinationImportItem item, String path, MasterData master) {
        List<DestinationImportIssue> errors = new ArrayList<>();
        RegionResult region = resolveRegion(item.region(), path + ".region", master, errors);
        List<Long> categoryIds = new ArrayList<>();
        Long requestedMain = null;
        List<Integer> amenityIds = new ArrayList<>();
        Long mainCategoryId = null;
        String mainCategoryName = null;
        if (item.type() != null) {
            Map<String, Long> resolvedByName = resolveCategories(item, path, master, categoryIds, errors);
            if (item.mainCategory() != null) {
                if (!item.categories().contains(item.mainCategory())) {
                    errors.add(issue(path + ".mainCategory", "categories 중 하나여야 합니다: " + item.mainCategory()));
                } else {
                    requestedMain = resolvedByName.get(item.mainCategory());
                }
            }
            resolveAmenities(item, path, master, amenityIds, errors);
            if (errors.stream().noneMatch(error -> error.path().startsWith(path + ".categories")
                    || error.path().startsWith(path + ".mainCategory"))) {
                // 저장과 같은 규칙으로 대표를 고른다. 비어 있으면 선택한 것 중 가장 작은 ID 다.
                mainCategoryId = DestinationService.resolveMainCategoryId(categoryIds, requestedMain);
                mainCategoryName = master.categoryName(item.type(), mainCategoryId);
            }
        }
        return new Resolution(region.regionId(), region.domestic(), region.path(), List.copyOf(categoryIds),
                mainCategoryId, mainCategoryName, requestedMain, List.copyOf(amenityIds), List.copyOf(errors));
    }

    private record RegionResult(Long regionId, Boolean domestic, List<String> path) {
    }

    private RegionResult resolveRegion(DestinationImportItem.Region region, String path, MasterData master,
                                       List<DestinationImportIssue> errors) {
        if (region == null || region.country() == null) {
            return new RegionResult(null, null, List.of());
        }
        String country = region.country();
        List<CountryCategory> countries = master.domesticRoots().stream()
                .filter(candidate -> sameName(candidate, country)).toList();
        boolean domestic = !countries.isEmpty();
        if (!domestic) {
            List<CountryCategory> matched = new ArrayList<>();
            for (CountryCategory continent : master.overseasRoots()) {
                master.children(continent.getId(), countryCategoryService).stream()
                        .filter(candidate -> sameName(candidate, country)).forEach(matched::add);
            }
            countries = matched;
        }
        if (countries.isEmpty()) {
            errors.add(issue(path + ".country", "'" + country + "' 국가를 찾지 못했습니다. "
                    + "마스터 데이터(destination-import-master.json)의 국가 이름과 정확히 같아야 합니다."));
            return new RegionResult(null, null, List.of());
        }
        if (countries.size() > 1) {
            errors.add(issue(path + ".country", "'" + country + "'에 해당하는 국가가 여러 곳입니다: "
                    + names(countries)));
            return new RegionResult(null, domestic, List.of());
        }
        CountryCategory current = countries.get(0);
        List<String> labels = new ArrayList<>(List.of(current.getRegionName()));
        List<String> values = List.of(nullToEmpty(region.city()), nullToEmpty(region.district()));
        for (int level = 0; level < REGION_LEVELS.size(); level++) {
            String field = REGION_LEVELS.get(level);
            String value = values.get(level).isEmpty() ? null : values.get(level);
            List<CountryCategory> children = master.children(current.getId(), countryCategoryService);
            if (children.isEmpty()) {
                for (int rest = level; rest < REGION_LEVELS.size(); rest++) {
                    if (!values.get(rest).isEmpty()) {
                        errors.add(issue(path + "." + REGION_LEVELS.get(rest), "'" + current.getRegionName()
                                + "' 아래에는 하위 지역이 없습니다. 비워 주세요."));
                        return new RegionResult(null, domestic, labels);
                    }
                }
                return new RegionResult(current.getId(), domestic, labels);
            }
            if (value == null) {
                errors.add(issue(path + "." + field, "'" + current.getRegionName() + "' 아래 지역을 지정해야 합니다. "
                        + "사용할 수 있는 지역: " + names(children)));
                return new RegionResult(null, domestic, labels);
            }
            String wanted = domestic && level == 0 ? KtoTourRegionMatchService.provinceName(value) : value;
            List<CountryCategory> matched = children.stream().filter(child -> sameName(child, wanted)).toList();
            if (matched.size() != 1) {
                errors.add(issue(path + "." + field, matched.isEmpty()
                        ? "'" + current.getRegionName() + "' 아래에 '" + value + "' 지역이 없습니다. 사용할 수 있는 지역: "
                        + names(children)
                        : "'" + value + "'에 해당하는 지역이 여러 곳입니다: " + names(matched)));
                return new RegionResult(null, domestic, labels);
            }
            current = matched.get(0);
            labels.add(current.getRegionName());
        }
        if (!master.children(current.getId(), countryCategoryService).isEmpty()) {
            errors.add(issue(path + ".district", "'" + current.getRegionName()
                    + "' 아래 지역은 JSON 일괄등록에서 지정할 수 없습니다. 등록폼에서 직접 등록해 주세요."));
            return new RegionResult(null, domestic, labels);
        }
        return new RegionResult(current.getId(), domestic, labels);
    }

    /** @return JSON 카테고리 이름 → ID (찾은 것만) */
    private Map<String, Long> resolveCategories(DestinationImportItem item, String path, MasterData master,
                                                List<Long> categoryIds, List<DestinationImportIssue> errors) {
        Map<String, Category> allowed = new LinkedHashMap<>();
        for (Category category : master.categories(item.type())) {
            String name = DestinationImportParser.clean(category.getName());
            if (name != null) allowed.putIfAbsent(name, category);
        }
        Map<String, Long> resolved = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < item.categories().size(); index++) {
            String name = item.categories().get(index);
            String itemPath = path + ".categories[" + index + "]";
            if (!seen.add(name)) {
                errors.add(issue(itemPath, "같은 카테고리가 두 번 있습니다: " + name));
                continue;
            }
            Category category = allowed.get(name);
            if (category != null) {
                categoryIds.add(category.getId());
                resolved.put(name, category.getId());
            } else if (master.allCategories().containsKey(name)) {
                errors.add(issue(itemPath, "'" + name + "'은(는) " + item.type().name()
                        + " 여행지에서 쓸 수 없는 카테고리입니다. 사용할 수 있는 카테고리: " + limited(allowed.keySet())));
            } else {
                errors.add(issue(itemPath, "'" + name + "' 카테고리가 없습니다. 사용할 수 있는 카테고리: "
                        + limited(allowed.keySet())));
            }
        }
        return resolved;
    }

    private void resolveAmenities(DestinationImportItem item, String path, MasterData master,
                                  List<Integer> amenityIds, List<DestinationImportIssue> errors) {
        Map<String, AmenityRef> allowed = new LinkedHashMap<>();
        for (AmenityRef amenity : master.amenities(item.type())) {
            if (amenity.code() != null) allowed.putIfAbsent(codeKey(amenity.code()), amenity);
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < item.amenities().size(); index++) {
            String code = item.amenities().get(index);
            String itemPath = path + ".amenities[" + index + "]";
            String key = codeKey(code);
            if (!seen.add(key)) {
                errors.add(issue(itemPath, "같은 편의시설이 두 번 있습니다: " + code));
                continue;
            }
            AmenityRef amenity = allowed.get(key);
            if (amenity != null) {
                amenityIds.add(amenity.id());
            } else if (master.allAmenityCodes().contains(key)) {
                errors.add(issue(itemPath, "'" + code + "'은(는) " + item.type().name()
                        + " 여행지에서 쓸 수 없는 편의시설입니다. 사용할 수 있는 code: " + limited(allowedCodes(allowed))));
            } else {
                errors.add(issue(itemPath, "'" + code + "' 편의시설 code 가 없습니다. 사용할 수 있는 code: "
                        + limited(allowedCodes(allowed))));
            }
        }
    }

    private static List<String> allowedCodes(Map<String, AmenityRef> allowed) {
        return allowed.values().stream().map(AmenityRef::code).toList();
    }

    /** 이름(region_name) 또는 영문 이름(name_en, 대소문자 무시)이 정확히 같은지. */
    static boolean sameName(CountryCategory region, String name) {
        String korean = DestinationImportParser.clean(region.getRegionName());
        String english = DestinationImportParser.clean(region.getNameEn());
        return name.equals(korean) || (english != null && name.equalsIgnoreCase(english));
    }

    private static String codeKey(String code) {
        return code.strip().toUpperCase(Locale.ROOT);
    }

    private static String names(List<CountryCategory> regions) {
        return limited(regions.stream().map(CountryCategory::getRegionName).toList());
    }

    private static String limited(Collection<String> names) {
        if (names.isEmpty()) {
            return "(없음)";
        }
        List<String> list = new ArrayList<>(names);
        String shown = list.stream().limit(MAX_LISTED_NAMES).collect(Collectors.joining(", "));
        return list.size() > MAX_LISTED_NAMES ? shown + " 외 " + (list.size() - MAX_LISTED_NAMES) + "개" : shown;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static <T> List<T> nonNull(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static DestinationImportIssue issue(String path, String message) {
        return new DestinationImportIssue(path, message);
    }

    /**
     * 요청 한 번 동안의 기준 데이터. 지역 하위 목록은 처음 볼 때만 읽어 둔다.
     */
    public static final class MasterData {
        private final Map<DestinationType, List<Category>> categories;
        private final Map<DestinationType, List<AmenityRef>> amenities;
        private final Map<String, Category> allCategories;
        private final Set<String> allAmenityCodes;
        private final List<CountryCategory> domesticRoots;
        private final List<CountryCategory> overseasRoots;
        private final Map<Long, List<CountryCategory>> children = new HashMap<>();

        MasterData(Map<DestinationType, List<Category>> categories,
                   Map<DestinationType, List<AmenityRef>> amenities,
                   Map<String, Category> allCategories,
                   Set<String> allAmenityCodes,
                   List<CountryCategory> domesticRoots,
                   List<CountryCategory> overseasRoots) {
            this.categories = categories;
            this.amenities = amenities;
            this.allCategories = allCategories;
            this.allAmenityCodes = allAmenityCodes;
            this.domesticRoots = domesticRoots;
            this.overseasRoots = overseasRoots;
        }

        /** 이 유형에서 쓸 수 있는 카테고리(음식점·카페는 합집합). */
        public List<Category> categories(DestinationType type) {
            return categories.getOrDefault(type, List.of());
        }

        /** 이 유형에서 쓸 수 있는 편의시설(음식점·카페는 합집합). */
        public List<AmenityRef> amenities(DestinationType type) {
            return amenities.getOrDefault(type, List.of());
        }

        Map<String, Category> allCategories() {
            return allCategories;
        }

        Set<String> allAmenityCodes() {
            return allAmenityCodes;
        }

        public List<CountryCategory> domesticRoots() {
            return domesticRoots;
        }

        public List<CountryCategory> overseasRoots() {
            return overseasRoots;
        }

        /** parent_id 로 찾은 하위 지역. 화면에서 숨긴 지역(is_visible = 0)은 고를 수 없으므로 뺀다. */
        public List<CountryCategory> children(Long parentId, CountryCategoryService service) {
            return children.computeIfAbsent(parentId, id -> {
                List<CountryCategory> found = service.getRegionsByParentId(id);
                return found == null ? List.of() : found.stream()
                        .filter(region -> region != null
                                && (region.getIsVisible() == null || region.getIsVisible() != 0))
                        .toList();
            });
        }

        String categoryName(DestinationType type, Long categoryId) {
            if (categoryId == null) return null;
            return categories(type).stream().filter(category -> categoryId.equals(category.getId()))
                    .map(Category::getName).findFirst().orElse(null);
        }
    }
}
