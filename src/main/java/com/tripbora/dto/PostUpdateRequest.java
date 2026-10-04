package com.tripbora.dto;

import com.tripbora.model.PostType;
import lombok.Data;

@Data
public class PostUpdateRequest {
    private String title;
    private PostType postType;
    private String content;
}
