package com.example.travlediary.dto;

import com.example.travlediary.model.TravelInfoScope;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@Data
public class FestivalCreateForm {

    private String title;
    private String content;
    private TravelInfoScope scope = TravelInfoScope.DOMESTIC;
    private Long categoryId;
    // input type="date" 는 ISO(yyyy-MM-dd) 값만 읽고 쓴다. 등록 실패로 화면을 다시 그릴 때도
    // 입력해 둔 날짜가 그대로 남아야 한다.
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
    private String eventPlace;
    private String address;
    private String playTime;
    private String useTime;
    private String sponsor1;
    private String sponsor1Tel;
    private String sponsor2;
    private String sponsor2Tel;
    private String contactTel;
    private String homepageUrl;
    private String ktoFestivalContentId;
    private String ktoThumbnailImageSelection;

    /**
     * travel_info 제목·본문의 언어별 입력 슬롯. 0번은 한국어 자리이고 화면에 그리지 않는다.
     * 한국어는 위쪽 제목·본문 입력이 그대로 base 이자 ko 번역이 된다.
     * (행사 상세정보 번역은 후속 단계다)
     */
    private List<TravelInfoTranslationForm> translations =
            TravelInfoTranslationForm.newTranslationSlots();

    /**
     * 행사 상세정보의 언어별 입력 슬롯. travel_info 번역과 같은 언어 탭 안에서 함께 편집한다.
     * 0번은 한국어 자리이고 화면에 그리지 않는다.
     */
    private List<FestivalInfoTranslationForm> festivalInfoTranslations =
            FestivalInfoTranslationForm.newTranslationSlots();
}
