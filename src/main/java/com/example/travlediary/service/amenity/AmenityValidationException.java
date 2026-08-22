package com.example.travlediary.service.amenity;

import lombok.Getter;

/**
 * 편의시설 등록 입력 검증 실패.
 * EventValidationException 과 같은 형태로 어떤 입력이 문제인지 field 에 담는다.
 */
@Getter
public class AmenityValidationException extends RuntimeException {

    private final String field;

    public AmenityValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
