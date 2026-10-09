package com.tripbora.service.category;

import lombok.Getter;

/**
 * 관리자 지역 등록 입력 검증 실패.
 * CategoryValidationException 과 같은 형태로 어떤 입력이 문제인지 field 에 담는다.
 */
@Getter
public class CountryCategoryValidationException extends RuntimeException {

    private final String field;

    public CountryCategoryValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
