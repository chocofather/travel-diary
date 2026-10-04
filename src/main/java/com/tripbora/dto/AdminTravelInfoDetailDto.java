package com.tripbora.dto;

import com.tripbora.model.InfoPeriod;
import com.tripbora.model.TravelInfoContentFormat;
import com.tripbora.model.TravelInfoContentType;
import com.tripbora.model.TravelInfoScope;
import com.tripbora.service.travelinfo.structured.StructuredContent;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

@Data
public class AdminTravelInfoDetailDto {

    private Long id;
    private String title;
    private String content;
    private TravelInfoContentFormat contentFormat = TravelInfoContentFormat.QUILL;
    /** STRUCTURED 글의 검사를 마친 원문 블록. QUILL 이면 null. */
    private StructuredContent structuredContent;
    private TravelInfoScope scope;
    private TravelInfoContentType contentType;
    private Long categoryId;
    private String categoryName;
    private Integer views;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private List<InfoPeriod> periods = List.of();
}
