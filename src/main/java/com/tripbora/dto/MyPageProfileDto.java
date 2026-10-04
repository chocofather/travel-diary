package com.tripbora.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MyPageProfileDto {
    private String nickname;
    private String userEmail;
    private String profileImage;
}
