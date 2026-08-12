package com.example.travlediary.dto;

import com.example.travlediary.model.InquiryStatus;
import com.example.travlediary.model.InquiryType;
import lombok.Data;

import java.sql.Timestamp;

@Data
public class InquiryListItemDto {
    private Long id;
    private String subject;
    private InquiryStatus status;
    private InquiryType inquiryType;
    private Timestamp createdAt;
    private Long userId;
    private String userDisplayName;

    public boolean isPending() {
        return status == InquiryStatus.PENDING;
    }

    public boolean isAnswered() {
        return status == InquiryStatus.ANSWERED;
    }
}
