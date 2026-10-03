package com.example.travlediary.service.travelinfo.structured;

/** 테스트에서 Spring 없이 실제 구조화 콘텐츠 처리기를 만든다. (상태가 없는 부품들이라 실제 구현을 그대로 쓴다) */
public final class StructuredContentTestSupport {

    private StructuredContentTestSupport() {
    }

    public static StructuredContentService structuredContentService() {
        StructuredContentValidator validator = new StructuredContentValidator();
        return new StructuredContentService(
                new StructuredContentParser(validator),
                validator,
                new StructuredContentSerializer(),
                new StructuredContentTextRenderer(),
                new StructuredContentLocalizer());
    }
}
