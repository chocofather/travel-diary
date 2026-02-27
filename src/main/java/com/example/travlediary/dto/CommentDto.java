package com.example.travlediary.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CommentDto {
    private Long id;
    private String content;
    private String imageUrl;
    private String createdAt;
    private String updatedAt;
    private boolean likedByMe; // 현재 유저가 좋아요 눌렀는지
    private int likes;
    private Long parentCommentId;
    private WriterDto writer;

    private boolean myComment; //  현재 로그인한 사용자가 이 댓글의 작성자인가?
    private boolean admin;    // 관리자 여부

    @JsonProperty("isLoggedIn")
    private Boolean isLoggedIn;

}
