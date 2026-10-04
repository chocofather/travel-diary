package com.tripbora.service.wikidata;

public class WikidataApiException extends RuntimeException {

    public WikidataApiException(String message) {
        super(message);
    }

    public WikidataApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
