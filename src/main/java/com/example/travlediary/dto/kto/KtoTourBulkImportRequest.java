package com.example.travlediary.dto.kto;

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
    public record Item(
            @NotBlank @Size(max = 100) String contentId,
            @NotBlank @Size(max = 10) String contentTypeId
    ) {
    }
}
