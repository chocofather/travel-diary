package com.tripbora.dto.kto;

import com.tripbora.service.destination.DestinationDuplicateCheck;

import java.util.List;

/**
 * 선택 일괄등록 결과. 항목별로 독립 처리하므로 한 건이 실패해도 나머지는 그대로 남는다.
 */
public record KtoTourBulkImportResponse(
        int successCount,
        int duplicateCount,
        int failureCount,
        int possibleDuplicateCount,
        List<ItemResult> results
) {
    public static KtoTourBulkImportResponse of(List<ItemResult> results) {
        List<ItemResult> immutableResults = List.copyOf(results);
        return new KtoTourBulkImportResponse(
                count(immutableResults, Status.SUCCESS),
                count(immutableResults, Status.DUPLICATE),
                count(immutableResults, Status.FAILED),
                count(immutableResults, Status.POSSIBLE_DUPLICATE),
                immutableResults);
    }

    private static int count(List<ItemResult> results, Status status) {
        return (int) results.stream().filter(result -> result.status() == status).count();
    }

    public enum Status {
        /** 새 여행지로 저장됐다. */
        SUCCESS,
        /** 이미 등록된 여행지(같은 contentId 등)라 건너뛰었다. */
        DUPLICATE,
        /** 저장 직전 이름·위치가 같은 기존 여행지를 찾았는데 관리자가 확인하지 않은 후보라 건너뛰었다. */
        POSSIBLE_DUPLICATE,
        /** 조회·매핑·저장 중 실패했다. */
        FAILED
    }

    public record ItemResult(
            String contentId,
            String title,
            Status status,
            String message,
            Long destinationId
    ) {
        public static ItemResult success(String contentId, String title, Long destinationId) {
            return new ItemResult(contentId, title, Status.SUCCESS, null, destinationId);
        }

        public static ItemResult duplicate(String contentId, String title) {
            return duplicate(contentId, title, null);
        }

        /** @param destinationId 이미 등록된 기존 여행지. 확인할 수 없으면 null */
        public static ItemResult duplicate(String contentId, String title, Long destinationId) {
            return new ItemResult(contentId, title, Status.DUPLICATE, "이미 등록된 여행지입니다.", destinationId);
        }

        public static ItemResult possibleDuplicate(String contentId, String title, DestinationDuplicateCheck check) {
            return new ItemResult(contentId, title, Status.POSSIBLE_DUPLICATE,
                    "기존 여행지 #" + check.destinationId()
                            + (check.destinationName() == null ? "" : " " + check.destinationName())
                            + "와 중복일 수 있습니다 (" + check.message() + "). 확인 후 다시 선택해 주세요.",
                    check.destinationId());
        }

        public static ItemResult failed(String contentId, String title, String message) {
            return new ItemResult(contentId, title, Status.FAILED, message, null);
        }
    }
}
