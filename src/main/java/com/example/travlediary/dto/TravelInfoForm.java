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

    /**
     * 언어별 번역 입력 슬롯. 0번은 한국어 자리이고 화면에 그리지 않는다.
     * 한국어는 위쪽 제목·본문 입력이 그대로 base 이자 ko 번역이 된다.
     */
    private List<TravelInfoTranslationForm> translations =
            TravelInfoTranslationForm.newTranslationSlots();

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
