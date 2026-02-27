package com.example.travlediary.dto;

import lombok.Data;

@Data
public class DestinationDto {

    private Long id;
    private String name;
    private String thumbnailPath;
    private String regionName;
    private boolean bookmarked; //  북마크 여부
    private int commentCount; // 댓글 수


    private String shortDescription;
}
