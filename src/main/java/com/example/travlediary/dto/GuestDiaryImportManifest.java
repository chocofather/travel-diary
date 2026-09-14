package com.example.travlediary.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 브라우저에 있던 체험 여행일기를 옮겨 담기 위해 보내오는 값.
 *
 * <p>전부 신뢰할 수 없는 입력이다. localStorage 와 IndexedDB 는 사용자가 마음대로 고칠 수 있으므로
 * 여기에 담긴 값은 "요청" 일 뿐이고, 무엇을 저장할지는 서버가 다시 판단한다.
 * 그래서 이 자리에는 검증 애너테이션을 달지 않고 값만 받는다. 판정은 import 서비스 한 곳에서 한다.
 *
 * <p>소유자는 이 값에 담기지 않는다. 누구의 여행일기가 되는지는 로그인 정보로만 정해진다.
 * 모르는 칸이 섞여 와도 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GuestDiaryImportManifest(
        String title,
        String startDate,
        String endDate,
        String notebookType,
        String coverType,
        String coverStyle,
        Cover coverDesign,
        List<Page> pages,
        /**
         * 사진 참조 → 함께 올라온 파일 이름표.
         * 올라오는 차례에 기대지 않고 이 표로만 짝을 짓는다.
         */
        Map<String, String> photoParts) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cover(
            String baseCoverStyle,
            String backgroundColor,
            List<Element> elements) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(
            String pageDate,
            String backgroundType,
            String paperColor,
            String pageHeader,
            String pageHeaderFont,
            Boolean pageHeaderBold,
            String content,
            List<Element> elements) {
    }

    /**
     * 요소 한 개.
     *
     * <p>{@code imageUrl} 은 스티커에만 쓴다. 사진은 {@code photoRef} 로 올라온 파일을 가리키며,
     * 브라우저가 말한 주소는 쓰지 않는다. 저장되는 주소는 서버가 파일을 저장하고 새로 만든다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Element(
            String elementType,
            String textContent,
            String imageUrl,
            String photoRef,
            String styleType,
            String colorType,
            String photoStyle,
            String textFont,
            String textColor,
            BigDecimal positionX,
            BigDecimal positionY,
            BigDecimal width,
            BigDecimal height,
            BigDecimal rotation,
            Integer zIndex) {
    }
}
