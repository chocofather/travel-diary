package com.example.travlediary.dto.wikidata;

import java.util.List;

public record WikipediaDescriptionPreview(String qid, List<LanguageEntry> languages) {
    /**
     * @param title        Wikipedia 문서의 실제 제목. 출처 기록에 쓴다.
     * @param displayTitle 이 언어 입력칸에 보여줄 제목. 중국어 변형은 Wikipedia가 변환한 해당 변형 제목만 쓰고,
     *                     변환 제목이 없으면 null이다(원래 제목을 다른 변형 제목으로 쓰지 않는다).
     */
    public record LanguageEntry(String language, String status, String title, String description,
                                int length, String sourceUrl, String sourceLanguage, String variant,
                                String licenseName, String licenseUrl, Long revisionId, String message,
                                String displayTitle) {
    }
}
