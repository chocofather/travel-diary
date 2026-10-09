package com.tripbora.service.category;

/**
 * 관리자에게 이유를 알려 주고 지역 삭제를 멈추는 경우.
 * (하위 지역 존재, 여행지·코스 참조, 최상위·기준 데이터 지역 등)
 */
public class CountryCategoryDeleteBlockedException extends RuntimeException {

    public CountryCategoryDeleteBlockedException(String message) {
        super(message);
    }

    public CountryCategoryDeleteBlockedException(String message, Throwable cause) {
        super(message, cause);
    }
}
