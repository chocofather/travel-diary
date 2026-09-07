package com.example.travlediary.dto;

import com.example.travlediary.model.DestinationType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 여행지 카테고리 등록/수정 폼.
 * categories 와 category_destination_types 를 한 화면에서 다루기 위한 구조다.
 */
@Data
public class CategoryForm {
    private Long id;      // 수정 시에만 사용. 신규 등록에서는 null
    private String name;  // categories.name

    /** category_destination_types. AmenityForm 과 같이 enum 을 그대로 바인딩한다. */
    private List<DestinationType> destinationTypes = new ArrayList<>();

    /**
     * category_translations. 언어별 카테고리명 입력 슬롯이다.
     * 0번은 한국어 자리이고 화면에 그리지 않는다 — 한국어는 위의 name 이 그대로 원문이다.
     */
    private List<CategoryTranslationForm> translations =
            CategoryTranslationForm.newTranslationSlots();
}
