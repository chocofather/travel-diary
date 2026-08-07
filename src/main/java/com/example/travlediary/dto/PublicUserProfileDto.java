package com.example.travlediary.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PublicUserProfileDto {
    private Long id;
    private String nickname;
    private String profileImage;
}
