package com.example.travlediary.dto;

import com.example.travlediary.model.InquiryType;
import lombok.Data;

@Data
public class InquiryForm {
    private InquiryType inquiryType;
    private String subject;
    private String content;
}
