package com.example.travlediary.service.wikidata;

import com.example.travlediary.model.DestinationImageCommonsSource;

/**
 * 재검증과 내려받기가 끝나고 DB 저장만 남은 Commons 사진 한 장.
 * source 의 destinationImageId 는 이미지 INSERT 후 채운다.
 */
public record PreparedCommonsPhoto(String localImageUrl, boolean main, DestinationImageCommonsSource source) {
}
