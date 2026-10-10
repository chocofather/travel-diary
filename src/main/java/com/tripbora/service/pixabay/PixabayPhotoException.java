package com.tripbora.service.pixabay;

/** Pixabay 사진 선택·내려받기·중복 등 저장 전 거절. 메시지는 관리자 화면에 그대로 보여 준다. */
public class PixabayPhotoException extends RuntimeException {

    public PixabayPhotoException(String message) {
        super(message);
    }
}
