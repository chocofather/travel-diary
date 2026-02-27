package com.example.travlediary.model;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class CourseImage {
    private Long id; // 사진번호
    private Long courseId; // 코스번호
    private String imageUrl; // 사진 url
    private Timestamp uploadedAt; // 등록일
    private Timestamp updatedAt; // 수정일
    private Timestamp deletedAt; // 삭제일
    private Boolean deleted; // 삭제여부
}
