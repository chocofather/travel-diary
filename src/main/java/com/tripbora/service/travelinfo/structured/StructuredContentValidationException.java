package com.tripbora.service.travelinfo.structured;

import com.tripbora.service.travelinfo.TravelInfoValidationException;

/**
 * 구조화 콘텐츠 JSON 을 읽거나 검사하다 실패했다. 관리자 화면에 그대로 보여 줄 수 있는 문구를 담는다.
 *
 * <p>여행정보 검증 오류와 같은 계열이라, 저장 흐름에 연결하면 기존 폼 오류 처리를 그대로 탄다.
 */
public class StructuredContentValidationException extends TravelInfoValidationException {

    /** 원문 블록 JSON 을 받는 폼 필드. */
    public static final String CONTENT_FIELD = "structuredContent";

    public StructuredContentValidationException(String message) {
        super(CONTENT_FIELD, message);
    }

    public StructuredContentValidationException(String field, String message) {
        super(field, message);
    }
}
