package com.example.travlediary.service.amenity;

import com.example.travlediary.dto.AmenityDto;
import com.example.travlediary.dto.AmenityForm;
import com.example.travlediary.model.Amenity;
import com.example.travlediary.model.AmenityDestinationType;
import com.example.travlediary.model.AmenityTranslation;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.repository.amenity.AmenityMapper;
import com.example.travlediary.service.file.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AmenityService {
    private final AmenityMapper amenityMapper;
    private final FileUploadService fileUploadService;

    // --- 공통: lang 없으면 "ko" (한국어) 기본 ---
    private static final String DEFAULT_LANG = "ko";

    /** 아이콘 파일명이 code 에서 만들어지므로 경로 문자가 섞일 수 없는 형식만 허용한다. */
    private static final Pattern AMENITY_CODE = Pattern.compile("^[A-Z0-9_]{2,50}$");
    /** amenity_translations.name 은 varchar(100) */
    private static final int MAX_TRANSLATION_NAME_LENGTH = 100;

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
            dto.setIconUrl(tr.getIconUrl());
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
            dto.setIconUrl(tr.getIconUrl());
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
            dto.setIconUrl(tr.getIconUrl());
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
            dto.setIconUrl(tr.getIconUrl());
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
            dto.setIconUrl(tr.getIconUrl());
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

    /**
     * 관리자 통합 편의시설 등록.
     * amenities / amenity_translations / amenity_destination_types 와 아이콘 파일을 한 번에 만든다.
     * 입력 검증은 전부 INSERT 전에 끝내고, 아이콘 파일은 DB INSERT 가 끝난 뒤 저장한다.
     *
     * @return 생성된 amenities.id
     */
    @Transactional
    public Integer registerAmenity(AmenityForm form) {
        if (form == null) {
            throw new AmenityValidationException("form", "편의시설 정보를 입력해 주세요.");
        }

        String code = requiredCode(form.getCode());
        String nameKo = requiredName("nameKo", form.getNameKo(), "한국어 이름을 입력해 주세요.");
        String nameEn = optionalName("nameEn", form.getNameEn());
        String nameJa = optionalName("nameJa", form.getNameJa());
        String nameZh = optionalName("nameZh", form.getNameZh());
        List<DestinationType> types = requiredDestinationTypes(form.getDestinationTypes());

        MultipartFile icon = form.getIcon();
        if (icon == null || icon.isEmpty()) {
            throw new AmenityValidationException("icon", "아이콘 이미지를 선택해 주세요.");
        }
        // 파일은 아직 저장하지 않고 검증만 한다. DB 를 건드리기 전에 잘못된 업로드를 걸러낸다.
        fileUploadService.validateAmenityIcon(icon);

        if (amenityMapper.countByCode(code) > 0) {
            throw new AmenityValidationException("code", "이미 등록된 편의시설 코드입니다.");
        }

        Amenity amenity = new Amenity();
        amenity.setCode(code);
        amenityMapper.insertAmenity(amenity);
        Integer amenityId = amenity.getId();

        // 언어 코드는 사용자가 정하지 않고 폼 슬롯에 고정한다.
        insertTranslation(amenityId, "ko", nameKo);
        insertTranslation(amenityId, "en", nameEn);
        insertTranslation(amenityId, "ja", nameJa);
        insertTranslation(amenityId, "zh", nameZh);

        for (DestinationType type : types) {
            amenityMapper.insertAmenityDestinationType(amenityId, type.name());
        }

        // 업로드한 포맷 그대로 저장되므로 확장자는 파일마다 다르다. 실제 경로를 그대로 기록한다.
        String iconUrl = fileUploadService.saveAmenityIcon(code, icon);
        registerIconRollbackCleanup(code);
        amenityMapper.updateAmenityIconUrl(amenityId, iconUrl);
        return amenityId;
    }

    private String requiredCode(String value) {
        String code = value == null ? "" : value.trim();
        if (code.isEmpty()) {
            throw new AmenityValidationException("code", "코드를 입력해 주세요.");
        }
        if (!AMENITY_CODE.matcher(code).matches()) {
            throw new AmenityValidationException("code",
                    "코드는 영문 대문자, 숫자, 밑줄만 사용해 2~50자로 입력해 주세요.");
        }
        return code;
    }

    private String requiredName(String field, String value, String message) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty()) {
            throw new AmenityValidationException(field, message);
        }
        return checkNameLength(field, name);
    }

    /** 선택 언어는 비우면 저장하지 않는다. */
    private String optionalName(String field, String value) {
        String name = value == null ? "" : value.trim();
        return name.isEmpty() ? null : checkNameLength(field, name);
    }

    private String checkNameLength(String field, String name) {
        if (name.length() > MAX_TRANSLATION_NAME_LENGTH) {
            throw new AmenityValidationException(field, "이름은 100자 이하로 입력해 주세요.");
        }
        return name;
    }

    /** 같은 타입이 여러 번 들어와도 복합 PK 가 깨지지 않도록 순서를 유지하며 중복을 없앤다. */
    private List<DestinationType> requiredDestinationTypes(List<DestinationType> values) {
        Set<DestinationType> types = new LinkedHashSet<>();
        if (values != null) {
            for (DestinationType type : values) {
                if (type != null) {
                    types.add(type);
                }
            }
        }
        if (types.isEmpty()) {
            throw new AmenityValidationException("destinationTypes", "적용 대상을 1개 이상 선택해 주세요.");
        }
        return List.copyOf(types);
    }

    private void insertTranslation(Integer amenityId, String languageCode, String name) {
        if (name == null) {
            return;
        }
        AmenityTranslation translation = new AmenityTranslation();
        translation.setAmenityId(amenityId);
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        amenityMapper.insertAmenityTranslation(translation);
    }

    /**
     * DB 트랜잭션은 파일시스템을 되돌리지 못하므로, 커밋되지 않으면 이번에 만든 아이콘을 지운다.
     * 신규 등록은 덮어쓰기를 하지 않으므로 기존 운영 아이콘이 지워질 일은 없다.
     */
    private void registerIconRollbackCleanup(String code) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                try {
                    fileUploadService.deleteAmenityIcon(code);
                } catch (RuntimeException cleanupFailure) {
                    log.warn("롤백된 신규 편의시설 아이콘 파일을 정리하지 못했습니다. (원인: {})",
                            cleanupFailure.getClass().getSimpleName());
                }
            }
        });
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
