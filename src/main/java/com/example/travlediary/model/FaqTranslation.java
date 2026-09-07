package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 자주 묻는 질문의 언어별 질문·답변.
 *
 * <p>한국어 원문은 {@code faqs}.question / answer 가 갖고, 여기에는 en / ja / zh-CN / zh-TW 만 담는다.
 * answer 는 원문과 같이 서식 없는 일반 텍스트다. (HTML 이 아니므로 정제 대상이 아니다)
 */
@Data
@NoArgsConstructor
public class FaqTranslation {
    private Long id; // 자주묻는질문 다국어 번호
    private Long faqId; // 자주묻는질문 번호
    private String languageCode; // 언어 코드 예: 'en', 'ja'
    private String question; // 다국어 질문
    private String answer; // 다국어 답변 (plain text)
}
