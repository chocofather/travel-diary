package com.example.travlediary.dto;

import java.util.List;

public enum DiaryCoverLibrarySort {

    LATEST("최신순"),
    POPULAR("인기순");

    public static final DiaryCoverLibrarySort DEFAULT = LATEST;

    private final String label;

    DiaryCoverLibrarySort(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static DiaryCoverLibrarySort of(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        for (DiaryCoverLibrarySort sort : values()) {
            if (sort.name().equalsIgnoreCase(value.strip())) {
                return sort;
            }
        }
        return DEFAULT;
    }

    public static List<DiaryCoverLibrarySort> options() {
        return List.of(values());
    }
}
