package com.example.travlediary.service.kto;

/**
 * 사진 원본 서버가 요청 제한(HTTP 429·503)으로 답했다. 기존 다운로드 실패의 한 종류라 KTO 흐름은 이전과 같이 처리하고,
 * Commons 사진 저장은 Retry-After 를 보고 잠시 뒤 다시 시도한다.
 */
public class PhotoDownloadRateLimitedException extends KtoPhotoDownloadException {
    private final String retryAfter;

    public PhotoDownloadRateLimitedException(String retryAfter) {
        this.retryAfter = retryAfter;
    }

    /** 응답의 Retry-After 헤더 값 그대로. 없으면 null. */
    public String retryAfter() {
        return retryAfter;
    }
}
