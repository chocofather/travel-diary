package com.tripbora.service.travelinfo.structured;

import java.util.ArrayList;
import java.util.List;

/**
 * 구조화 콘텐츠 이미지 한 장의 저작권·출처 정보. 출처표시가 필요한 이미지에만 두고, 없으면 image 의 credit 이 null 이다.
 * (출처표시 여부는 이 값이 있는지로 정한다)
 *
 * <p>사진 자체의 정보라 이미지와 함께 언어와 상관없이 쓰며 번역하지 않는다. 라이선스는 정해진 코드가 아니라 자유 글이다.
 * 저작자·출처명 중 하나는 있어야 한다는 규칙, 길이, URL 형식(http / https)은 {@link StructuredContentValidator} 가 본다.
 * 그래서 record 는 공백뿐인 값만 null 로 맞춘다.
 */
public record StructuredImageCredit(String author, String source, String sourceUrl,
                                    String license, String licenseUrl) {

    public StructuredImageCredit {
        author = StructuredJson.blankToNull(author);
        source = StructuredJson.blankToNull(source);
        sourceUrl = StructuredJson.blankToNull(sourceUrl);
        license = StructuredJson.blankToNull(license);
        licenseUrl = StructuredJson.blankToNull(licenseUrl);
    }

    /**
     * 공개 화면 출처표시 한 줄의 조각. 저작자 · 출처명 · 라이선스 순서이고 빈 칸은 뺀다.
     * 출처명은 출처 URL, 라이선스는 라이선스 URL 이 있으면 링크로 그린다. (저작자는 링크가 없다)
     * 저작자와 출처명이 같으면 한 번만 쓴다. 이때 링크를 가질 수 있는 출처명 쪽을 남긴다.
     * (Jackson 속성 이름 규칙에 맞지 않아 JSON 에는 쓰이지 않는다)
     */
    public List<Part> displayParts() {
        List<Part> parts = new ArrayList<>(3);
        if (author != null && !author.equals(source)) {
            parts.add(new Part(author, null));
        }
        if (source != null) {
            parts.add(new Part(source, sourceUrl));
        }
        if (license != null) {
            parts.add(new Part(license, licenseUrl));
        }
        return List.copyOf(parts);
    }

    /** 출처표시 한 조각. url 이 있으면 화면이 새 창 링크로 그린다. */
    public record Part(String text, String url) {
    }
}
