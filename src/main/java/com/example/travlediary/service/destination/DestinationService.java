package com.example.travlediary.service.destination;

import com.example.travlediary.dto.DestinationDetailDto;
import com.example.travlediary.dto.DestinationDto;
import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.dto.DestinationTranslationForm;
import com.example.travlediary.model.BookmarkTargetType;
import com.example.travlediary.model.Destination;
import com.example.travlediary.model.DestinationImage;
import com.example.travlediary.model.DestinationSeason;
import com.example.travlediary.model.DestinationTranslation;
import com.example.travlediary.repository.bookmark.BookmarkMapper;
import com.example.travlediary.repository.destination.DestinationMapper;
import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.comment.DestinationCommentService;
import com.example.travlediary.service.info.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DestinationService {

    private final DestinationMapper destinationMapper;
    private final DestinationImageService destinationImageService;
    private final BookmarkMapper bookmarkMapper;
    private final AmenityService amenityService;
    private final DestinationCommentService destinationCommentService;


    // 추가 정보
    private final AccommodationInfoService accommodationInfoService;
    private final AttractionInfoService attractionInfoService;
    private final RestaurantInfoService restaurantInfoService;
    private final ActivityInfoService activityInfoService;
    private final ShopInfoService shopInfoService;

    @Value("${custom.upload-path}")
    private String uploadPath;


    public Long registerDestination(DestinationForm form, Long userId) {
        Destination destination = new Destination();
        destination.setLatitude(form.getLatitude());
        destination.setLongitude(form.getLongitude());
        destination.setGooglePlaceId(form.getGooglePlaceId());
        destination.setSeason(DestinationSeason.valueOf(form.getSeason()));
        destination.setUserID(userId);
        destination.setViews(0);
        destination.setRegionId(form.getRegionId());

        destination.setCreatedAt(new java.sql.Timestamp(System.currentTimeMillis()));

        destination.setType(form.getType());

        destinationMapper.insertDestination(destination);
        Long destinationId = destination.getId();

        switch (form.getType()) {
            case ATTRACTION:
                if (form.getAttractionInfo() != null) {
                    form.getAttractionInfo().setDestinationId(destinationId);
                    attractionInfoService.save(form.getAttractionInfo());
                }
                break;
            case ACCOMMODATION:
                if (form.getAccommodationInfo() != null) {
                    form.getAccommodationInfo().setDestinationId(destinationId);
                    accommodationInfoService.save(form.getAccommodationInfo());
                }
                break;
            case RESTAURANTS:
            case CAFE:
                if (form.getRestaurantInfo() != null) {
                    form.getRestaurantInfo().setDestinationId(destinationId);
                    restaurantInfoService.save(form.getRestaurantInfo());
                }
                break;
            case ACTIVITY:
                if (form.getActivityInfo() != null) {
                    form.getActivityInfo().setDestinationId(destinationId);
                    activityInfoService.save(form.getActivityInfo());
                }
                break;
            case SHOP:
                if (form.getShopInfo() != null) {
                    form.getShopInfo().setDestinationId(destinationId);
                    shopInfoService.save(form.getShopInfo());
                }
                break;
        }


        for (DestinationTranslationForm transForm : form.getTranslations()) {
            DestinationTranslation trans = new DestinationTranslation();
            trans.setDestinationId(destinationId);
            trans.setLanguageCode(transForm.getLanguageCode());
            trans.setName(transForm.getName());
            trans.setDescription(transForm.getDescription());
            trans.setShortDescription(transForm.getShortDescription());
            destinationMapper.insertTranslation(trans);
        }

        for (Long categoryId : form.getCategoryIds()) {
            destinationMapper.insertDestinationCategory(destinationId, categoryId);
        }
        // === 편의시설 체크박스 값 저장 ===
        switch (form.getType()) {
            case ATTRACTION:
                amenityService.insertAttractionAmenities(destinationId, form.getAttractionAmenityIds());
                break;
            case ACCOMMODATION:
                amenityService.insertAccommodationAmenities(destinationId, form.getAccommodationAmenityIds());
                break;
            case RESTAURANTS:
            case CAFE:
                amenityService.insertRestaurantAmenities(destinationId, form.getRestaurantAmenityIds());
                break;
            case ACTIVITY:
                amenityService.insertActivityAmenities(destinationId, form.getActivityAmenityIds());
                break;
            case SHOP:
                amenityService.insertShopAmenities(destinationId, form.getShopAmenityIds());
                break;
        }

        destinationImageService.saveImages(
                destinationId, form.getImages(), form.isMain(), form.isSlide());
        return destinationId;
    }

    public List<Destination> getDomesticDestinations() {
        return destinationMapper.findDomestic();
    }

    public List<Destination> getAllDestinationsWithRegion() {
        return destinationMapper.findAllWithRegion();
    }

    public List<String> getImageUrlsByDestinationId(Long id) {
        List<DestinationImage> images = destinationMapper.findImagesByDestinationId(id);
        return images.stream()
                .map(DestinationImage::getImageUrl)
                .collect(Collectors.toList());
    }

    public DestinationDetailDto getDestinationDetailWithInfo(Long id) {
        Destination destination = destinationMapper.findDestinationDetail(id);
        if (destination == null) return null;

        DestinationDetailDto dto = new DestinationDetailDto();
        dto.setDestination(destination);

        // 이미지 리스트
        dto.setImages(destinationMapper.findImagesByDestinationId(id));
        dto.setCategoryIds(destinationMapper.findCategoryIdsByDestinationId(id));

        // 타입별 상세정보 및 amenity 리스트 셋팅
        switch (destination.getType()) {
            case ATTRACTION:
                dto.setAttractionInfo(attractionInfoService.findByDestinationId(id));
                dto.setAttractionAmenities(amenityService.getAttractionAmenities(id));
                break;
            case ACCOMMODATION:
                dto.setAccommodationInfo(accommodationInfoService.findByDestinationId(id));
                dto.setAccommodationAmenities(amenityService.getAccommodationAmenities(id));
                break;
            case RESTAURANTS:
            case CAFE:
                dto.setRestaurantInfo(restaurantInfoService.findByDestinationId(id));
                dto.setRestaurantAmenities(amenityService.getRestaurantAmenities(id));
                break;
            case ACTIVITY:
                dto.setActivityInfo(activityInfoService.findByDestinationId(id));
                dto.setActivityAmenities(amenityService.getActivityAmenities(id));
                break;
            case SHOP:
                dto.setShopInfo(shopInfoService.findByDestinationId(id));
                dto.setShopAmenities(amenityService.getShopAmenities(id));
                break;
        }
        return dto;
    }

    // 상세 페이지 여행지 추천
    public List<Destination> getSimilarDestinations(Long destinationId, int limit) {
        Destination destination = destinationMapper.findById(destinationId);
        if (destination == null) return Collections.emptyList();

        Long regionId = destination.getRegionId();
        List<Long> categoryIds = destinationMapper.findCategoryIdsByDestinationId(destinationId);

        if (categoryIds == null || categoryIds.isEmpty()) return Collections.emptyList();

        return destinationMapper.findSimilarDestinations(regionId, categoryIds, destinationId, limit);
    }




    public List<Destination> findAll() {
        return destinationMapper.findAllWithRegion();
    }

    public List<Destination> findAllWithRegion() {
        return destinationMapper.findAllWithRegion();
    }

    public Destination findById(Long id) {
        return destinationMapper.findById(id);
    }

    // ✅ 추가: 이미지 리스트만 따로 불러올 수 있도록
    public List<DestinationImage> getImagesByDestinationId(Long id) {
        return destinationMapper.findImagesByDestinationId(id);
    }

    public List<Destination> getDestinationsByCityId(Long cityId) {
        return destinationMapper.findByCountryCategoryId(cityId);
    }

    public List<Destination> getDestinationsByRegionIds(List<Long> regionIds) {
        return destinationMapper.findByRegionIds(regionIds);
    }


    // 조회수 증가
    public void incrementViewCount(Long id) {
        destinationMapper.incrementViewCount(id);
    }

    // 여행지 삭제

    @Transactional
    public void deleteById(Long id) {
        List<DestinationImage> images = destinationMapper.findImagesByDestinationId(id);
        List<String> imageUrls = images == null
                ? List.of()
                : images.stream()
                .map(DestinationImage::getImageUrl)
                .toList();
        // 댓글 row 가 지워지기 전에 정리 대상 사진 URL 을 모아 둔다
        List<String> commentImageUrls = destinationCommentService.findAllCommentImageUrls(id);

        // 연관 테이블 먼저 삭제
        // 북마크는 target_type/target_id 구조라 FK cascade 대상이 아니므로 직접 지운다
        bookmarkMapper.deleteByTarget(BookmarkTargetType.DESTINATION.name(), id);
        destinationMapper.deleteImagesByDestinationId(id);
        destinationMapper.deleteTranslationsByDestinationId(id);
        destinationMapper.deleteCommentsByDestinationId(id);
        destinationMapper.deleteDestinationCategoriesByDestinationId(id); // 이 줄 추가됨

        // 마지막으로 본체 삭제
        destinationMapper.deleteById(id);
        destinationImageService.deleteFilesAfterCommit(imageUrls);
        destinationCommentService.deleteImageFilesAfterCommit(commentImageUrls);
    }

    public List<DestinationDto> convertToDtoWithBookmark(List<Destination> destinations, Long userId) {
        Set<Long> bookmarkedIds = (userId != null)
                ? bookmarkMapper.findBookmarkedTargetIdsByUserId(userId, "DESTINATION")
                : Collections.emptySet();

        //  여행지 ID 목록 뽑기
        List<Long> ids = destinations.stream().map(Destination::getId).toList();
        // 여행지별 댓글 수를 한 번에 조회
        Map<Long, Integer> commentCountMap = destinationCommentService.countCommentsByDestinationIds(ids);

        return destinations.stream()
                .map(dest -> {
                    DestinationDto dto = new DestinationDto();
                    dto.setId(dest.getId());
                    dto.setName(dest.getName());
                    dto.setThumbnailPath(dest.getThumbnailPath());
                    dto.setRegionName(dest.getRegionName());
                    dto.setShortDescription(dest.getShortDescription());
                    dto.setBookmarked(bookmarkedIds.contains(dest.getId()));

                    dto.setCommentCount(commentCountMap.getOrDefault(dest.getId(), 0));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateDestination(Long destinationId, DestinationForm form) {
        // 1. 기본 정보 update
        Destination destination = destinationMapper.findById(destinationId);
        destination.setLatitude(form.getLatitude());
        destination.setLongitude(form.getLongitude());
        destination.setGooglePlaceId(form.getGooglePlaceId());
        destination.setSeason(DestinationSeason.valueOf(form.getSeason()));
        destination.setRegionId(form.getRegionId());
        destination.setType(form.getType());
        destinationMapper.updateDestination(destination);

        // 2. 타입별 상세 정보 update (각 InfoService에 update 메서드 필요)
        switch (form.getType()) {
            case ATTRACTION:
                if (form.getAttractionInfo() != null) {
                    form.getAttractionInfo().setDestinationId(destinationId);
                    attractionInfoService.update(form.getAttractionInfo());
                }
                break;
            case ACCOMMODATION:
                if (form.getAccommodationInfo() != null) {
                    form.getAccommodationInfo().setDestinationId(destinationId);
                    accommodationInfoService.update(form.getAccommodationInfo());
                }
                break;
            case RESTAURANTS:
            case CAFE:
                if (form.getRestaurantInfo() != null) {
                    form.getRestaurantInfo().setDestinationId(destinationId);
                    restaurantInfoService.update(form.getRestaurantInfo());
                }
                break;
            case ACTIVITY:
                if (form.getActivityInfo() != null) {
                    form.getActivityInfo().setDestinationId(destinationId);
                    activityInfoService.update(form.getActivityInfo());
                }
                break;
            case SHOP:
                if (form.getShopInfo() != null) {
                    form.getShopInfo().setDestinationId(destinationId);
                    shopInfoService.update(form.getShopInfo());
                }
                break;
        }

        // 3. 번역(Translation) update or insert
        List<DestinationTranslation> existingTranslations = destinationMapper.findTranslationsByDestinationId(destinationId);
        for (DestinationTranslationForm transForm : form.getTranslations()) {
            DestinationTranslation match = existingTranslations.stream()
                    .filter(t -> t.getLanguageCode().equals(transForm.getLanguageCode()))
                    .findFirst().orElse(null);

            if (match != null) {
                // UPDATE
                match.setName(transForm.getName());
                match.setDescription(transForm.getDescription());
                match.setShortDescription(transForm.getShortDescription());
                destinationMapper.updateTranslation(match);
            } else {
                // INSERT
                DestinationTranslation trans = new DestinationTranslation();
                trans.setDestinationId(destinationId);
                trans.setLanguageCode(transForm.getLanguageCode());
                trans.setName(transForm.getName());
                trans.setDescription(transForm.getDescription());
                trans.setShortDescription(transForm.getShortDescription());
                destinationMapper.insertTranslation(trans);
            }
        }

        // 4. 카테고리 부분 수정(차집합 기반)
        List<Long> beforeIds = destinationMapper.findCategoryIdsByDestinationId(destinationId);
        List<Long> afterIds = form.getCategoryIds();

        for (Long id : afterIds) {
            if (!beforeIds.contains(id)) {
                destinationMapper.insertDestinationCategory(destinationId, id);
            }
        }
        for (Long id : beforeIds) {
            if (!afterIds.contains(id)) {
                destinationMapper.deleteDestinationCategory(destinationId, id);
            }
        }

        // 5. 편의시설(amenity) 부분도 위와 동일(amenityService에서 비교 후 갱신)
        switch (form.getType()) {
            case ATTRACTION:
                amenityService.updateAttractionAmenities(destinationId, form.getAttractionAmenityIds());
                break;
            case ACCOMMODATION:
                amenityService.updateAccommodationAmenities(destinationId, form.getAccommodationAmenityIds());
                break;
            case RESTAURANTS:
            case CAFE:
                amenityService.updateRestaurantAmenities(destinationId, form.getRestaurantAmenityIds());
                break;
            case ACTIVITY:
                amenityService.updateActivityAmenities(destinationId, form.getActivityAmenityIds());
                break;
            case SHOP:
                amenityService.updateShopAmenities(destinationId, form.getShopAmenityIds());
                break;
        }
    }

    public List<DestinationTranslation> getTranslationsByDestinationId(Long destinationId) {
        return destinationMapper.findTranslationsByDestinationId(destinationId);
    }

    // 페이징
    // 국내 전체 여행지 페이징 (대한민국=7 기준)
    public List<Destination> getDomesticDestinationsPaged(int offset, int size, String sort) {
        return destinationMapper.findDomesticPaged(offset, size, sort);
    }
    public int countDomesticDestinations() {
        return destinationMapper.countDomestic();
    }

    //  (공통) 특정 루트 지역(rootRegionId) 이하 전체(계층 포함) 페이징
    public List<Destination> getDestinationsByRootRegionPaged(Long rootRegionId, int offset, int size, String sort) {
        List<Long> regionIds = destinationMapper.findAllRegionIdsUnder(rootRegionId);
        return regionIds.isEmpty()
                ? Collections.emptyList()
                : destinationMapper.findByRegionIdsPaged(regionIds, offset, size, sort);
    }

    //  (공통) 특정 루트 지역(rootRegionId) 이하 전체(계층 포함) 카운트
    public int countDestinationsByRootRegion(Long rootRegionId) {
        List<Long> regionIds = destinationMapper.findAllRegionIdsUnder(rootRegionId);
        return regionIds.isEmpty()
                ? 0
                : destinationMapper.countByRegionIds(regionIds);
    }


    // 지역별 여행지 페이징
    public List<Destination> getDestinationsByRegionIdsPaged(List<Long> regionIds, int offset, int size, String sort) {
        return destinationMapper.findByRegionIdsPaged(regionIds, offset, size, sort);
    }
    public int countDestinationsByRegionIds(List<Long> regionIds) {
        return destinationMapper.countByRegionIds(regionIds);
    }




}
