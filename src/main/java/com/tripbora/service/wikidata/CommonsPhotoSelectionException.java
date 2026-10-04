package com.tripbora.service.wikidata;

/** 선택한 Commons 사진을 자동 저장할 수 없을 때. 메시지는 관리자 등록폼에 그대로 보여준다. */
public class CommonsPhotoSelectionException extends IllegalArgumentException {
    public CommonsPhotoSelectionException(String message) {
        super(message);
    }
}
