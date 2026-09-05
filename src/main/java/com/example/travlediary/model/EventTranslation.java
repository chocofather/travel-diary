package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 상단 메뉴 이벤트(사이트 프로모션/서비스 이벤트)의 언어별 공개 콘텐츠.
 *
 * <p>제목·본문과 함께 포스터 경로까지 담는다. 인포그래픽 이벤트는 안내 문구가
 * 이미지 안에 들어 있어 언어별 포스터가 따로 필요하기 때문이다.
 * 대표 이미지(event_img)는 언어와 무관한 공용 이미지라 여기에 담지 않는다.
 */
@Data
@NoArgsConstructor
public class EventTranslation {
    private Long id; // 이벤트 다국어 번호
    private Long eventId; // 이벤트 번호
    private String languageCode; // 언어 코드 예: 'ko', 'en'
    private String title; // 이벤트 다국어 제목
    private String description; // 이벤트 다국어 본문
    private String posterImg; // 언어별 인포그래픽 포스터 경로
}
