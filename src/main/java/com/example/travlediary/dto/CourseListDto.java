package com.example.travlediary.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CourseListDto {
    private Long id;               // 코스 PK
    private String title;          // 제목
    private int commentCount;      // 댓글 수
    private String nickname;       // 작성자 닉네임
    private String createdAt;      // 작성일
    private int views;             // 조회수

}
