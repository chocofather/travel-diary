package com.example.travlediary.service.file;

/**
 * 실제 이미지 bytes 검증에 실패한 업로드.
 * 입력 오류이므로 IllegalArgumentException 계약을 유지하되,
 * Controller 가 다른 잘못된 인자와 구분해 응답을 매핑할 수 있게 타입을 나눈다.
 */
public class UnsupportedImageFormatException extends IllegalArgumentException {

    public UnsupportedImageFormatException(String message) {
        super(message);
    }
}
