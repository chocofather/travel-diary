package com.example.travlediary.service.category;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.Category;
import com.example.travlediary.model.CategoryTranslation;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.model.CountryCategoryTranslation;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.InfoCategoryTranslation;
import com.example.travlediary.repository.category.CategoryMapper;
import com.example.travlediary.repository.category.CountryCategoryMapper;
import com.example.travlediary.repository.category.InfoCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReferenceNameLocalizationService {

    private final CountryCategoryMapper countryCategoryMapper;
    private final CategoryMapper categoryMapper;
    private final InfoCategoryMapper infoCategoryMapper;
    private final LocalizedReferenceNameResolver nameResolver;

    public Map<Long, String> localizeCountryCategories(
            Collection<CountryCategory> countryCategories,
            SupportedLanguage requestedLanguage) {
        Map<Long, String> baseNames = new LinkedHashMap<>();
        if (countryCategories != null) {
            for (CountryCategory category : countryCategories) {
                if (category != null && category.getId() != null) {
                    baseNames.putIfAbsent(category.getId(), category.getRegionName());
                }
            }
        }
        return localizeCountryCategoryNames(baseNames, requestedLanguage);
    }

    public Map<Long, String> localizeCountryCategoryNames(
            Map<Long, String> countryCategoryBaseNames,
            SupportedLanguage requestedLanguage) {
        Map<Long, String> baseNames = new LinkedHashMap<>();
        if (countryCategoryBaseNames != null) {
            countryCategoryBaseNames.forEach((id, name) -> {
                if (id != null) {
                    baseNames.putIfAbsent(id, name);
                }
            });
        }
        if (baseNames.isEmpty()) {
            return Map.of();
        }

        List<Long> ids = List.copyOf(baseNames.keySet());
        List<CountryCategoryTranslation> translations =
                countryCategoryMapper.findTranslationsByCountryCategoryIds(ids);
        Map<Long, List<LocalizedReferenceNameResolver.LocalizedName>> namesById = new LinkedHashMap<>();
        if (translations != null) {
            for (CountryCategoryTranslation translation : translations) {
                if (translation == null || translation.getCountryCategoryId() == null) {
                    continue;
                }
                namesById.computeIfAbsent(translation.getCountryCategoryId(), key -> new ArrayList<>())
                        .add(new LocalizedReferenceNameResolver.LocalizedName(
                                translation.getId(), translation.getLanguageCode(), translation.getName()));
            }
        }
        return resolve(baseNames, namesById, requestedLanguage);
    }

    public Map<Long, String> localizeCategories(Collection<Long> categoryIds,
                                                 SupportedLanguage requestedLanguage) {
        List<Long> ids = distinctIds(categoryIds);
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> baseNames = new LinkedHashMap<>();
        List<Category> categories = categoryMapper.findByIds(ids);
        if (categories != null) {
            for (Category category : categories) {
                if (category != null && category.getId() != null) {
                    baseNames.putIfAbsent(category.getId(), category.getName());
                }
            }
        }
        for (Long id : ids) {
            baseNames.putIfAbsent(id, null);
        }

        List<CategoryTranslation> translations = categoryMapper.findTranslationsByCategoryIds(ids);
        Map<Long, List<LocalizedReferenceNameResolver.LocalizedName>> namesById = new LinkedHashMap<>();
        if (translations != null) {
            for (CategoryTranslation translation : translations) {
                if (translation == null || translation.getCategoryId() == null) {
                    continue;
                }
                namesById.computeIfAbsent(translation.getCategoryId(), key -> new ArrayList<>())
                        .add(new LocalizedReferenceNameResolver.LocalizedName(
                                translation.getId(), translation.getLanguageCode(), translation.getName()));
            }
        }
        return resolve(baseNames, namesById, requestedLanguage);
    }

    /**
     * 정보 카테고리(여행정보·축제·행사 공용) 이름을 요청 언어로 바꾼다.
     *
     * <p>카테고리 목록을 이미 들고 있는 화면(필터 등)이 그대로 넘기면 된다.
     */
    public Map<Long, String> localizeInfoCategories(Collection<InfoCategory> infoCategories,
                                                    SupportedLanguage requestedLanguage) {
        Map<Long, String> baseNames = new LinkedHashMap<>();
        if (infoCategories != null) {
            for (InfoCategory category : infoCategories) {
                if (category != null && category.getId() != null) {
                    baseNames.putIfAbsent(category.getId(), category.getName());
                }
            }
        }
        return localizeInfoCategoryNames(baseNames, requestedLanguage);
    }

    /**
     * 카테고리 번호 → 원문 이름을 받아 표시용 이름을 만든다.
     *
     * <p>목록 카드처럼 조회 결과에 카테고리 번호와 이름이 이미 실려 오는 화면용이다.
     * 번역은 한 번의 조회로 모아 읽는다.
     */
    public Map<Long, String> localizeInfoCategoryNames(Map<Long, String> infoCategoryBaseNames,
                                                       SupportedLanguage requestedLanguage) {
        Map<Long, String> baseNames = new LinkedHashMap<>();
        if (infoCategoryBaseNames != null) {
            infoCategoryBaseNames.forEach((id, name) -> {
                if (id != null) {
                    baseNames.putIfAbsent(id, name);
                }
            });
        }
        if (baseNames.isEmpty()) {
            // 볼 카테고리가 없으면 번역도 읽지 않는다.
            return Map.of();
        }

        List<Long> ids = List.copyOf(baseNames.keySet());
        List<InfoCategoryTranslation> translations =
                infoCategoryMapper.findTranslationsByCategoryIds(ids);
        Map<Long, List<LocalizedReferenceNameResolver.LocalizedName>> namesById = new LinkedHashMap<>();
        if (translations != null) {
            for (InfoCategoryTranslation translation : translations) {
                if (translation == null || translation.getInfoCategoryId() == null) {
                    continue;
                }
                namesById.computeIfAbsent(translation.getInfoCategoryId(), key -> new ArrayList<>())
                        .add(new LocalizedReferenceNameResolver.LocalizedName(
                                translation.getId(), translation.getLanguageCode(), translation.getName()));
            }
        }
        return resolve(baseNames, namesById, requestedLanguage);
    }

    /** 상세 화면처럼 카테고리 한 건만 필요한 곳에서 쓴다. */
    public String localizeInfoCategoryName(Long infoCategoryId,
                                           String baseName,
                                           SupportedLanguage requestedLanguage) {
        if (infoCategoryId == null) {
            return baseName;
        }
        Map<Long, String> localized = localizeInfoCategoryNames(
                Map.of(infoCategoryId, baseName == null ? "" : baseName), requestedLanguage);
        return localized.getOrDefault(infoCategoryId, baseName);
    }

    private Map<Long, String> resolve(
            Map<Long, String> baseNames,
            Map<Long, List<LocalizedReferenceNameResolver.LocalizedName>> namesById,
            SupportedLanguage requestedLanguage) {
        Map<Long, String> localized = new LinkedHashMap<>();
        baseNames.forEach((id, baseName) -> localized.put(id,
                nameResolver.resolve(requestedLanguage, baseName,
                        namesById.getOrDefault(id, List.of()))));
        return Map.copyOf(localized);
    }

    private List<Long> distinctIds(Collection<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
