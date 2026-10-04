package com.tripbora.service.kto;

import java.util.Arrays;
import java.util.Optional;

/**
 * 국문 TourAPI(KorService2) 이미지의 공공누리 유형 판정.
 *
 * <p>응답의 사진별 저작권 구분 코드({@code cpyrhtDivCd})를 근거로 판정한다.
 * {@code Type1}·{@code Type3}만 허용하고, 그 밖의 코드나 빈 값은 판정 불가로 보고 제외한다.
 * 축제·여행지 일괄 등록과 관리자 관광사진 검색이 같은 규칙을 쓴다.</p>
 *
 * <p>관광사진 API(PhotoGalleryService1)는 사진별 저작권 코드가 없으므로 이 판정을 쓰지 않는다
 * ({@link KtoPhotoGalleryService#DATASET_LICENSE_TYPE} 참고).</p>
 */
public enum KtoFestivalImageLicense {
    KOGL_TYPE_1("Type1"),
    KOGL_TYPE_3("Type3");

    private final String tourApiCode;

    KtoFestivalImageLicense(String tourApiCode) {
        this.tourApiCode = tourApiCode;
    }

    public static Optional<KtoFestivalImageLicense> fromCopyrightDivisionCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.strip();
        return Arrays.stream(values())
                .filter(license -> license.tourApiCode.equalsIgnoreCase(normalized))
                .findFirst();
    }
}
