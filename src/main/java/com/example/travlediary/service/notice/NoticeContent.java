package com.example.travlediary.service.notice;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * 공지사항 본문(Quill HTML)이 실질적으로 값을 가졌는지 판정한다.
 *
 * <p>{@code <p><br></p>} 처럼 태그만 남은 본문은 빈 값으로 본다. 반대로 글이 없어도
 * 이미지가 들어 있으면 값이 있는 것으로 본다. 등록/수정 검증과 공개 화면의 언어 대체가
 * 같은 기준을 쓰도록 판정을 한곳에 둔다. (여행정보의 TravelInfoContent 와 같은 자리다)
 */
public final class NoticeContent {

    private NoticeContent() {
    }

    /** 글이나 이미지가 하나라도 있으면 true. */
    public static boolean hasContent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        Document document = Jsoup.parseBodyFragment(content);
        return !document.text().strip().isEmpty() || !document.select("img[src]").isEmpty();
    }
}
