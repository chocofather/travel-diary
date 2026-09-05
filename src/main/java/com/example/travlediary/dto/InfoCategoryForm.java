package com.example.travlediary.dto;

import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.TravelInfoContentType;
import lombok.Data;

import java.util.List;

@Data
public class InfoCategoryForm {

    private String name;
    private TravelInfoContentType contentType = TravelInfoContentType.GENERAL;
    private Integer displayOrder = 1;
    private Boolean isVisible = true;

    /**
     * 언어별 카테고리명 입력 슬롯. 0번은 한국어 자리이고 화면에 그리지 않는다.
     * 한국어는 위쪽 카테고리명 입력이 그대로 base 이자 ko 번역이 된다.
     */
    private List<InfoCategoryTranslationForm> translations =
            InfoCategoryTranslationForm.newTranslationSlots();

    public static InfoCategoryForm from(InfoCategory category) {
        InfoCategoryForm form = new InfoCategoryForm();
        form.setName(category.getName());
        form.setContentType(category.getContentType());
        form.setDisplayOrder(category.getDisplayOrder());
        form.setIsVisible(category.getIsVisible());
        return form;
    }
}
