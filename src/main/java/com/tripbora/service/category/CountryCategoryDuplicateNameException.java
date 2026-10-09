package com.tripbora.service.category;

/**
 * 같은 부모 아래에 같은 이름의 지역이 이미 있다.
 * 일괄 등록 결과에서 "이미 존재" 를 다른 실패와 나눠 보여 주려고 따로 둔다.
 */
public class CountryCategoryDuplicateNameException extends CountryCategoryValidationException {

    public CountryCategoryDuplicateNameException(String field, String message) {
        super(field, message);
    }
}
