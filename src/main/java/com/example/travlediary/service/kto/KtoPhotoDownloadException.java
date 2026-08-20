package com.example.travlediary.service.kto;

public class KtoPhotoDownloadException extends RuntimeException {

    private static final String SAFE_MESSAGE = "관광사진을 다운로드하지 못했습니다.";

    public KtoPhotoDownloadException() {
        super(SAFE_MESSAGE);
    }

}
