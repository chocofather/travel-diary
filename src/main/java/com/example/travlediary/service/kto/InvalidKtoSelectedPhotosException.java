package com.example.travlediary.service.kto;

public class InvalidKtoSelectedPhotosException extends RuntimeException {

    private static final String SAFE_MESSAGE = "선택한 관광사진 정보가 올바르지 않습니다.";

    public InvalidKtoSelectedPhotosException() {
        super(SAFE_MESSAGE);
    }

    public InvalidKtoSelectedPhotosException(Throwable cause) {
        super(SAFE_MESSAGE, cause);
    }
}
