package com.example.travlediary.model;

import lombok.Data;

import java.beans.Transient;
import java.util.List;

@Data
public class CountryCategory {
    private Long id; // 지역 카테고리 번호
    private String regionName; // 한귝명 지역
    private String nameEn; // 영어명
    private String iconPath; // 아이콘 url
    private String code; // 지역코드
    private Long parentId; // 부모 카테고리 번호 예 : 국내/해외 -> 국가 -> 도시
    private Integer depth; // 계층 1대륙 2국가 3도시 4행정구
    private String subregion; // 하위 지역 : 동아시아, 동유럽 등등
    private Integer isVisible = 1; // 항상 기본값 1 (보임)

    // DB에 저장되지 않고 JSON에서만 사용하는 계층용 필드
    private List<CountryCategory> children;
}
