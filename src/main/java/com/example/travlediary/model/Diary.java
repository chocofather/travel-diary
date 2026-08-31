package com.example.travlediary.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * diaries 한 행. 여행 한 번이 다이어리 한 권이다.
 */
@Data
@NoArgsConstructor
public class Diary {

    private Long id; // 다이어리 번호 (PK)
    private Long userId; // 회원 번호
    private String title; // 제목
    private LocalDate startDate; // 여행 시작일
    private LocalDate endDate; // 여행 종료일
    private String coverImageUrl; // 표지 이미지 경로
    private String coverStyle; // 표지 스타일
    private String notebookType; // 다이어리 내부(속지) 타입 - CLASSIC / SPIRAL
    /**
     * 4자리 PIN 잠금의 해시. 값이 있으면 잠긴 다이어리다.
     * 화면으로는 어떤 형태로도 내보내지 않는다. (필요한 것은 {@link #isPinEnabled()} 하나뿐이다)
     */
    @JsonIgnore
    private String pinHash;
    private Timestamp createdAt; // 생성일
    private Timestamp updatedAt; // 수정일

    /**
     * PIN 잠금이 걸린 다이어리인지. 별도의 사용 여부 칸을 두지 않고 해시의 유무로만 판단한다.
     * (화면은 이 값만 보고 자물쇠를 그린다 — 해시 자체는 보지 않는다)
     */
    public boolean isPinEnabled() {
        return pinHash != null && !pinHash.isBlank();
    }
}
