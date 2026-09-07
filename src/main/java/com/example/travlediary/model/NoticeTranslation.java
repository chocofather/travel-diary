package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 공지사항의 언어별 제목·본문.
 *
 * <p>한국어 원문은 {@code notices}.title / content 가 갖고, 여기에는 en / ja / zh-CN / zh-TW 만 담는다.
 * content 는 base 와 같은 Quill HTML 이며 저장 전 같은 sanitize 를 거친다.
 */
@Data
@NoArgsConstructor
public class NoticeTranslation {
    private Long id; // 공지사항 다국어 번호
    private Long noticeId; // 공지사항 번호
    private String languageCode; // 언어 코드 예: 'en', 'ja'
    private String title; // 공지사항 다국어 제목
    private String content; // 공지사항 다국어 본문 (Quill HTML)
}
