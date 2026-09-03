package com.example.travlediary.dto;

import com.example.travlediary.model.DestinationType;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 편의시설 등록/수정 통합 폼.
 * 한 화면에서 amenities / amenity_translations / amenity_destination_types 를 함께 다룬다.
 * code 는 아이콘 파일명과 연결되므로 수정 화면에서는 readonly 로 쓴다.
 */
@Data
@NoArgsConstructor
public class AmenityForm {
    private Integer id;      // 수정 시에만 사용. 신규 등록에서는 null
    private String code;     // amenities.code

    /**
     * amenity_translations. ko 는 필수, 나머지는 선택이며 비우면 저장하지 않는다.
     * 중국어는 간체(zh-CN)와 번체(zh-TW)를 각각 따로 받는다.
     */
    private String nameKo;
    private String nameEn;
    private String nameJa;
    private String nameZhCn;
    private String nameZhTw;

    /** amenity_destination_types. DestinationForm.type 과 같이 enum 을 그대로 바인딩한다. */
    private List<DestinationType> destinationTypes = new ArrayList<>();

    /** 상세 페이지 아이콘. 신규 등록에서는 필수이며 PNG 만 받는다. */
    private MultipartFile icon;
}
