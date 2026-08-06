package com.example.travlediary.dto;

import com.example.travlediary.model.PostImage;
import com.example.travlediary.model.PostType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.List;

@Data
@NoArgsConstructor
public class PostDetailDto {
    private Long id;
    private PostType postType;
    private String title;
    private String content;
    private String nickname;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Integer views;
    private List<PostImage> images;
    private boolean myPost;
}
