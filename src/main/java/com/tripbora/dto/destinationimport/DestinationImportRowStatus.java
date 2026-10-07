package com.tripbora.dto.destinationimport;

/**
 * JSON 일괄등록 미리보기의 행 상태. 선언 순서가 우선순위다(앞이 높다).
 * 여러 조건에 걸리면 가장 앞의 상태 하나만 보여준다.
 */
public enum DestinationImportRowStatus {
    /** 검증·매핑 오류. 등록할 수 없다. */
    INVALID,
    /** 이미 등록된 여행지(같은 외부 ID·Place ID). 등록할 수 없다. */
    REGISTERED,
    /** 이름·위치가 같은 여행지가 있다. 관리자가 행마다 확인해야 등록할 수 있다. */
    POSSIBLE_DUPLICATE,
    /** 바로 등록할 수 있다. */
    NOT_REGISTERED
}
