package com.example.travlediary.dto;

import lombok.Data;

/**
 * 통제된 사진 응답이 필요한 값 한 줄.
 *
 * <p>사진 한 장을 내보내는 데 다이어리·페이지·요소를 통째로 읽을 이유가 없다.
 * 소유권 확인과 PIN 판단, 저장 키까지 JOIN 한 번으로 이 세 칸만 가져온다.
 * 남의 것이거나 부모 관계가 어긋나면 애초에 행이 나오지 않는다.
 */
@Data
public class DiaryPrivatePhotoRef {

    /** private 저장소를 가리키는 저장 키 (예: /uploads/diary-pages/{uuid}.jpg) */
    private String imageUrl;
    /** 이 사진이 속한 다이어리. PIN 잠금 판단에 쓴다. */
    private Long diaryId;
    /** PIN 해시. 값이 있으면 잠긴 다이어리다. 화면으로는 나가지 않는다. */
    private String pinHash;
}
