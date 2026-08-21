package com.example.travlediary.service.destination;

/**
 * 대상 여행지가 없을 때(이미 삭제됐거나 잘못된 id).
 * HTTP 변환은 Controller 가 맡는다.
 */
public class DestinationNotFoundException extends RuntimeException {

    public DestinationNotFoundException() {
        super("여행지를 찾을 수 없습니다.");
    }
}
