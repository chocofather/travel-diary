package com.example.travlediary.dto;

import com.example.travlediary.model.InfoCategory;
import lombok.Data;

@Data
public class InfoCategoryForm {

    private String name;
    private Integer displayOrder = 1;
    private Boolean isVisible = true;

    public static InfoCategoryForm from(InfoCategory category) {
        InfoCategoryForm form = new InfoCategoryForm();
        form.setName(category.getName());
        form.setDisplayOrder(category.getDisplayOrder());
        form.setIsVisible(category.getIsVisible());
        return form;
    }
}
