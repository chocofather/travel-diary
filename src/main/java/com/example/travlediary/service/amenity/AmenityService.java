package com.example.travlediary.service.amenity;

import com.example.travlediary.dto.AmenityDto;
import com.example.travlediary.model.Amenity;
import com.example.travlediary.model.AmenityDestinationType;
import com.example.travlediary.model.AmenityTranslation;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.repository.amenity.AmenityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AmenityService {
    private final AmenityMapper amenityMapper;

    // --- 공통: lang 없으면 "ko" (한국어) 기본 ---
    private static final String DEFAULT_LANG = "ko";

    // 관광지용 Amenity DTO 반환
    public List<AmenityDto> getAttractionAmenities(Long destinationId) {
        return getAttractionAmenities(destinationId, DEFAULT_LANG);
    }
    public List<AmenityDto> getAttractionAmenities(Long destinationId, String lang) {
        List<AmenityTranslation> list = amenityMapper.findAttractionAmenityTranslationsByDestinationId(destinationId, lang);
        return list.stream().map(tr -> {
            AmenityDto dto = new AmenityDto();
            dto.setId(tr.getAmenityId());
            dto.setCode(tr.getCode());
            dto.setName(tr.getName());
            return dto;
        }).collect(Collectors.toList());
    }

    public List<AmenityDto> getAccommodationAmenities(Long destinationId) {
        return getAccommodationAmenities(destinationId, DEFAULT_LANG);
    }
    public List<AmenityDto> getAccommodationAmenities(Long destinationId, String lang) {
        List<AmenityTranslation> list = amenityMapper.findAccommodationAmenityTranslationsByDestinationId(destinationId, lang);
        return list.stream().map(tr -> {
            AmenityDto dto = new AmenityDto();
            dto.setId(tr.getAmenityId());
            dto.setCode(tr.getCode());
            dto.setName(tr.getName());
            return dto;
        }).collect(Collectors.toList());
    }

    public List<AmenityDto> getRestaurantAmenities(Long destinationId) {
        return getRestaurantAmenities(destinationId, DEFAULT_LANG);
    }
    public List<AmenityDto> getRestaurantAmenities(Long destinationId, String lang) {
        List<AmenityTranslation> list = amenityMapper.findRestaurantAmenityTranslationsByDestinationId(destinationId, lang);
        return list.stream().map(tr -> {
            AmenityDto dto = new AmenityDto();
            dto.setId(tr.getAmenityId());
            dto.setCode(tr.getCode());
            dto.setName(tr.getName());
            return dto;
        }).collect(Collectors.toList());
    }

    public List<AmenityDto> getActivityAmenities(Long destinationId) {
        return getActivityAmenities(destinationId, DEFAULT_LANG);
    }
    public List<AmenityDto> getActivityAmenities(Long destinationId, String lang) {
        List<AmenityTranslation> list = amenityMapper.findActivityAmenityTranslationsByDestinationId(destinationId, lang);
        return list.stream().map(tr -> {
            AmenityDto dto = new AmenityDto();
            dto.setId(tr.getAmenityId());
            dto.setCode(tr.getCode());
            dto.setName(tr.getName());
            return dto;
        }).collect(Collectors.toList());
    }

    public List<AmenityDto> getShopAmenities(Long destinationId) {
        return getShopAmenities(destinationId, DEFAULT_LANG);
    }
    public List<AmenityDto> getShopAmenities(Long destinationId, String lang) {
        List<AmenityTranslation> list = amenityMapper.findShopAmenityTranslationsByDestinationId(destinationId, lang);
        return list.stream().map(tr -> {
            AmenityDto dto = new AmenityDto();
            dto.setId(tr.getAmenityId());
            dto.setCode(tr.getCode());
            dto.setName(tr.getName());
            return dto;
        }).collect(Collectors.toList());
    }

    // --- 언어별 전체 (예: "ko", "en") ---
    public List<AmenityTranslation> getAllAmenityTranslations(String languageCode) {
        return amenityMapper.findTranslationsByLang(languageCode);
    }

    public List<AmenityTranslation> getAmenityTranslationsByType(String type, String languageCode) {
        return amenityMapper.findTranslationsByTypeAndLang(type, languageCode);
    }

    /**
     * 편의시설별 "적용 가능한 여행지 유형" 태그 (예: 1 -> "ATTRACTION CAFE").
     * 화면 필터가 이름이 아니라 이 매핑만 보고 동작하도록 서버에서 만들어 준다.
     * 매핑이 없는 편의시설은 키 자체가 없으며 [전체] 에서만 보인다.
     */
    public Map<Integer, String> getAmenityDestinationTypeTags() {
        List<AmenityDestinationType> mappings = amenityMapper.findAmenityDestinationTypes();
        if (mappings == null || mappings.isEmpty()) {
            return Map.of();
        }

        Map<Integer, StringBuilder> tags = new LinkedHashMap<>();
        for (AmenityDestinationType mapping : mappings) {
            if (mapping == null || mapping.getAmenityId() == null
                    || mapping.getDestinationType() == null || mapping.getDestinationType().isBlank()) {
                continue;
            }
            StringBuilder tag = tags.computeIfAbsent(mapping.getAmenityId(), id -> new StringBuilder());
            if (!tag.isEmpty()) {
                tag.append(' ');
            }
            tag.append(mapping.getDestinationType());
        }

        Map<Integer, String> result = new LinkedHashMap<>();
        tags.forEach((amenityId, tag) -> result.put(amenityId, tag.toString()));
        return result;
    }

    /** 해당 여행지 유형에 적용 가능한 편의시설만 (amenity_destination_types 매핑 기준). */
    public List<AmenityTranslation> getAmenityTranslationsByDestinationType(DestinationType type,
                                                                           String languageCode) {
        List<AmenityTranslation> translations =
                amenityMapper.findTranslationsByDestinationTypeAndLang(type.name(), languageCode);
        return translations == null ? List.of() : translations;
    }

    /**
     * 여러 유형을 한 화면에서 함께 쓰는 경우(음식점/카페처럼)의 합집합.
     * 같은 편의시설이 여러 유형에 매핑돼 있어도 한 번만 담는다.
     */
    public List<AmenityTranslation> getAmenityTranslationsByDestinationTypes(String languageCode,
                                                                            DestinationType... types) {
        Map<Integer, AmenityTranslation> merged = new LinkedHashMap<>();
        for (DestinationType type : types) {
            for (AmenityTranslation translation : getAmenityTranslationsByDestinationType(type, languageCode)) {
                if (translation != null) {
                    merged.putIfAbsent(translation.getAmenityId(), translation);
                }
            }
        }
        return List.copyOf(merged.values());
    }

    // --- 등록용(그대로 유지) ---
    @Transactional
    public void insertAttractionAmenities(Long attractionId, List<Integer> amenityIds) {
        if (amenityIds != null) {
            for (Integer amenityId : amenityIds) {
                amenityMapper.insertAttractionAmenity(attractionId, amenityId);
            }
        }
    }
    @Transactional
    public void insertAccommodationAmenities(Long accommodationId, List<Integer> amenityIds) {
        if (amenityIds != null) {
            for (Integer amenityId : amenityIds) {
                amenityMapper.insertAccommodationAmenity(accommodationId, amenityId);
            }
        }
    }
    @Transactional
    public void insertRestaurantAmenities(Long restaurantId, List<Integer> amenityIds) {
        if (amenityIds != null) {
            for (Integer amenityId : amenityIds) {
                amenityMapper.insertRestaurantAmenity(restaurantId, amenityId);
            }
        }
    }
    @Transactional
    public void insertActivityAmenities(Long activityId, List<Integer> amenityIds) {
        if (amenityIds != null) {
            for (Integer amenityId : amenityIds) {
                amenityMapper.insertActivityAmenity(activityId, amenityId);
            }
        }
    }
    @Transactional
    public void insertShopAmenities(Long shopId, List<Integer> amenityIds) {
        if (amenityIds != null) {
            for (Integer amenityId : amenityIds) {
                amenityMapper.insertShopAmenity(shopId, amenityId);
            }
        }
    }

    @Transactional
    public void updateAttractionAmenities(Long attractionId, List<Integer> amenityIds) {
        amenityMapper.deleteAttractionAmenities(attractionId); // 모든 기존 연결 삭제
        if (amenityIds != null) {
            for (Integer amenityId : amenityIds) {
                amenityMapper.insertAttractionAmenity(attractionId, amenityId);
            }
        }
    }

    @Transactional
    public void updateAccommodationAmenities(Long accommodationId, List<Integer> amenityIds) {
        amenityMapper.deleteAccommodationAmenities(accommodationId);
        if (amenityIds != null) {
            for (Integer amenityId : amenityIds) {
                amenityMapper.insertAccommodationAmenity(accommodationId, amenityId);
            }
        }
    }

    @Transactional
    public void updateRestaurantAmenities(Long restaurantId, List<Integer> amenityIds) {
        amenityMapper.deleteRestaurantAmenities(restaurantId);
        if (amenityIds != null) {
            for (Integer amenityId : amenityIds) {
                amenityMapper.insertRestaurantAmenity(restaurantId, amenityId);
            }
        }
    }

    @Transactional
    public void updateActivityAmenities(Long activityId, List<Integer> amenityIds) {
        amenityMapper.deleteActivityAmenities(activityId);
        if (amenityIds != null) {
            for (Integer amenityId : amenityIds) {
                amenityMapper.insertActivityAmenity(activityId, amenityId);
            }
        }
    }

    @Transactional
    public void updateShopAmenities(Long shopId, List<Integer> amenityIds) {
        amenityMapper.deleteShopAmenities(shopId);
        if (amenityIds != null) {
            for (Integer amenityId : amenityIds) {
                amenityMapper.insertShopAmenity(shopId, amenityId);
            }
        }
    }

    // === 1. Amenity 등록 ===
    @Transactional
    public void registerAmenity(String code) {
        Amenity amenity = new Amenity();
        amenity.setCode(code);
        amenityMapper.insertAmenity(amenity);
    }

    // === Amenity 전체 리스트 ===
    public List<Amenity> getAllAmenities() {
        return amenityMapper.findAll();
    }

    // === 특정 Amenity의 번역 전체 ===
    public List<AmenityTranslation> getTranslationsByAmenityId(Integer amenityId) {
        return amenityMapper.findTranslationsByAmenityId(amenityId);
    }

    // === Amenity 번역 등록 ===
    @Transactional
    public void registerAmenityTranslation(Integer amenityId, String languageCode, String name) {
        AmenityTranslation translation = new AmenityTranslation();
        translation.setAmenityId(amenityId);
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        amenityMapper.insertAmenityTranslation(translation);
    }

    //  특정 amenity+lang에 번역이 이미 있는지 체크 (중복 방지용)
    public AmenityTranslation findTranslation(Integer amenityId, String languageCode) {
        return amenityMapper.findTranslation(amenityId, languageCode);
    }

}
