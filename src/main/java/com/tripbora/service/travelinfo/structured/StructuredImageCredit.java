package com.tripbora.service.travelinfo.structured;

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
}
