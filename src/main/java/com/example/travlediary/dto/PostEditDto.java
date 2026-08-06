package com.example.travlediary.dto;

import com.example.travlediary.model.PostType;
import lombok.Data;

@Data
public class PostEditDto {
    private Long id;
    private String title;
    private PostType postType;
    private String content;
}
