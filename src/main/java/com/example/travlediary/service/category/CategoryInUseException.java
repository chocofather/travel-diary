package com.example.travlediary.service.category;

import lombok.Getter;

/**
 * 여행지에서 사용 중인 카테고리를 삭제하려 한 경우.
 * 사용 건수를 함께 담아 Controller 가 다시 세지 않아도 되게 한다.
 */
@Getter
public class CategoryInUseException extends RuntimeException {

    private final int usageCount;

    public CategoryInUseException(int usageCount) {
        super("현재 " + usageCount + "개의 여행지에서 사용 중이라 삭제할 수 없습니다.");
        this.usageCount = usageCount;
    }
}
