package com.tripbora.service.kto;

import java.io.IOException;
import java.io.InputStream;

final class KtoPhotoHttpResponse implements AutoCloseable {

    private final int statusCode;
    private final String contentType;
    private final long contentLength;
    private final InputStream body;
    private final Runnable closeAction;
    private final String retryAfter;

    KtoPhotoHttpResponse(int statusCode, String contentType, long contentLength, InputStream body) {
        this(statusCode, contentType, contentLength, body, () -> { });
    }

    KtoPhotoHttpResponse(
            int statusCode,
            String contentType,
            long contentLength,
            InputStream body,
            Runnable closeAction
    ) {
        this(statusCode, contentType, contentLength, body, closeAction, null);
    }

    KtoPhotoHttpResponse(
            int statusCode,
            String contentType,
            long contentLength,
            InputStream body,
            Runnable closeAction,
            String retryAfter
    ) {
        this.statusCode = statusCode;
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.body = body;
        this.closeAction = closeAction;
        this.retryAfter = retryAfter;
    }

    /** 요청 제한 응답의 Retry-After 헤더 값. 없으면 null. */
    String retryAfter() {
        return retryAfter;
    }

    int statusCode() {
        return statusCode;
    }

    String contentType() {
        return contentType;
    }

    long contentLength() {
        return contentLength;
    }

    InputStream body() {
        return body;
    }

    @Override
    public void close() throws IOException {
        try {
            if (body != null) {
                body.close();
            }
        } finally {
            closeAction.run();
        }
    }
}
