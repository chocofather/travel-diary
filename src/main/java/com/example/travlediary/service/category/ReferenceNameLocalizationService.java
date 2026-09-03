package com.example.travlediary.service.category;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.Category;
import com.example.travlediary.model.CategoryTranslation;
import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.model.CountryCategoryTranslation;
import com.example.travlediary.repository.category.CategoryMapper;
import com.example.travlediary.repository.category.CountryCategoryMapper;
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
