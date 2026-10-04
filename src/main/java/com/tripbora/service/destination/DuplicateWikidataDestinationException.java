package com.tripbora.service.destination;

public class DuplicateWikidataDestinationException extends RuntimeException {
    public DuplicateWikidataDestinationException(String qid) {
        super("이미 등록된 Wikidata 여행지입니다: " + qid);
    }
}
