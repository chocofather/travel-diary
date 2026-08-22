package com.example.travlediary.service.category;

/** categories.name 은 UNIQUE 다. 사전 검사와 DB 제약 위반 양쪽에서 같은 예외로 알린다. */
public class DuplicateCategoryNameException extends RuntimeException {

    public DuplicateCategoryNameException() {
        super("이미 등록된 카테고리 이름입니다.");
    }

    public DuplicateCategoryNameException(Throwable cause) {
        super("이미 등록된 카테고리 이름입니다.", cause);
    }
}
