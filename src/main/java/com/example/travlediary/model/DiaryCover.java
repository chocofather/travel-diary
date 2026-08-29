package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * diary_covers 한 행. 특정 다이어리에 실제로 적용된 커스텀 표지다. (한 다이어리에 하나)
 *
 * <p>저장 디자인({@link DiaryCoverDesign})을 적용할 때 값을 복사해 만든 독립본이라
 * 원본 디자인과 FK 로 이어져 있지 않다. 원본을 고치거나 지워도 여기 표지는 그대로다.
 *
 * <p>이 행이 있으면 커스텀 표지, 없으면 예전 그대로 diaries.cover_style + cover_image_url 로
 * 그리는 기본 표지다. 표지 종류를 가리키는 별도 컬럼은 두지 않는다.
 */
@Data
@NoArgsConstructor
public class DiaryCover {

    private Long id; // 표지 번호 (PK)
    private Long diaryId; // 다이어리 번호 (한 다이어리에 하나)
    private String baseCoverStyle; // 바탕으로 쓴 기본 표지 스타일
    private String backgroundColor; // 표지 바탕색 (#RRGGBB, 없으면 기본색)
    private Timestamp createdAt; // 생성일
    private Timestamp updatedAt; // 수정일

    /**
     * 바탕 표지의 CSS 클래스. (LEATHER_BLACK → diary-cover-leather-black)
     * 보관함 원본과 이름을 맞춰 두어 같은 표지 렌더링 조각을 그대로 쓴다.
     */
    public String getBaseCoverStyleClass() {
        return DiaryCoverStyle.toCssClass(baseCoverStyle);
    }
}
