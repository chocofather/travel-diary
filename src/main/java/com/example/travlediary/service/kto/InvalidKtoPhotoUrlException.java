package com.example.travlediary.service.kto;

public class InvalidKtoPhotoUrlException extends RuntimeException {

    private static final String SAFE_MESSAGE = "허용되지 않은 관광사진 URL입니다.";

    public InvalidKtoPhotoUrlException() {
        super(SAFE_MESSAGE);
    }

}
