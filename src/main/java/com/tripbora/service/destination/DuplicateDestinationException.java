package com.tripbora.service.destination;

/**
 * 저장 직전 공통 중복 판별에 걸렸다. 확정 중복이거나, 관리자가 확인하지 않은 중복 가능성이다.
 * 아무것도 저장하지 않았다.
 */
public class DuplicateDestinationException extends RuntimeException {

    private final DestinationDuplicateCheck check;

    public DuplicateDestinationException(DestinationDuplicateCheck check) {
        super(check.message());
        this.check = check;
    }

    public DestinationDuplicateCheck getCheck() {
        return check;
    }
}
