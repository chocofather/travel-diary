package com.example.travlediary.dto.kto;

import java.util.List;

/**
 * 선택 일괄등록 결과. 항목별로 독립 처리하므로 한 건이 실패해도 나머지는 그대로 남는다.
 */
public record KtoTourBulkImportResponse(
        int successCount,
        int duplicateCount,
        int failureCount,
        List<ItemResult> results
) {
    public static KtoTourBulkImportResponse of(List<ItemResult> results) {
        List<ItemResult> immutableResults = List.copyOf(results);
        return new KtoTourBulkImportResponse(
                count(immutableResults, Status.SUCCESS),
                count(immutableResults, Status.DUPLICATE),
                count(immutableResults, Status.FAILED),
                immutableResults);
    }

    private static int count(List<ItemResult> results, Status status) {
        return (int) results.stream().filter(result -> result.status() == status).count();
    }

    public enum Status {
        /** 새 여행지로 저장됐다. */
        SUCCESS,
        /** 이미 등록된 contentId 라 건너뛰었다. */
        DUPLICATE,
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
            return new ItemResult(contentId, title, Status.DUPLICATE, "이미 등록된 여행지입니다.", null);
        }

        public static ItemResult failed(String contentId, String title, String message) {
            return new ItemResult(contentId, title, Status.FAILED, message, null);
        }
    }
}
