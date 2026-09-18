package com.example.travlediary.service.file;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 실제 이미지 bytes 검증에 실패한 업로드.
 * 입력 오류이므로 IllegalArgumentException 계약을 유지하되,
 * Controller 가 다른 잘못된 인자와 구분해 응답을 매핑할 수 있게 타입을 나눈다.
 *
 * <p>Controller 가 따로 받지 않은 경우에도 서버 오류(500)가 아니라 사용자 입력 오류(400)로 끝난다.
 * (오류 화면에는 server.error.include-message=never 설정으로 메시지가 실리지 않는다)
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class UnsupportedImageFormatException extends IllegalArgumentException {

    public UnsupportedImageFormatException(String message) {
        super(message);
    }
}
