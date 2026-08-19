package com.example.travlediary.model;

import java.util.List;

/**
 * picker 의 스티커 묶음 한 개. (예: 여행 / 감정 / 음식)
 * 목록과 순서는 resources/json/diary_stickers.json 의 categories 를 그대로 따른다.
 */
public record DiaryStickerCategory(String id, String name, List<DiarySticker> stickers) {
}
