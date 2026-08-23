package com.example.travlediary.service.travelplan;

import lombok.Getter;

/**
 * 공동 여행계획 입력 검증 실패.
 * Amenity/Category 와 같은 형태로 어떤 입력이 문제인지 field 에 담아
 * 이후 Controller 가 BindingResult 로 옮길 수 있게 한다.
 */
@Getter
public class TravelPlanValidationException extends RuntimeException {

    private final String field;

    public TravelPlanValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
