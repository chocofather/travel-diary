package com.example.travlediary.controller.destination;

import com.example.travlediary.dto.DestinationDetailDto;
import com.example.travlediary.dto.DestinationDto;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.model.Destination;
import com.example.travlediary.security.CustomUserDetails;
import com.example.travlediary.service.category.CategoryService;
import com.example.travlediary.service.category.CountryCategoryService;
import com.example.travlediary.service.comment.DestinationCommentService;
import com.example.travlediary.service.destination.DestinationImageService;
import com.example.travlediary.service.destination.DestinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;
import jakarta.servlet.http.HttpServletRequest; // Spring Boot 3.x

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class DestinationController {

    /** 여행지 목록 기본 페이지 크기. 목록 카드가 4열이라 12개가 3줄로 떨어진다. */
    private static final String DEFAULT_PAGE_SIZE = "12";

    /** Maps Embed API 기본 확대 수준 */
    private static final int EMBED_MAP_ZOOM = 15;

    /** 해외 지도(Maps Embed API) 키. 환경변수 GOOGLE_MAPS_API_KEY 로만 주입한다. */
    @Value("${GOOGLE_MAPS_API_KEY:}")
    private String googleMapsApiKey;

    private final DestinationService destinationService;
    private final DestinationImageService destinationImageService;
    private final CategoryService categoryService;
    private final CountryCategoryService countryCategoryService;
    private final DestinationCommentService destinationCommentService;

    // 공통 리스트: type=domestic or overseas, region(도시, 국가 등) id
    @GetMapping("/destinations")
    public String destinationList(
            @RequestParam(value = "type", defaultValue = "domestic") String type,
            @RequestParam(value = "region", required = false) Long regionId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = DEFAULT_PAGE_SIZE) int size,
            @RequestParam(value = "sort", defaultValue = "default") String sort,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request,
            Model model) {

        Long userId = (userDetails != null) ? userDetails.getId() : null;
        int offset = (page - 1) * size;
        int totalCount;
        List<Destination> rawList;

        List<CountryCategory> cities;
        Long selectedCityId = null;
        List<CountryCategory> subregions = null;
        String selectedCityName = null;

        // [1] 상단 region 케러셀/서브카테고리 분기
        if ("domestic".equals(type)) {
            // 국내: rootId = 7
            final Long rootId = 7L;
            if (regionId == null) {
                cities = countryCategoryService.getSubregions(rootId, 3);
                //테스트
                System.out.println("=== 상단 cities 리스트 ===");
                for (CountryCategory c : cities) System.out.println(c.getRegionName() + " " + c.getId() + " depth=" + c.getDepth());
            } else {
                CountryCategory region = countryCategoryService.getById(regionId);
                if (region.getDepth() == 3) {
                    // 시/도 클릭 (서울 등)
                    cities = countryCategoryService.getSubregions(rootId, 3);

                    //테스트
                    System.out.println("=== 상단 cities 리스트 ===");
                    for (CountryCategory c : cities) System.out.println(c.getRegionName() + " " + c.getId() + " depth=" + c.getDepth());

                    selectedCityId = region.getId();
                    // 구/군(하위) 있는지 체크
                    subregions = countryCategoryService.getSubregions(region.getId(), 4);
                } else if (region.getDepth() == 4) {
                    // 구/군 클릭
                    CountryCategory parent = countryCategoryService.getById(region.getParentId());
                    cities = countryCategoryService.getSubregions(rootId, 3);
                    selectedCityId = parent.getId();
                    //테스트
                    System.out.println("=== 상단 cities 리스트 ===");
                    for (CountryCategory c : cities) System.out.println(c.getRegionName() + " " + c.getId() + " depth=" + c.getDepth());
                    // 구/군 형제들
                    subregions = countryCategoryService.getSubregions(parent.getId(), 4);
                } else {
                    cities = countryCategoryService.getSubregions(rootId, 3);
                    //테스트
                    System.out.println("=== 상단 cities 리스트 ===");
                    for (CountryCategory c : cities) System.out.println(c.getRegionName() + " " + c.getId() + " depth=" + c.getDepth());
                }
            }
        } else {
            // 해외
            if (regionId == null) {
                // 대륙 리스트 (대한민국 제외)
                List<Long> overseasRootIds = countryCategoryService.getOverseasRootIds();
                cities = overseasRootIds.stream()
                        .map(countryCategoryService::getById)
                        .toList();
                //테스트
                System.out.println("=== 상단 cities 리스트 ===");
                for (CountryCategory c : cities) System.out.println(c.getRegionName() + " " + c.getId() + " depth=" + c.getDepth());
            } else {
                CountryCategory region = countryCategoryService.getById(regionId);
                if (region.getDepth() == 1) {
                    // 대륙 클릭 → 국가 리스트
                    cities = countryCategoryService.getSubregions(regionId, 2);

                    // 테스트
                    System.out.println("=== 상단 cities 리스트 ===");
                    for (CountryCategory c : cities) System.out.println(c.getRegionName() + " " + c.getId() + " depth=" + c.getDepth());
                    selectedCityId = region.getId();
                    subregions = null;


                } else if (region.getDepth() == 2) {
                    // 국가 클릭
                    List<CountryCategory> childCities = countryCategoryService.getSubregions(regionId, 3);
                    if (!childCities.isEmpty()) {
                        // 하위 도시 있음: cities=형제국가, subregions=하위도시
                        cities = countryCategoryService.getSubregions(region.getParentId(), 2);

                        //테스트
                        System.out.println("=== 상단 cities 리스트 ===");
                        for (CountryCategory c : cities) System.out.println(c.getRegionName() + " " + c.getId() + " depth=" + c.getDepth());

                        selectedCityId = region.getId();
                        subregions = childCities;
                    } else {
                        // 하위 도시 없음: cities=형제국가, subregions=null
                        cities = countryCategoryService.getSubregions(region.getParentId(), 2);

                        //테스트
                        System.out.println("=== 상단 cities 리스트 ===");
                        for (CountryCategory c : cities) System.out.println(c.getRegionName() + " " + c.getId() + " depth=" + c.getDepth());

                        selectedCityId = region.getId();
                        subregions = null;
                    }
                } else if (region.getDepth() == 3) {
                    // 도시 클릭: cities=해당 국가 모든 도시, subregions=형제 도시들
                    CountryCategory parent = countryCategoryService.getById(region.getParentId());
                    cities = countryCategoryService.getSubregions(parent.getId(), 3);
                    //테스트
                    System.out.println("=== 상단 cities 리스트 ===");
                    for (CountryCategory c : cities) System.out.println(c.getRegionName() + " " + c.getId() + " depth=" + c.getDepth());

                    selectedCityId = region.getId();
                    subregions = countryCategoryService.getSubregions(parent.getId(), 3);
                } else {
                    cities = List.of();
                }
            }
        }

        model.addAttribute("cities", cities);
        model.addAttribute("type", type);
        model.addAttribute("selectedCityId", selectedCityId != null ? selectedCityId : regionId);

        // [2] subregions와 선택값 세팅(위에서 다 처리, 중복 없음)
        model.addAttribute("subregions", subregions);
        model.addAttribute("selectedSubregionId", regionId);

        // [3] 선택 지역명, 여행지 리스트
        if (regionId != null) {
            CountryCategory selectedCity = countryCategoryService.getById(regionId);
            selectedCityName = selectedCity.getRegionName();
            model.addAttribute("selectedCityName", selectedCityName);

            List<Long> regionIds = countryCategoryService.getAllRegionIdsUnder(regionId);
            rawList = destinationService.getDestinationsByRegionIdsPaged(regionIds, offset, size, sort);
            totalCount = destinationService.countDestinationsByRegionIds(regionIds);

        } else {
            List<Long> rootRegionIds = "overseas".equals(type)
                    ? countryCategoryService.getOverseasRootIds()
                    : countryCategoryService.getDomesticRootIds();
            List<Long> allRegionIds = rootRegionIds.stream()
                    .flatMap(id -> countryCategoryService.getAllRegionIdsUnder(id).stream())
                    .toList();
            rawList = destinationService.getDestinationsByRegionIdsPaged(allRegionIds, offset, size, sort);
            totalCount = destinationService.countDestinationsByRegionIds(allRegionIds);

            model.addAttribute("selectedCityName", null);
        }

        List<DestinationDto> destinations = destinationService.convertToDtoWithBookmark(rawList, userId);
        int totalPages = (int) Math.ceil((double) totalCount / size);

        model.addAttribute("destinations", destinations);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalCount", totalCount);



        return "destination/list";

    }



    // 여행지 상세 (국내/해외 구분 없이 동일)
    @GetMapping("/destinations/{id}")
    public String destinationDetail(@PathVariable Long id,
                                    @AuthenticationPrincipal CustomUserDetails userDetails,
                                    Model model) {
        // ✅ 조회수 증가
        destinationService.incrementViewCount(id);

        Long userId = (userDetails != null) ? userDetails.getId() : null;

        // 1. 여행지 + 타입별 상세 + amenity + 이미지 전부
        DestinationDetailDto dto = destinationService.getDestinationDetailWithInfo(id);
        model.addAttribute("destination", dto.getDestination());
        model.addAttribute("images", dto.getImages());

        // 소개글 줄바꿈 처리 (XSS 방지도 함께)
        String description = dto.getDestination().getDescription();
        if (description != null && !description.isBlank()) {
            String sanitized = HtmlUtils.htmlEscape(description).replace("\n", "<br>");
            model.addAttribute("descriptionWithBr", sanitized);
        } else {
            model.addAttribute("descriptionWithBr", "-");
        }

        // 2. 타입별 추가 정보
        if (dto.getAccommodationInfo() != null) {
            model.addAttribute("accommodationInfo", dto.getAccommodationInfo());
            model.addAttribute("accommodationAmenities", dto.getAccommodationAmenities());
        }
        if (dto.getRestaurantInfo() != null) {
            model.addAttribute("restaurantInfo", dto.getRestaurantInfo());
            model.addAttribute("restaurantAmenities", dto.getRestaurantAmenities());
        }
        if (dto.getAttractionInfo() != null) {
            model.addAttribute("attractionInfo", dto.getAttractionInfo());
            model.addAttribute("attractionAmenities", dto.getAttractionAmenities());
            String guide = dto.getAttractionInfo().getGuide();
            if (guide != null && !guide.isBlank()) {
                String sanitized = HtmlUtils.htmlEscape(guide).replace("\n", "<br>");
                model.addAttribute("attractionGuideWithBr", sanitized);
            } else {
                model.addAttribute("attractionGuideWithBr", "-");
            }
        }
        if (dto.getActivityInfo() != null) {
            model.addAttribute("activityInfo", dto.getActivityInfo());
            model.addAttribute("activityAmenities", dto.getActivityAmenities());
        }
        if (dto.getShopInfo() != null) {
            model.addAttribute("shopInfo", dto.getShopInfo());
            model.addAttribute("shopAmenities", dto.getShopAmenities());
        }

        // 지역/부모지역/카테고리/댓글/비슷한 여행지 등은 기존대로
        CountryCategory region = countryCategoryService.getById(dto.getDestination().getRegionId());
        model.addAttribute("regionName", region.getRegionName());
        model.addAttribute("regionId", region.getId());
        // 목록으로 돌아가는 링크(/destinations?type=..&region=..)가 쓰는 값.
        // 비어 있으면 목록이 지역 필터를 적용하지 못한다.
        model.addAttribute("type", resolveRegionType(region));

        String code = region.getCode(); // 또는 countryCategoryService.getCodeById(region.getId());
        String countryCode = code != null ? code.split("-")[0] : null;
        model.addAttribute("countryCode", countryCode);

        CountryCategory parentRegion = null;
        if (region.getParentId() != null) {
            parentRegion = countryCategoryService.getById(region.getParentId());
            model.addAttribute("regionPath", parentRegion.getRegionName());
            model.addAttribute("regionPathId", parentRegion.getId());
        } else {
            model.addAttribute("regionPath", null);
            model.addAttribute("regionPathId", null);
        }

        // 해외 지도는 Maps Embed API iframe 으로 표시한다. 만들 수 없으면 null → 지도 영역만 감춘다.
        model.addAttribute("overseasMapEmbedUrl",
                buildOverseasMapEmbedUrl(countryCode, dto.getDestination()));
        model.addAttribute("overseasMapLinkUrl",
                buildOverseasMapLinkUrl(countryCode, dto.getDestination()));

        String categoryName = categoryService.getFirstCategoryNameByDestinationId(id);
        model.addAttribute("categoryName", categoryName);

        int commentCount = destinationCommentService.getCommentCountByDestinationId(id);
        model.addAttribute("commentCount", commentCount);

        List<Destination> similarEntities = destinationService.getSimilarDestinations(id, 4);
        List<DestinationDto> similarDtos = destinationService.convertToDtoWithBookmark(similarEntities, userId);
        model.addAttribute("similarDestinations", similarDtos);

        return "destination/detail";
    }

    /**
     * 해외 여행지의 Maps Embed API URL. 아래 중 하나라도 없으면 null 을 반환해
     * 잘못된 iframe 대신 지도 영역을 감추게 한다.
     * - 해외 여행지가 아님(국내는 Kakao 지도 유지)
     * - GOOGLE_MAPS_API_KEY 환경변수 미설정
     * - 좌표 미입력
     */
    private String buildOverseasMapEmbedUrl(String countryCode, Destination destination) {
        if (googleMapsApiKey == null || googleMapsApiKey.isBlank()) {
            return null;
        }
        if (countryCode == null || "KR".equals(countryCode)) {
            return null;
        }
        String key = URLEncoder.encode(googleMapsApiKey, StandardCharsets.UTF_8);
        String position = overseasPosition(countryCode, destination);
        String placeId = trimmedPlaceId(destination);

        // 1) Place ID 가 있으면 place 모드로 해당 장소를 정확히 지정한다. (마커 표시)
        //    텍스트 검색은 다른 장소가 선택될 수 있어 쓰지 않는다.
        if (placeId != null) {
            String url = "https://www.google.com/maps/embed/v1/place"
                    + "?key=" + key
                    + "&q=" + URLEncoder.encode("place_id:" + placeId, StandardCharsets.UTF_8);
            if (position != null) {
                url += "&center=" + URLEncoder.encode(position, StandardCharsets.UTF_8)
                        + "&zoom=" + EMBED_MAP_ZOOM;
            }
            return url;
        }

        // 2) Place ID 가 없으면 장소를 추측하지 않고 좌표 중심 view 모드만 보여준다.
        if (position == null) {
            return null;
        }
        return "https://www.google.com/maps/embed/v1/view"
                + "?key=" + key
                + "&center=" + URLEncoder.encode(position, StandardCharsets.UTF_8)
                + "&zoom=" + EMBED_MAP_ZOOM;
    }

    /** 입력되지 않았거나 공백뿐이면 null. */
    private String trimmedPlaceId(Destination destination) {
        String placeId = destination.getGooglePlaceId();
        if (placeId == null || placeId.isBlank()) {
            return null;
        }
        return placeId.trim();
    }

    /** "Google 지도에서 크게 보기" 링크. API 키 없이도 열 수 있어 좌표만 있으면 만든다. */
    private String buildOverseasMapLinkUrl(String countryCode, Destination destination) {
        String position = overseasPosition(countryCode, destination);
        if (position == null) {
            return null;
        }
        String url = "https://www.google.com/maps/search/?api=1"
                + "&query=" + URLEncoder.encode(position, StandardCharsets.UTF_8);
        // Place ID 가 있으면 같은 장소로 정확히 열리게 한다. (query 는 필수라 좌표를 유지)
        String placeId = trimmedPlaceId(destination);
        if (placeId != null) {
            url += "&query_place_id=" + URLEncoder.encode(placeId, StandardCharsets.UTF_8);
        }
        return url;
    }

    /** 해외 여행지의 "위도,경도" 문자열. 국내이거나 좌표가 없으면 null. */
    private String overseasPosition(String countryCode, Destination destination) {
        if (countryCode == null || "KR".equals(countryCode)) {
            return null;
        }
        BigDecimal latitude = destination.getLatitude();
        BigDecimal longitude = destination.getLongitude();
        if (latitude == null || longitude == null) {
            return null;
        }
        return latitude.toPlainString() + "," + longitude.toPlainString();
    }

    /**
     * 지역이 속한 최상위 루트까지 거슬러 올라가 목록의 type 값(domestic/overseas)을 정한다.
     * 지역 ID 는 하드코딩하지 않고 CountryCategoryService 로 판별한다.
     */
    private String resolveRegionType(CountryCategory region) {
        CountryCategory root = region;
        while (root != null && root.getParentId() != null) {
            root = countryCategoryService.getById(root.getParentId());
        }
        if (root == null) {
            return "domestic";
        }
        return countryCategoryService.getDomesticRootIds().contains(root.getId())
                ? "domestic"
                : "overseas";
    }

    @GetMapping("/destinations/fragment")
    public String regionFragment(
            @RequestParam(value = "type", defaultValue = "domestic") String type,
            @RequestParam(value = "region", required = false) Long regionId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = DEFAULT_PAGE_SIZE) int size,
            @RequestParam(value = "sort", defaultValue = "default") String sort,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        Long userId = (userDetails != null) ? userDetails.getId() : null;
        int offset = (page - 1) * size;
        int totalCount;
        List<Destination> rawList;

        List<CountryCategory> cities = null;
        List<CountryCategory> subregions = null;
        Long selectedSubregionId = null;
        Long selectedCityId = null;
        String selectedCityName = null;

        if ("domestic".equals(type)) {
            final Long rootId = 7L;
            if (regionId == null) {
                cities = countryCategoryService.getSubregions(rootId, 3);
            } else {
                CountryCategory region = countryCategoryService.getById(regionId);
                if (region.getDepth() == 3) {
                    cities = countryCategoryService.getSubregions(rootId, 3);
                    selectedCityId = region.getId();
                    subregions = countryCategoryService.getSubregions(region.getId(), 4);
                } else if (region.getDepth() == 4) {
                    CountryCategory parent = countryCategoryService.getById(region.getParentId());
                    cities = countryCategoryService.getSubregions(rootId, 3);
                    selectedCityId = parent.getId();
                    subregions = countryCategoryService.getSubregions(parent.getId(), 4);
                    selectedSubregionId = regionId;
                } else {
                    cities = countryCategoryService.getSubregions(rootId, 3);
                }
            }
        } else { // overseas
            if (regionId == null) {
                List<Long> overseasRootIds = countryCategoryService.getOverseasRootIds();
                cities = overseasRootIds.stream()
                        .map(countryCategoryService::getById)
                        .toList();
            } else {
                CountryCategory region = countryCategoryService.getById(regionId);
                if (region.getDepth() == 1) {
                    cities = countryCategoryService.getSubregions(regionId, 2);
                    selectedCityId = region.getId();
                } else if (region.getDepth() == 2) {
                    List<CountryCategory> childCities = countryCategoryService.getSubregions(regionId, 3);
                    if (!childCities.isEmpty()) {
                        cities = countryCategoryService.getSubregions(region.getParentId(), 2);
                        selectedCityId = region.getId();
                        subregions = childCities;
                    } else {
                        cities = countryCategoryService.getSubregions(region.getParentId(), 2);
                        selectedCityId = region.getId();
                        subregions = null;
                    }
                } else if (region.getDepth() == 3) {
                    CountryCategory parent = countryCategoryService.getById(region.getParentId());
                    cities = countryCategoryService.getSubregions(parent.getId(), 3);
                    selectedCityId = region.getId();
                    subregions = countryCategoryService.getSubregions(parent.getId(), 3);
                }
            }
        }

        // 여행지 리스트 추출 (공통)
        List<Long> regionIds;
        if (regionId != null) {
            regionIds = countryCategoryService.getAllRegionIdsUnder(regionId);
            CountryCategory selectedRegion = countryCategoryService.getById(regionId);
            selectedCityName = selectedRegion.getRegionName();
        } else {
            List<Long> rootRegionIds = "overseas".equals(type)
                    ? countryCategoryService.getOverseasRootIds()
                    : countryCategoryService.getDomesticRootIds();
            regionIds = rootRegionIds.stream()
                    .flatMap(id -> countryCategoryService.getAllRegionIdsUnder(id).stream())
                    .toList();
            selectedCityName = null;
        }
        rawList = destinationService.getDestinationsByRegionIdsPaged(regionIds, offset, size, sort);
        totalCount = destinationService.countDestinationsByRegionIds(regionIds);

        List<DestinationDto> destinations = destinationService.convertToDtoWithBookmark(rawList, userId);
        int totalPages = (int) Math.ceil((double) totalCount / size);

        model.addAttribute("cities", cities);
        model.addAttribute("destinations", destinations);
        model.addAttribute("subregions", subregions);
        model.addAttribute("selectedSubregionId", selectedSubregionId);
        model.addAttribute("selectedCityId", selectedCityId);
        model.addAttribute("selectedCityName", selectedCityName);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("sort", sort);
        model.addAttribute("type", type);

        // ★ regionFragment를 리턴 (region-bar + 리스트 + subregion 전체)
        return "destination/fragment :: regionFragment";
    }


    @GetMapping("/destinations/list-fragment")
    public String destinationListFragment(
            @RequestParam(value = "type", defaultValue = "domestic") String type,
            @RequestParam(value = "region", required = false) Long regionId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = DEFAULT_PAGE_SIZE) int size,
            @RequestParam(value = "sort", defaultValue = "default") String sort,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        Long userId = (userDetails != null) ? userDetails.getId() : null;
        int offset = (page - 1) * size;
        int totalCount;
        List<Destination> rawList;

        List<CountryCategory> subregions = null;
        Long selectedSubregionId = null;
        Long selectedCityId = null;
        String selectedCityName = null;

        if ("domestic".equals(type)) {
            final Long rootId = 7L;
            if (regionId != null) {
                CountryCategory region = countryCategoryService.getById(regionId);
                if (region.getDepth() == 3) {
                    selectedCityId = region.getId();
                    subregions = countryCategoryService.getSubregions(region.getId(), 4);
                } else if (region.getDepth() == 4) {
                    CountryCategory parent = countryCategoryService.getById(region.getParentId());
                    selectedCityId = parent.getId();
                    subregions = countryCategoryService.getSubregions(parent.getId(), 4);
                    selectedSubregionId = regionId;
                }
            }
        } else { // overseas
            if (regionId != null) {
                CountryCategory region = countryCategoryService.getById(regionId);
                if (region.getDepth() == 2) {
                    List<CountryCategory> childCities = countryCategoryService.getSubregions(regionId, 3);
                    if (!childCities.isEmpty()) {
                        selectedCityId = region.getId();
                        subregions = childCities;
                    }
                } else if (region.getDepth() == 3) {
                    CountryCategory parent = countryCategoryService.getById(region.getParentId());
                    selectedCityId = region.getId();
                    subregions = countryCategoryService.getSubregions(parent.getId(), 3);
                }
            }
        }

        // 여행지 리스트 추출 (공통)
        List<Long> regionIds;
        if (regionId != null) {
            regionIds = countryCategoryService.getAllRegionIdsUnder(regionId);
            CountryCategory selectedRegion = countryCategoryService.getById(regionId);
            selectedCityName = selectedRegion.getRegionName();
        } else {
            List<Long> rootRegionIds = "overseas".equals(type)
                    ? countryCategoryService.getOverseasRootIds()
                    : countryCategoryService.getDomesticRootIds();
            regionIds = rootRegionIds.stream()
                    .flatMap(id -> countryCategoryService.getAllRegionIdsUnder(id).stream())
                    .toList();
            selectedCityName = null;
        }
        rawList = destinationService.getDestinationsByRegionIdsPaged(regionIds, offset, size, sort);
        totalCount = destinationService.countDestinationsByRegionIds(regionIds);

        List<DestinationDto> destinations = destinationService.convertToDtoWithBookmark(rawList, userId);
        int totalPages = (int) Math.ceil((double) totalCount / size);

        // cities 필요 없음!
        model.addAttribute("destinations", destinations);
        model.addAttribute("subregions", subregions);
        model.addAttribute("selectedSubregionId", selectedSubregionId);
        model.addAttribute("selectedCityId", selectedCityId);
        model.addAttribute("selectedCityName", selectedCityName);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("sort", sort);
        model.addAttribute("type", type);

        // ★ destinationList만 리턴 (region-bar 없음)
        return "destination/fragment :: destinationList";
    }
}