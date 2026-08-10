package com.example.travlediary.dto;

import com.example.travlediary.model.InfoPeriod;
import com.example.travlediary.model.TravelInfo;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Data
public class TravelInfoForm {

    private String title;
    private String content;
    private TravelInfoScope scope;
    private TravelInfoContentType contentType = TravelInfoContentType.GENERAL;
    private Long categoryId;
    private List<InfoPeriodForm> periods = new ArrayList<>();
    private MultipartFile thumbnailFile;
    private boolean removeThumbnail;

    public static TravelInfoForm from(TravelInfo travelInfo, List<InfoPeriod> periods) {
        TravelInfoForm form = new TravelInfoForm();
        form.setTitle(travelInfo.getTitle());
        form.setContent(travelInfo.getContent());
        form.setScope(travelInfo.getScope());
        form.setContentType(travelInfo.getContentType());
        form.setCategoryId(travelInfo.getCategoryId());
        form.setPeriods(periods == null
                ? new ArrayList<>()
                : periods.stream().map(InfoPeriodForm::from).toList());
        return form;
    }
}
