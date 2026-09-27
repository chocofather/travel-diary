package com.example.travlediary.service.wikidata;

/** 검증을 통과한 Commons 사진을 내려받지 못했을 때. 메시지는 관리자 등록폼에 그대로 보여준다. */
public class CommonsPhotoDownloadException extends RuntimeException {
    public CommonsPhotoDownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
