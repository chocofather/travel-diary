package com.example.travlediary.service.kto;

public class KtoPhotoImportException extends RuntimeException {

    public KtoPhotoImportException() {
        super("관광사진을 저장하지 못했습니다.");
    }
}
