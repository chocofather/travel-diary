package com.tripbora.dto;

import com.tripbora.model.InfoPeriod;
import com.tripbora.model.TravelInfo;
import com.tripbora.model.TravelInfoContentFormat;
import com.tripbora.model.TravelInfoContentType;
import com.tripbora.model.TravelInfoScope;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Data
public class TravelInfoForm {

    /*
      관리자 화면의 '콘텐츠 구분' 값. 저장 구조(contentType + scope)는 그대로 두고 화면에서만 하나로 고른다.
        국내 여행정보 → GENERAL + DOMESTIC
        해외 여행정보 → GENERAL + INTERNATIONAL
        여행가이드   → GUIDE (scope 는 서비스가 비운다)
        축제·행사    → FESTIVAL (scope 는 화면의 국내/해외 선택을 그대로 쓴다)
    */
    public static final String SECTION_GENERAL_DOMESTIC = "GENERAL_DOMESTIC";
    public static final String SECTION_GENERAL_INTERNATIONAL = "GENERAL_INTERNATIONAL";
    public static final String SECTION_GUIDE = "GUIDE";
    public static final String SECTION_FESTIVAL = "FESTIVAL";

    private String title;
    private String content;
    /** 본문 작성 방식. 저장 흐름은 아직 QUILL 만 쓴다. (STRUCTURED 연결은 다음 단계) */
    private TravelInfoContentFormat contentFormat = TravelInfoContentFormat.QUILL;
    /** STRUCTURED 블록 원본 JSON. QUILL 이면 쓰지 않는다. */
    private String structuredContent;
    private TravelInfoScope scope;
    private TravelInfoContentType contentType = TravelInfoContentType.GENERAL;
    private Long categoryId;
    private List<InfoPeriodForm> periods = new ArrayList<>();
    private MultipartFile thumbnailFile;
    private boolean removeThumbnail;

    /**
     * 메인 추천 노출. 일반 여행정보와 여행가이드만 쓴다.
     * 체크는 노출 여부만 정하고, 순서는 체크와 상관없이 입력한 값을 저장한다.
     */
    private boolean homeFeatured;
    private Integer homeFeaturedOrder = 1;

    /** 저장된 contentType + scope 로 화면의 콘텐츠 구분을 복원한다. 국내/해외가 없는 일반 정보는 고르지 않는다. */
    public String getContentSection() {
        if (contentType == null) {
            return null;
        }
        return switch (contentType) {
            case GUIDE -> SECTION_GUIDE;
            case FESTIVAL -> SECTION_FESTIVAL;
            case GENERAL -> scope == TravelInfoScope.DOMESTIC ? SECTION_GENERAL_DOMESTIC
                    : scope == TravelInfoScope.INTERNATIONAL ? SECTION_GENERAL_INTERNATIONAL
                    : null;
        };
    }

    /**
     * 화면의 콘텐츠 구분을 contentType + scope 로 옮긴다.
     * 축제·행사는 국내/해외를 따로 고르므로 scope 를 건드리지 않는다.
     * 모르는 값이면 유형을 비워 서비스 검증이 막게 한다.
     */
    public void setContentSection(String contentSection) {
        switch (contentSection == null ? "" : contentSection) {
            case SECTION_GENERAL_DOMESTIC -> {
                contentType = TravelInfoContentType.GENERAL;
                scope = TravelInfoScope.DOMESTIC;
            }
            case SECTION_GENERAL_INTERNATIONAL -> {
                contentType = TravelInfoContentType.GENERAL;
                scope = TravelInfoScope.INTERNATIONAL;
            }
            case SECTION_GUIDE -> contentType = TravelInfoContentType.GUIDE;
            case SECTION_FESTIVAL -> contentType = TravelInfoContentType.FESTIVAL;
            default -> contentType = null;
        }
    }

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
        if (travelInfo.getContentFormat() != null) {
            form.setContentFormat(travelInfo.getContentFormat());
        }
        form.setStructuredContent(travelInfo.getStructuredContent());
        form.setScope(travelInfo.getScope());
        form.setContentType(travelInfo.getContentType());
        form.setCategoryId(travelInfo.getCategoryId());
        form.setHomeFeatured(Boolean.TRUE.equals(travelInfo.getHomeFeatured()));
        if (travelInfo.getHomeFeaturedOrder() != null) {
            form.setHomeFeaturedOrder(travelInfo.getHomeFeaturedOrder());
        }
        form.setPeriods(periods == null
                ? new ArrayList<>()
                : periods.stream().map(InfoPeriodForm::from).toList());
        return form;
    }
}
