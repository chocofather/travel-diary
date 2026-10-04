package com.tripbora.service.wikidata;

public class CommonsApiException extends RuntimeException {
    public CommonsApiException(String message) {
        super(message);
    }

    public CommonsApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
