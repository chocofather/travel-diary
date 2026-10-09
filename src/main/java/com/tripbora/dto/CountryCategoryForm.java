package com.tripbora.dto;

import lombok.Data;

/**
 * 관리자 지역 등록 입력.
 *
 * <p>depth 는 받지 않는다. 서버가 부모 지역을 기준으로 정한다.
 * 한국어명·영어명은 기본 정보이자 ko/en 번역이 되므로 번역 입력은 나머지 세 언어만 받는다.
 * code 는 국가·시/도처럼 코드가 필요한 계층에서만 쓰고, 그 밖의 계층에서는 저장하지 않는다.
 */
@Data
public class CountryCategoryForm {

    private Long parentId;
    private String regionName;
    private String nameEn;
    private String code;

    private String nameJa;
    private String nameZhCn;
    private String nameZhTw;
}
