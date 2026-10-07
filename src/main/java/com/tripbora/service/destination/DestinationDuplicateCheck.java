package com.tripbora.service.destination;

/**
 * 후보 한 건의 중복 판별 결과. 목록 화면과 저장 직전 확인이 같은 값을 쓴다.
 *
 * @param destinationId   같은 곳으로 본 기존 여행지. 미등록이면 null
 * @param destinationName 그 여행지의 한국어(없으면 다른 언어) 이름
 * @param distanceMeters  좌표로 비교했을 때의 거리(m). 좌표 비교가 아니면 null
 * @param message         관리자 화면에 보여줄 근거 한 줄
 */
public record DestinationDuplicateCheck(
        DestinationDuplicateStatus status,
        DestinationDuplicateReason reason,
        Long destinationId,
        String destinationName,
        Integer distanceMeters,
        String message
) {
    public static final DestinationDuplicateCheck NOT_REGISTERED = new DestinationDuplicateCheck(
            DestinationDuplicateStatus.NOT_REGISTERED, null, null, null, null, null);

    static DestinationDuplicateCheck registered(DestinationDuplicateReason reason, Long destinationId,
                                                String destinationName) {
        return new DestinationDuplicateCheck(DestinationDuplicateStatus.REGISTERED, reason,
                destinationId, destinationName, null, reason.label());
    }

    static DestinationDuplicateCheck possibleDuplicate(DestinationDuplicateReason reason, Long destinationId,
                                                       String destinationName, Integer distanceMeters) {
        String message = distanceMeters == null ? reason.label()
                : reason.label() + " (약 " + formatDistance(distanceMeters) + ")";
        return new DestinationDuplicateCheck(DestinationDuplicateStatus.POSSIBLE_DUPLICATE, reason,
                destinationId, destinationName, distanceMeters, message);
    }

    /** 확정 중복. 다시 등록하지 않는다. */
    public boolean confirmed() {
        return status == DestinationDuplicateStatus.REGISTERED;
    }

    /** 관리자 확인이 필요한 중복 가능성. */
    public boolean needsReview() {
        return status == DestinationDuplicateStatus.POSSIBLE_DUPLICATE;
    }

    private static String formatDistance(int meters) {
        return meters < 1000 ? meters + "m" : String.format("%.1fkm", meters / 1000.0);
    }
}
