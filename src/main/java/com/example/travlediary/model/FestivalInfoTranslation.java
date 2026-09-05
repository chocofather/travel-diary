package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 축제·행사 상세정보의 언어별 자유 텍스트.
 *
 * <p>연락처·홈페이지·TourAPI 식별자처럼 언어와 무관한 값은 담지 않는다.
 */
@Data
@NoArgsConstructor
public class FestivalInfoTranslation {
    private Long id;
    private Long infoId;
    private String languageCode;
    private String eventPlace;
    private String address;
    private String playTime;
    private String useTime;
    private String sponsor1;
    private String sponsor2;
}
