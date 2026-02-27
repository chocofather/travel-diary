package com.example.travlediary.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BoardListDto {
    private Long id;
    private String boardType;      // "post" or "course"
    private String postType;       // (post만) "tip" "question"
    private String title;
    private int commentCount;
    private String nickname;
    private String createdAt;
    private int views;
}
