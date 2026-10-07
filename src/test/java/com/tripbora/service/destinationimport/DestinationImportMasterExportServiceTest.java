package com.tripbora.service.destinationimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripbora.model.Amenity;
import com.tripbora.model.AmenityTranslation;
import com.tripbora.model.Category;
import com.tripbora.model.CountryCategory;
import com.tripbora.repository.amenity.AmenityMapper;
import com.tripbora.repository.category.CategoryMapper;
import com.tripbora.service.amenity.AmenityService;
import com.tripbora.service.category.CategoryService;
import com.tripbora.service.category.CountryCategoryService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** AI 용 마스터 데이터: 매핑과 같은 기준의 이름·code 만 내보내고 DB 번호는 넣지 않는다. */
class DestinationImportMasterExportServiceTest {

    private final CountryCategoryService regions = mock(CountryCategoryService.class);
    private final CategoryMapper categoryMapper = mock(CategoryMapper.class);
    private final AmenityMapper amenityMapper = mock(AmenityMapper.class);
    private final List<CountryCategory> all = new ArrayList<>();

    @Test
    void exportsNamesCodesAndFieldLimitsWithoutDatabaseIds() throws Exception {
        region(7L, "대한민국", "South Korea", null, 1);
        region(38L, "서울", "Seoul", 7L, 1);
        region(235L, "종로구", "Jongno-gu", 38L, 1);
        region(1L, "아시아", "Asia", null, 1);
        region(8L, "일본", "Japan", 1L, 1);
        region(55L, "후쿠오카", "Fukuoka", 8L, 1);
        region(57L, "숨긴 지역", "Hidden", 8L, 0);
        when(regions.getDomesticRootIds()).thenReturn(List.of(7L));
        when(regions.getOverseasRootIds()).thenReturn(List.of(1L));
        when(regions.getById(anyLong())).thenAnswer(invocation -> all.stream()
                .filter(region -> region.getId().equals(invocation.getArgument(0))).findFirst().orElse(null));
        when(regions.getRegionsByParentId(anyLong())).thenAnswer(invocation -> all.stream()
                .filter(region -> invocation.getArgument(0).equals(region.getParentId()))
                .sorted(Comparator.comparing(CountryCategory::getId)).toList());
        Map<String, List<Category>> categories = Map.of(
                "ATTRACTION", List.of(category(1L, "궁궐·역사")),
                "RESTAURANTS", List.of(category(3L, "한식")),
                "CAFE", List.of(category(4L, "디저트")));
        when(categoryMapper.findByDestinationType(anyString()))
                .thenAnswer(invocation -> categories.getOrDefault(invocation.<String>getArgument(0), List.of()));
        when(categoryMapper.findAll()).thenReturn(List.of());
        AmenityTranslation wifi = new AmenityTranslation();
        wifi.setAmenityId(11);
        wifi.setCode("WIFI");
        wifi.setName("와이파이");
        when(amenityMapper.findTranslationsByDestinationTypeAndLang(eq("CAFE"), eq("ko"))).thenReturn(List.of(wifi));
        when(amenityMapper.findAll()).thenReturn(List.<Amenity>of());
        DestinationImportMasterExportService service = new DestinationImportMasterExportService(
                new DestinationImportMasterResolver(regions, new CategoryService(categoryMapper),
                        new AmenityService(amenityMapper, null, null)), regions);

        JsonNode exported = new ObjectMapper().valueToTree(service.export());

        assertThat(exported.at("/regions/domestic/0/name").asText()).isEqualTo("대한민국");
        assertThat(exported.at("/regions/domestic/0/children/0/name").asText()).isEqualTo("서울");
        assertThat(exported.at("/regions/domestic/0/children/0/children/0/nameEn").asText()).isEqualTo("Jongno-gu");
        assertThat(exported.at("/regions/overseas/0/children/0/name").asText()).isEqualTo("일본");
        // 화면에서 숨긴 지역은 고를 수 없으므로 내보내지 않는다.
        assertThat(exported.at("/regions/overseas/0/children/0/children")).hasSize(1);
        // 음식점·카페는 등록폼처럼 두 유형의 합집합이다.
        assertThat(exported.at("/categoriesByType/CAFE").toString()).isEqualTo("[\"한식\",\"디저트\"]");
        assertThat(exported.at("/amenitiesByType/RESTAURANTS/0/code").asText()).isEqualTo("WIFI");
        assertThat(exported.at("/amenitiesByType/RESTAURANTS/0/name").asText()).isEqualTo("와이파이");
        assertThat(exported.at("/amenitiesByType/ATTRACTION")).isEmpty();
        // 필드 규격은 Validator 와 같은 값이다.
        JsonNode openingHours = exported.at("/contract/infoFields/RESTAURANTS/2");
        assertThat(openingHours.get("name").asText()).isEqualTo("openingHours");
        assertThat(openingHours.get("maxLength").asInt()).isEqualTo(64);
        assertThat(openingHours.get("fact").asBoolean()).isTrue();
        assertThat(exported.at("/contract/maxDestinations").asInt()).isEqualTo(30);
        // DB 번호는 계약값이 아니다.
        assertThat(exported.findValues("id")).isEmpty();
        assertThat(exported.findValues("regionId")).isEmpty();
        assertThat(exported.toString()).doesNotContain("\"235\"", ":235", ":55");
    }

    private void region(Long id, String name, String nameEn, Long parentId, int visible) {
        CountryCategory region = new CountryCategory();
        region.setId(id);
        region.setRegionName(name);
        region.setNameEn(nameEn);
        region.setParentId(parentId);
        region.setIsVisible(visible);
        all.add(region);
    }

    private static Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }
}
