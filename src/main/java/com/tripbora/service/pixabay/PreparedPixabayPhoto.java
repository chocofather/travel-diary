package com.tripbora.service.pixabay;

import com.tripbora.model.DestinationImageCommonsSource;

/**
 * 내려받기가 끝나고 DB 저장만 남은 Pixabay 사진 한 장.
 * source 는 destination_image_sources 한 행이다(Commons 사진과 같은 출처 테이블·같은 INSERT를 쓴다).
 * source 의 destinationImageId 는 이미지 INSERT 후 채운다.
 */
public record PreparedPixabayPhoto(String localImageUrl, boolean main, DestinationImageCommonsSource source) {
}
