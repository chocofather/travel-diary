package com.example.travlediary.service.search;

import java.util.Arrays;

public enum GlobalSearchType {
    ALL("all"),
    DESTINATION("destination"),
    COMMUNITY("community"),
    COURSE("course"),
    TRAVEL_INFO("travel-info"),
    EVENT("event"),
    NOTICE("notice");

    private final String queryValue;

    GlobalSearchType(String queryValue) {
        this.queryValue = queryValue;
    }

    public String getQueryValue() {
        return queryValue;
    }

    /** 탭 이름은 화면에서 현재 언어로 읽는다. */
    public String getMessageKey() {
        return "search.type." + queryValue;
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
