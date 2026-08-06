package com.example.travlediary.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.sql.Timestamp;

@Data
public class PostCommentDto {
    private Long id;
    private Long postId;
    private Long parentCommentId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String content;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String writerNickname;
    private Timestamp createdAt;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Timestamp updatedAt;
    private boolean deleted;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean myComment;
}
