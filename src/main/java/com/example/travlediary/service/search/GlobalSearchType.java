package com.example.travlediary.service.search;

import java.util.Arrays;

public enum GlobalSearchType {
    ALL("all", "전체"),
    DESTINATION("destination", "여행지"),
    COMMUNITY("community", "커뮤니티"),
    COURSE("course", "여행코스"),
    TRAVEL_INFO("travel-info", "여행정보"),
    EVENT("event", "이벤트"),
    NOTICE("notice", "공지사항");

    private final String queryValue;
    private final String label;

    GlobalSearchType(String queryValue, String label) {
        this.queryValue = queryValue;
        this.label = label;
    }

    public String getQueryValue() {
        return queryValue;
    }

    public String getLabel() {
        return label;
    }

    public static GlobalSearchType from(String value) {
        if (value == null) {
            return ALL;
        }
        String normalized = value.strip().toLowerCase();
        return Arrays.stream(values())
                .filter(type -> type.queryValue.equals(normalized))
                .findFirst()
                .orElse(ALL);
    }
}
