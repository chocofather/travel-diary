package com.example.travlediary.service.category;

import lombok.Getter;

/**
 * 카테고리 등록 입력 검증 실패.
 * AmenityValidationException 과 같은 형태로 어떤 입력이 문제인지 field 에 담는다.
 */
@Getter
public class CategoryValidationException extends RuntimeException {

    private final String field;

    public CategoryValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
