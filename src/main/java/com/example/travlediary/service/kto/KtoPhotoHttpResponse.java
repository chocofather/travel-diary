package com.example.travlediary.service.kto;

import java.io.IOException;
import java.io.InputStream;

final class KtoPhotoHttpResponse implements AutoCloseable {

    private final int statusCode;
    private final String contentType;
    private final long contentLength;
    private final InputStream body;
    private final Runnable closeAction;

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
        this.statusCode = statusCode;
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.body = body;
        this.closeAction = closeAction;
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
