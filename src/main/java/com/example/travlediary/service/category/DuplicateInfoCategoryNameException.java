package com.example.travlediary.service.category;

public class DuplicateInfoCategoryNameException extends RuntimeException {

    public DuplicateInfoCategoryNameException() {
        super("이미 사용 중인 카테고리명입니다.");
    }

    public DuplicateInfoCategoryNameException(Throwable cause) {
        super("이미 사용 중인 카테고리명입니다.", cause);
    }
}
