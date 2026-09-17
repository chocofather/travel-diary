package com.example.travlediary.dto;

import com.example.travlediary.model.DiaryCoverLibraryPhotoShareMode;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/** 내 표지 디자인을 라이브러리에 공유할 때 화면에서 받는 값. */
@Getter
@Setter
public class DiaryCoverLibraryShareForm {

    private String title;
    private String description;
    private Map<Long, DiaryCoverLibraryPhotoShareMode> photoModes = new LinkedHashMap<>();
    private boolean rightsConfirmed;
}
