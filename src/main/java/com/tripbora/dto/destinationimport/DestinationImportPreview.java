package com.tripbora.dto.destinationimport;

import com.tripbora.service.destination.DestinationDuplicateCheck;

import java.util.List;

/**
 * JSON 일괄등록 미리보기. 저장하지 않으며, 등록 요청은 이 결과를 믿지 않고 서버가 다시 검증한다.
 *
 * @param fileErrors 파일 자체의 오류(JSON 문법·크기·건수·최상위 필드). 있으면 행을 만들지 않는다
 */
public record DestinationImportPreview(
        int total,
        Summary summary,
        List<DestinationImportIssue> fileErrors,
        List<Row> rows
) {
    public static DestinationImportPreview failed(List<DestinationImportIssue> fileErrors) {
        return new DestinationImportPreview(0, new Summary(0, 0, 0, 0), List.copyOf(fileErrors), List.of());
    }

    public static DestinationImportPreview of(List<Row> rows) {
        int registrable = 0;
        int possible = 0;
        int registered = 0;
        int invalid = 0;
        for (Row row : rows) {
            switch (row.status()) {
                case NOT_REGISTERED -> registrable++;
                case POSSIBLE_DUPLICATE -> possible++;
                case REGISTERED -> registered++;
                case INVALID -> invalid++;
            }
        }
        return new DestinationImportPreview(rows.size(), new Summary(registrable, possible, registered, invalid),
                List.of(), List.copyOf(rows));
    }

    /** 상단 요약. 등록 가능 = 미등록 상태인 행 수. */
    public record Summary(int registrable, int possibleDuplicate, int registered, int invalid) {
    }

    /**
     * 미리보기 한 행.
     *
     * @param index         destinations 배열 순번(0부터)
     * @param regionLabel   매핑한 지역 경로(예: "대한민국 > 서울 > 종로구"). 매핑 실패면 JSON 값 그대로
     * @param mainCategory  실제로 저장될 대표 카테고리. JSON 에 없으면 기존 규칙으로 고른 값
     * @param sourceType    저장될 등록 출처(KTO_TOURAPI·WIKIDATA·ADMIN)
     * @param duplicate     기존 DB 여행지와의 공통 중복 판별 결과
     * @param fileDuplicate 같은 파일 안 앞 행과의 판별 결과. 없으면 null
     * @param factFields    값이 들어 있는 사실정보 필드(evidence 검수용)
     */
    public record Row(
            int index,
            String path,
            String key,
            String name,
            String regionLabel,
            String type,
            String season,
            List<String> categories,
            String mainCategory,
            String sourceType,
            String externalId,
            DestinationImportRowStatus status,
            DestinationDuplicateCheck duplicate,
            FileDuplicate fileDuplicate,
            List<DestinationImportIssue> errors,
            List<DestinationImportIssue> warnings,
            List<DestinationImportItem.Evidence> evidence,
            List<String> factFields
    ) {
    }

    /**
     * 같은 파일 안에서 앞 행과 같은 곳으로 본 결과.
     *
     * @param index   같은 곳으로 본 앞 행의 순번
     * @param confirmed 같은 외부 ID·Place ID 면 true(오류), 이름·위치만 같으면 false(중복 확인)
     */
    public record FileDuplicate(int index, String key, String name, boolean confirmed, String message) {
    }
}
