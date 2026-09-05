package com.example.travlediary.dto;

import com.example.travlediary.model.InfoImage;

import java.util.List;

/**
 * 축제·행사 수정 화면이 쓰는 값.
 *
 * <p>{@code ktoContentId} 는 국문 TourAPI 축제 식별자({@code festival_info.external_content_id})다.
 * 외국어 자동입력이 좌표를 되찾을 때만 쓰는 읽기 전용 값이며, 수기 등록 축제에서는 비어 있다.
 */
public record FestivalEditData(FestivalEditForm form, List<InfoImage> images,
                               String ktoContentId) {

    public FestivalEditData {
        images = images == null ? List.of() : List.copyOf(images);
        ktoContentId = ktoContentId == null || ktoContentId.isBlank() ? null : ktoContentId.strip();
    }

    /** 국문 TourAPI 에서 온 축제가 아닌 경우. */
    public FestivalEditData(FestivalEditForm form, List<InfoImage> images) {
        this(form, images, null);
    }
}
