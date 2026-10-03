package com.example.travlediary.model;

/**
 * 여행정보 본문 작성 방식. DB 의 {@code travel_info.content_format} 에 이름 그대로 저장한다.
 *
 * <p>QUILL 은 {@code content}(Quill HTML)가 원본이다. STRUCTURED 는 {@code structured_content}(블록 JSON)가
 * 원본이고, {@code content} 에는 서버가 만든 검색/SEO 용 파생 HTML 을 둔다.
 * 기존 글과 신규 기본값은 QUILL 이다. DB 에 이 밖의 값이 있으면 조용히 QUILL 로 보지 않고
 * 매핑 단계에서 실패시킨다. (MyBatis 기본 enum 변환)
 */
public enum TravelInfoContentFormat {
    QUILL,
    STRUCTURED
}
