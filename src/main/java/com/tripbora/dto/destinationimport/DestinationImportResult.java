package com.tripbora.dto.destinationimport;

import com.tripbora.service.destination.DestinationDuplicateCheck;

import java.util.List;

/**
 * JSON 일괄등록에서 여행지 한 건을 등록한 결과. 실패도 예외 대신 이 결과로 돌려 다른 행 진행을 막지 않는다.
 *
 * <ul>
 *   <li>SUCCESS: 저장했다. destinationId 가 새 여행지 번호다</li>
 *   <li>REGISTERED: 이미 등록된 여행지라 저장하지 않았다</li>
 *   <li>POSSIBLE_DUPLICATE: 같은 곳일 수 있는 여행지가 있는데 확인하지 않아 저장하지 않았다</li>
 *   <li>INVALID: 다시 검증해 보니 오류가 있어 저장하지 않았다</li>
 *   <li>FAILED: 외부 재검증·저장 중 실패했다. 아무것도 남기지 않았다</li>
 * </ul>
 */
public record DestinationImportResult(
        int index,
        String key,
        String status,
        Long destinationId,
        String message,
        DestinationDuplicateCheck duplicate,
        List<DestinationImportIssue> errors
) {
    public static final String SUCCESS = "SUCCESS";
    public static final String REGISTERED = "REGISTERED";
    public static final String POSSIBLE_DUPLICATE = "POSSIBLE_DUPLICATE";
    public static final String INVALID = "INVALID";
    public static final String FAILED = "FAILED";

    public DestinationImportResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static DestinationImportResult success(int index, String key, Long destinationId) {
        return new DestinationImportResult(index, key, SUCCESS, destinationId, "등록했습니다.", null, List.of());
    }

    public static DestinationImportResult registered(int index, String key, DestinationDuplicateCheck duplicate) {
        return new DestinationImportResult(index, key, REGISTERED, duplicate.destinationId(),
                "이미 등록된 여행지입니다.", duplicate, List.of());
    }

    public static DestinationImportResult possibleDuplicate(int index, String key, DestinationDuplicateCheck duplicate) {
        return new DestinationImportResult(index, key, POSSIBLE_DUPLICATE, duplicate.destinationId(),
                "같은 곳일 수 있는 여행지가 있습니다. 다른 여행지임을 확인한 뒤 다시 등록해 주세요.", duplicate, List.of());
    }

    public static DestinationImportResult invalid(int index, String key, List<DestinationImportIssue> errors) {
        return new DestinationImportResult(index, key, INVALID, null, "검증 오류가 있어 등록하지 않았습니다.", null, errors);
    }

    public static DestinationImportResult failed(int index, String key, String message) {
        return new DestinationImportResult(index, key, FAILED, null, message, null, List.of());
    }
}
