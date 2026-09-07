package com.example.travlediary.model;

import java.util.Comparator;
import java.util.List;

/**
 * picker 의 스티커 묶음 한 개. (예: 여행 / 감정 / 음식)
 * 목록과 순서는 resources/json/diary_stickers.json 의 categories 를 그대로 따른다.
 */
public record DiaryStickerCategory(String id, String name, List<DiarySticker> stickers) {

    /**
     * 이 묶음 안에 실제로 있는 표현 스타일.
     *
     * <p>화면은 이 값으로 고르는 줄을 그리지 않는다. picker 는 분류(category) 하나만 나누고,
     * 기본 스티커와 리얼 스티커는 한 grid 에 섞여 나온다. 표현 스타일은 asset 을 갈라 두기
     * 위한 내부 값이라, 여기서는 어떤 스타일이 들어 있는지 확인하는 용도로만 쓴다.
     * 순서는 enum 에 적은 차례(기본 → 리얼)를 따라 어느 묶음에서나 같다.
     */
    public List<DiaryStickerCollection> collections() {
        return stickers.stream()
                .map(DiarySticker::collection)
                .distinct()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList();
    }
}
