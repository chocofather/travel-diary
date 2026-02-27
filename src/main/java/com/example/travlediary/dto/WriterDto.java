package com.example.travlediary.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WriterDto {
    private Long id;
    private String nickname;
    private String profileImage;

    @JsonProperty("isWriter")
    private Boolean isWriter; // 원글 작성자인지 여부

}
