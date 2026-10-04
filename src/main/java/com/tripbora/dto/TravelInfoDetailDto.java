package com.tripbora.dto;

import com.tripbora.model.TravelInfoContentFormat;
import com.tripbora.model.TravelInfoContentType;
import com.tripbora.model.TravelInfoScope;
import com.tripbora.service.travelinfo.structured.StructuredContent;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

@Data
public class TravelInfoDetailDto {

    private Long id;
    private String title;
    private TravelInfoScope scope;
    private TravelInfoContentType contentType;
    private Long categoryId; // 카테고리 이름을 언어별로 바꿀 때 쓴다
    private String categoryName;
    /** QUILL 은 sanitize 한 본문 HTML, STRUCTURED 는 검색/SEO 용 파생 HTML 이다. */
    private String content;
    private TravelInfoContentFormat contentFormat = TravelInfoContentFormat.QUILL;
    /** DB 의 원문 블록 JSON. 화면은 쓰지 않고 Service 가 {@link #structuredContent} 로 읽어 둔다. */
    private String structuredContentJson;
    /** STRUCTURED 글의 검사를 마친(요청 언어로 바꾼) 블록. QUILL 이거나 읽지 못했으면 null. */
    private StructuredContent structuredContent;
    private Integer views;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private List<TravelInfoPeriodDto> periods = List.of();
    private boolean bookmarked;

    public boolean isUpdated() {
        if (createdAt == null || updatedAt == null) {
            return false;
        }
        return updatedAt.toLocalDateTime().toLocalDate()
                .isAfter(createdAt.toLocalDateTime().toLocalDate());
    }
}
