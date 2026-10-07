package com.tripbora.dto.kto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record KtoTourBulkImportRequest(
        @NotEmpty(message = "등록할 여행지를 선택해 주세요.")
        @Size(max = 50, message = "한 번에 최대 50건까지 등록할 수 있습니다.")
        @Valid List<Item> items
) {
    /**
     * @param allowPossibleDuplicate 목록에서 '중복 확인'으로 표시된 후보를 관리자가 확인하고 고른 경우 true.
     *                               확정 중복(같은 contentId 등)은 이 값과 상관없이 건너뛴다.
     */
    public record Item(
            @NotBlank @Size(max = 100) String contentId,
            @NotBlank @Size(max = 10) String contentTypeId,
            Boolean allowPossibleDuplicate
    ) {
        public Item(String contentId, String contentTypeId) {
            this(contentId, contentTypeId, null);
        }

        public boolean possibleDuplicateAllowed() {
            return Boolean.TRUE.equals(allowPossibleDuplicate);
        }
    }
}
