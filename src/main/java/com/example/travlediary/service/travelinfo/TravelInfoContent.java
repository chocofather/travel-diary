package com.example.travlediary.service.travelinfo;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * 여행정보 본문(Quill HTML)이 실질적으로 값을 가졌는지 판정한다.
 *
 * <p>{@code <p><br></p>} 처럼 태그만 남은 본문은 빈 값으로 본다. 반대로 글이 없어도
 * 이미지가 들어 있으면 값이 있는 것으로 본다. 등록/수정 검증과 언어 대체가 같은 기준을
 * 쓰도록 {@link TravelInfoService} 안에 있던 판정을 여기로 옮겨 두었다.
 */
public final class TravelInfoContent {

    private TravelInfoContent() {
    }

    /** 글이나 이미지가 하나라도 있으면 true. */
    public static boolean hasContent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        Document document = Jsoup.parseBodyFragment(content);
        boolean hasText = !document.text().strip().isEmpty();
        boolean hasImage = !document.select("img[src]").isEmpty();
        return hasText || hasImage;
    }
}
