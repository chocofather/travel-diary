package com.tripbora.dto;

import com.tripbora.model.InquiryStatus;
import com.tripbora.model.InquiryType;
import lombok.Data;

import java.sql.Timestamp;

@Data
public class InquiryDetailDto {
    private Long id;
    private String subject;
    private String content;
    private InquiryStatus status;
    private InquiryType inquiryType;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Long userId;
    private String userDisplayName;
    private Long answerId;
    private String answerContent;
    private Timestamp answerCreatedAt;
    private Timestamp answerUpdatedAt;

    public boolean isPending() {
        return status == InquiryStatus.PENDING;
    }

    public boolean isAnswered() {
        return status == InquiryStatus.ANSWERED;
    }

    public boolean hasAnswer() {
        return answerId != null;
    }
}
