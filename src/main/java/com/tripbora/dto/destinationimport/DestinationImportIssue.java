package com.tripbora.dto.destinationimport;

/**
 * JSON 일괄등록 검증 결과 한 줄.
 *
 * @param path    JSON 경로. 예: {@code destinations[3].info.openingHours}
 * @param message 관리자에게 보여줄 원인
 */
public record DestinationImportIssue(String path, String message) {

    /** 화면·로그에 그대로 쓰는 한 줄. 예: {@code destinations[3].info.openingHours: 최대 64자, 현재 71자} */
    public String text() {
        return path == null || path.isEmpty() ? message : path + ": " + message;
    }
}
