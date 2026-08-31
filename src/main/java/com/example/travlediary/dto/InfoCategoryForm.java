package com.example.travlediary.dto;

import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.TravelInfoContentType;
import lombok.Data;

@Data
public class InfoCategoryForm {

    private String name;
    private TravelInfoContentType contentType = TravelInfoContentType.GENERAL;
    private Integer displayOrder = 1;
    private Boolean isVisible = true;

    public static InfoCategoryForm from(InfoCategory category) {
        InfoCategoryForm form = new InfoCategoryForm();
        form.setName(category.getName());
        form.setContentType(category.getContentType());
        form.setDisplayOrder(category.getDisplayOrder());
        form.setIsVisible(category.getIsVisible());
        return form;
    }
}
