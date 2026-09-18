package com.example.travlediary.dto;

import com.example.travlediary.model.DiaryPhotoUrls;
import lombok.Data;

import java.time.LocalDate;

/** 일기장형 목록 화면 한 칸. 표지 정보와 페이지 수만 담는다. */
@Data
public class DiaryListItemDto {

    private Long id;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private String coverImageUrl;
    private String coverStyle;
    private int pageCount; // 다이어리에 속한 페이지 수 (없으면 0)
    /**
     * PIN 잠금이 걸린 다이어리인지. 화면은 이 값만 보고 자물쇠를 그린다.
     * (해시 자체는 SQL 에서 이미 참/거짓으로 바뀌어 이 자리까지 오지 않는다)
     */
    private boolean pinEnabled;

    /**
     * 책장 카드가 쓰는 대표 이미지 주소. 저장 키는 내보내지 않는다.
     * (다이어리 상세와 같은 통제된 endpoint 를 쓴다)
     */
    public String getCoverViewUrl() {
        if (id == null || coverImageUrl == null || coverImageUrl.isBlank()) {
            return null;
        }
        return DiaryPhotoUrls.coverImage(id);
    }
}
