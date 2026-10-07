package com.tripbora.service.destination;

/** 외부 후보(TourAPI·Wikidata·일괄등록 JSON 등)가 TripBora 여행지와 같은 곳인지 판별한 결과. */
public enum DestinationDuplicateStatus {
    /** 같은 외부 ID 또는 같은 Google Place ID. 다시 등록하지 않는다. */
    REGISTERED,
    /** 이름은 같고 위치·지역이 가깝다. 관리자가 확인한 경우에만 등록한다. */
    POSSIBLE_DUPLICATE,
    NOT_REGISTERED
}
