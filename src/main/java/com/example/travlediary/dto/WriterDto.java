package com.example.travlediary.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WriterDto {
    private Long id;
    private String nickname;
    private String profileImage;
    /** 최종 탈퇴(DEACTIVATED) 회원이면 true. 공개 프로필이 없으므로 링크도 걸지 않는다. */
    private boolean withdrawn;

    @JsonProperty("isWriter")
    private Boolean isWriter; // 원글 작성자인지 여부

}
