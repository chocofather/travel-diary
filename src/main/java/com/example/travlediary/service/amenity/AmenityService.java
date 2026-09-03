package com.example.travlediary.service.amenity;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.AmenityDto;
import com.example.travlediary.dto.AmenityForm;
import com.example.travlediary.model.Amenity;
import com.example.travlediary.model.AmenityDestinationType;
import com.example.travlediary.model.AmenityTranslation;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.repository.amenity.AmenityMapper;
import com.example.travlediary.service.category.LocalizedReferenceNameResolver;
import com.example.travlediary.service.file.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    /** 편의시설 이름도 지역·카테고리와 같은 언어 대체 규칙을 쓴다. */
    private final LocalizedReferenceNameResolver amenityNameResolver;

    // --- 공통: lang 없으면 "ko" (한국어) 기본 ---
    private static final String DEFAULT_LANG = "ko";

    /**
     * 관리자 입력 슬롯이 쓰는 언어 코드. 화면이 읽는 코드와 같아야 하므로
     * {@link SupportedLanguage} 의 canonical 값을 그대로 쓴다. (legacy 'zh' 는 쓰지 않는다)
     */
    private static final String KOREAN_CODE = SupportedLanguage.KOREAN.getLanguageTag();
    private static final String ENGLISH_CODE = SupportedLanguage.ENGLISH.getLanguageTag();
    private static final String JAPANESE_CODE = SupportedLanguage.JAPANESE.getLanguageTag();
    private static final String CHINESE_SIMPLIFIED_CODE =
            SupportedLanguage.CHINESE_SIMPLIFIED.getLanguageTag();
    private static final String CHINESE_TRADITIONAL_CODE =
            SupportedLanguage.CHINESE_TRADITIONAL.getLanguageTag();

    /** 아이콘 파일명이 code 에서 만들어지므로 경로 문자가 섞일 수 없는 형식만 허용한다. */
    private static final Pattern AMENITY_CODE = Pattern.compile("^[A-Z0-9_]{2,50}$");
    /** amenity_translations.name 은 varchar(100) */
    private static final int MAX_TRANSLATION_NAME_LENGTH = 100;
    /** icon_url 이 비어 있는 기존 데이터의 대체 경로 (상세 페이지 fallback 과 같은 규칙) */
    private static final String LEGACY_ICON_URL_PREFIX = "/uploads/icons/amenities/";

    // 관광지용 Amenity DTO 반환
    public List<AmenityDto> getAttractionAmenities(Long destinationId) {
        return getAttractionAmenities(destinationId, SupportedLanguage.KOREAN);
    }
    public List<AmenityDto> getAttractionAmenities(Long destinationId, SupportedLanguage language) {
        return toLocalizedAmenities(
                amenityMapper.findAttractionAmenityTranslationsByDestinationId(destinationId),
                language);
    }

    public List<AmenityDto> getAccommodationAmenities(Long destinationId) {
        return getAccommodationAmenities(destinationId, SupportedLanguage.KOREAN);
    }
    public List<AmenityDto> getAccommodationAmenities(Long destinationId, SupportedLanguage language) {
        return toLocalizedAmenities(
                amenityMapper.findAccommodationAmenityTranslationsByDestinationId(destinationId),
                language);
    }

    public List<AmenityDto> getRestaurantAmenities(Long destinationId) {
        return getRestaurantAmenities(destinationId, SupportedLanguage.KOREAN);
    }
    public List<AmenityDto> getRestaurantAmenities(Long destinationId, SupportedLanguage language) {
        return toLocalizedAmenities(
                amenityMapper.findRestaurantAmenityTranslationsByDestinationId(destinationId),
                language);
    }

    public List<AmenityDto> getActivityAmenities(Long destinationId) {
        return getActivityAmenities(destinationId, SupportedLanguage.KOREAN);
    }
    public List<AmenityDto> getActivityAmenities(Long destinationId, SupportedLanguage language) {
        return toLocalizedAmenities(
                amenityMapper.findActivityAmenityTranslationsByDestinationId(destinationId),
                language);
    }

    public List<AmenityDto> getShopAmenities(Long destinationId) {
        return getShopAmenities(destinationId, SupportedLanguage.KOREAN);
    }
    public List<AmenityDto> getShopAmenities(Long destinationId, SupportedLanguage language) {
        return toLocalizedAmenities(
                amenityMapper.findShopAmenityTranslationsByDestinationId(destinationId),
                language);
    }

    /**
     * 한 여행지의 편의시설 목록을 화면 언어에 맞춰 만든다.
     *
     * <p>편의시설마다 다시 조회하지 않는다. 조회 한 번으로 받은 번역 행을 편의시설별로 모아
     * 이름만 고른다. 고르는 차례는 요청 언어 → 한국어 → 남은 언어(언어 코드, id 순) → code 이며,
     * 지역·카테고리 이름과 같은 규칙({@link LocalizedReferenceNameResolver})을 쓴다.
     *
     * <p>번호·아이콘·목록 차례는 손대지 않는다.
     */
    private List<AmenityDto> toLocalizedAmenities(List<AmenityTranslation> rows,
                                                  SupportedLanguage requestedLanguage) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        Map<Integer, AmenityDto> amenities = new LinkedHashMap<>();
        Map<Integer, List<LocalizedReferenceNameResolver.LocalizedName>> namesByAmenityId =
                new LinkedHashMap<>();
        for (AmenityTranslation row : rows) {
            if (row == null || row.getAmenityId() == null) {
                continue;
            }
            amenities.computeIfAbsent(row.getAmenityId(), id -> {
                AmenityDto dto = new AmenityDto();
                dto.setId(id);
                dto.setCode(row.getCode());
                dto.setIconUrl(row.getIconUrl());
                return dto;
            });
            if (row.getLanguageCode() != null) {
                namesByAmenityId.computeIfAbsent(row.getAmenityId(), id -> new ArrayList<>())
                        .add(new LocalizedReferenceNameResolver.LocalizedName(
                                row.getId() == null ? null : row.getId().longValue(),
                                row.getLanguageCode(),
                                row.getName()));
            }
        }

        List<AmenityDto> localized = new ArrayList<>(amenities.size());
        amenities.forEach((amenityId, dto) -> {
            dto.setName(amenityNameResolver.resolve(requestedLanguage, dto.getCode(),
                    namesByAmenityId.getOrDefault(amenityId, List.of())));
            localized.add(dto);
        });
        return localized;
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
        String nameZhCn = optionalName("nameZhCn", form.getNameZhCn());
        String nameZhTw = optionalName("nameZhTw", form.getNameZhTw());
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

        // 언어 코드는 사용자가 정하지 않고 폼 슬롯에 고정한다. 화면이 쓰는 다섯 코드 그대로다.
        insertTranslation(amenityId, KOREAN_CODE, nameKo);
        insertTranslation(amenityId, ENGLISH_CODE, nameEn);
        insertTranslation(amenityId, JAPANESE_CODE, nameJa);
        insertTranslation(amenityId, CHINESE_SIMPLIFIED_CODE, nameZhCn);
        insertTranslation(amenityId, CHINESE_TRADITIONAL_CODE, nameZhTw);

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

    /** 수정 화면 복원용. code / 4개 언어 / 적용 대상을 폼에 담아 돌려준다. */
    public AmenityForm getAmenityForm(Integer id) {
        Amenity amenity = requireAmenity(id);

        AmenityForm form = new AmenityForm();
        form.setId(amenity.getId());
        form.setCode(amenity.getCode());

        Map<String, String> names = new LinkedHashMap<>();
        List<AmenityTranslation> translations = amenityMapper.findTranslationsByAmenityId(id);
        if (translations != null) {
            for (AmenityTranslation translation : translations) {
                if (translation != null && translation.getLanguageCode() != null) {
                    names.put(translation.getLanguageCode(), translation.getName());
                }
            }
        }
        form.setNameKo(names.get(KOREAN_CODE));
        form.setNameEn(names.get(ENGLISH_CODE));
        form.setNameJa(names.get(JAPANESE_CODE));
        form.setNameZhCn(names.get(CHINESE_SIMPLIFIED_CODE));
        form.setNameZhTw(names.get(CHINESE_TRADITIONAL_CODE));

        List<DestinationType> types = new ArrayList<>();
        List<AmenityDestinationType> mappings =
                amenityMapper.findAmenityDestinationTypesByAmenityId(id);
        if (mappings != null) {
            for (AmenityDestinationType mapping : mappings) {
                if (mapping != null && mapping.getDestinationType() != null) {
                    types.add(DestinationType.valueOf(mapping.getDestinationType()));
                }
            }
        }
        form.setDestinationTypes(types);
        return form;
    }

    /** 수정 화면 아이콘 미리보기 경로. icon_url 이 없는 기존 데이터는 code 기반 .png 로 대체한다. */
    public String getAmenityIconUrl(Integer id) {
        Amenity amenity = requireAmenity(id);
        if (amenity.getIconUrl() != null && !amenity.getIconUrl().isBlank()) {
            return amenity.getIconUrl();
        }
        return LEGACY_ICON_URL_PREFIX + amenity.getCode().toLowerCase(Locale.ROOT) + ".png";
    }

    /**
     * 관리자 편의시설 수정.
     * code 는 아이콘 파일명과 연결된 식별자이므로 절대 갱신하지 않는다.
     * 아이콘은 파일을 새로 고른 경우에만 교체하며, 커밋된 뒤에야 옛 파일을 정리한다.
     */
    @Transactional
    public void updateAmenity(AmenityForm form) {
        if (form == null || form.getId() == null) {
            throw new AmenityValidationException("form", "수정할 편의시설을 찾을 수 없습니다.");
        }

        Amenity amenity = requireAmenity(form.getId());
        Integer amenityId = amenity.getId();
        // 폼으로 넘어온 code 는 신뢰하지 않고 저장된 값만 쓴다.
        String code = amenity.getCode();

        String nameKo = requiredName("nameKo", form.getNameKo(), "한국어 이름을 입력해 주세요.");
        String nameEn = optionalName("nameEn", form.getNameEn());
        String nameJa = optionalName("nameJa", form.getNameJa());
        String nameZhCn = optionalName("nameZhCn", form.getNameZhCn());
        String nameZhTw = optionalName("nameZhTw", form.getNameZhTw());
        List<DestinationType> types = requiredDestinationTypes(form.getDestinationTypes());

        MultipartFile icon = form.getIcon();
        boolean replacingIcon = icon != null && !icon.isEmpty();
        if (replacingIcon) {
            // DB 를 건드리기 전에 잘못된 업로드를 먼저 걸러낸다.
            fileUploadService.validateAmenityIcon(icon);
        }

        // 언어마다 따로 넣고/고치고/지운다. 간체와 번체도 서로 영향을 주지 않는다.
        upsertTranslation(amenityId, KOREAN_CODE, nameKo);
        upsertTranslation(amenityId, ENGLISH_CODE, nameEn);
        upsertTranslation(amenityId, JAPANESE_CODE, nameJa);
        upsertTranslation(amenityId, CHINESE_SIMPLIFIED_CODE, nameZhCn);
        upsertTranslation(amenityId, CHINESE_TRADITIONAL_CODE, nameZhTw);

        // 복합 PK 뿐이라 잃을 정보가 없다. 전체 삭제 후 선택값을 다시 넣는다.
        amenityMapper.deleteAmenityDestinationTypesByAmenityId(amenityId);
        for (DestinationType type : types) {
            amenityMapper.insertAmenityDestinationType(amenityId, type.name());
        }

        if (replacingIcon) {
            FileUploadService.AmenityIconReplacement replacement =
                    fileUploadService.replaceAmenityIcon(code, icon);
            registerIconReplacementCleanup(replacement);
            amenityMapper.updateAmenityIconUrl(amenityId, replacement.iconUrl());
        }
    }

    private Amenity requireAmenity(Integer id) {
        Amenity amenity = id == null ? null : amenityMapper.selectAmenityById(id);
        if (amenity == null) {
            throw new AmenityValidationException("id", "수정할 편의시설을 찾을 수 없습니다.");
        }
        return amenity;
    }

    /** 값이 있으면 있으면 UPDATE / 없으면 INSERT, 값을 비웠으면 DELETE. */
    private void upsertTranslation(Integer amenityId, String languageCode, String name) {
        AmenityTranslation existing = amenityMapper.findTranslation(amenityId, languageCode);
        if (name == null) {
            if (existing != null) {
                amenityMapper.deleteAmenityTranslation(amenityId, languageCode);
            }
            return;
        }

        AmenityTranslation translation = new AmenityTranslation();
        translation.setAmenityId(amenityId);
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        if (existing == null) {
            amenityMapper.insertAmenityTranslation(translation);
        } else {
            amenityMapper.updateAmenityTranslation(translation);
        }
    }

    /**
     * 커밋되면 백업과 옛 확장자 파일을 정리하고, 롤백되면 새 파일을 지우고 기존 아이콘을 되돌린다.
     * 기존 운영 아이콘은 커밋이 확정되기 전까지 삭제하지 않는다.
     */
    private void registerIconReplacementCleanup(
            FileUploadService.AmenityIconReplacement replacement) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                try {
                    if (status == TransactionSynchronization.STATUS_COMMITTED) {
                        fileUploadService.commitAmenityIconReplacement(replacement);
                    } else {
                        fileUploadService.rollbackAmenityIconReplacement(replacement);
                    }
                } catch (RuntimeException cleanupFailure) {
                    log.warn("편의시설 아이콘 교체 뒷정리에 실패했습니다. (원인: {})",
                            cleanupFailure.getClass().getSimpleName());
                }
            }
        });
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

    /** 관리자 목록용 한 줄 목록. ko 번역이 없는 편의시설도 그대로 포함된다. */
    public List<AmenityDto> getAdminAmenityRows() {
        List<AmenityDto> rows = amenityMapper.findAdminAmenityRows();
        return rows == null ? List.of() : rows;
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
