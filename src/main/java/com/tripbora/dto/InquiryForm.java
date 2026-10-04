package com.tripbora.dto;

import com.tripbora.model.InquiryType;
import lombok.Data;

@Data
public class InquiryForm {
    private InquiryType inquiryType;
    private String subject;
    private String content;
}
