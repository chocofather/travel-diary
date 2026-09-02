package com.example.travlediary.dto;

import com.example.travlediary.model.InfoImage;

import java.util.List;

public record FestivalEditData(FestivalEditForm form, List<InfoImage> images) {

    public FestivalEditData {
        images = images == null ? List.of() : List.copyOf(images);
    }
}
