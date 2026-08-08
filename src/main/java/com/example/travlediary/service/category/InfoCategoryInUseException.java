package com.example.travlediary.service.category;

public class InfoCategoryInUseException extends RuntimeException {

    public InfoCategoryInUseException() {
        super("이 카테고리를 사용하는 여행정보가 있어 삭제할 수 없습니다.");
    }

    public InfoCategoryInUseException(Throwable cause) {
        super("이 카테고리를 사용하는 여행정보가 있어 삭제할 수 없습니다.", cause);
    }
}
