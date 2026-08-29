package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * diary_cover_designs 한 행. 사용자가 꾸며 저장해 둔 "내 표지 디자인" 원본 하나다.
 * 한 사용자가 여러 개를 가질 수 있고 이름은 중복을 허용한다.
 *
 * <p>base_cover_style 은 그 위에서 꾸민 기본 표지 스타일이다. (diaries.cover_style 과 같은 목록)
 * 바탕까지 디자인의 일부라 함께 저장해 두고, 다시 적용할 때 그대로 되살린다.
 *
 * <p>여기 담긴 것은 어디까지나 원본이다. 실제 다이어리에 적용한 표지는 {@link DiaryCover} 로
 * 값을 복사해 두므로, 이 원본을 고치거나 지워도 이미 적용된 표지는 그대로 남는다.
 */
@Data
@NoArgsConstructor
public class DiaryCoverDesign {

    private Long id; // 디자인 번호 (PK)
    private Long userId; // 회원 번호
    private String name; // 디자인 이름
    private String baseCoverStyle; // 바탕으로 쓴 기본 표지 스타일
    private String backgroundColor; // 표지 바탕색 (#RRGGBB, 없으면 기본색)
    private Timestamp createdAt; // 생성일
    private Timestamp updatedAt; // 수정일

    /** 바탕 표지의 CSS 클래스. (LEATHER_BLACK → diary-cover-leather-black) */
    public String getBaseCoverStyleClass() {
        return DiaryCoverStyle.toCssClass(baseCoverStyle);
    }

    /** 바탕 표지의 화면 이름. 저장된 값이 목록에 없으면 기본 표지 이름으로 본다. */
    public String getBaseCoverStyleLabel() {
        return DiaryCoverStyle.labelOf(baseCoverStyle);
    }

    /**
     * 이 디자인이 쓰는 표지 재질. (기본 / 가죽 / 양장)
     * 예전에 만든 "가죽 딥그린" 같은 값도 이 갈래로 읽어 고르는 자리에 표시한다.
     */
    public String getCoverMaterialCode() {
        return DiaryCoverMaterial.of(baseCoverStyle).getCode();
    }
}
