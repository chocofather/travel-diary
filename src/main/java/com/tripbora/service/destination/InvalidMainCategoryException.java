package com.tripbora.service.destination;

/** 선택하지 않은 카테고리를 대표로 보냈다. 저장하지 않고 폼에서 다시 고르게 한다. */
public class InvalidMainCategoryException extends IllegalArgumentException {

    public static final String MESSAGE = "대표 카테고리는 선택한 카테고리 중에서 지정해 주세요.";

    public InvalidMainCategoryException() {
        super(MESSAGE);
    }
}
