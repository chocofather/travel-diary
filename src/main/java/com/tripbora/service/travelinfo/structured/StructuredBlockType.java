package com.tripbora.service.travelinfo.structured;

/**
 * 구조화 콘텐츠 블록 종류. JSON 의 {@code type} 값과 이름이 같다.
 * (JSON 의 type 을 실제로 읽는 쪽은 {@link StructuredBlock} 의 하위 형식 등록이다)
 *
 * <p>label 은 관리자 검증 오류 문구에서 블록을 가리킬 때 쓴다.
 */
public enum StructuredBlockType {
    SECTION_TITLE("섹션 제목"),
    RICH_TEXT("본문"),
    FULL_IMAGE("큰 이미지"),
    IMAGE_TEXT("이미지 + 글"),
    IMAGE_SLIDER("이미지 슬라이더"),
    IMAGE_GRID("이미지 배치"),
    CALLOUT("강조 문구");

    private final String label;

    StructuredBlockType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
