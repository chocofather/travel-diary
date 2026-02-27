package com.example.travlediary.config;

import com.example.travlediary.model.CountryCategory;
import com.example.travlediary.repository.category.CountryCategoryMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CountryCategoryLoader {

    private final CountryCategoryMapper mapper;

    @PostConstruct
    public void loadInitialData() {
        try {

/*
            mapper.deleteAll(); // ✅ 전체 삭제
*/

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            InputStream is = getClass().getResourceAsStream("/json/country_categories.json");
            List<CountryCategory> topList = objectMapper.readValue(is, new TypeReference<>() {});
            List<CountryCategory> flatList = flatten(topList);

            Set<Integer> existingIds = new HashSet<>(mapper.selectAllIds());

            for (CountryCategory category : flatList) {
                Long idLong = category.getId();
                int id = (idLong != null) ? idLong.intValue() : -1; // null-safe 처리

                if (!existingIds.contains(id)) {
                    mapper.insert(category);
                } else {

                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 트리 구조 -> 평탄화
    private List<CountryCategory> flatten(List<CountryCategory> list) {
        List<CountryCategory> result = new ArrayList<>();
        for (CountryCategory item : list) {
            result.add(item);
            if (item.getChildren() != null && !item.getChildren().isEmpty()) {
                result.addAll(flatten(item.getChildren()));
            }
        }
        return result;
    }
}
