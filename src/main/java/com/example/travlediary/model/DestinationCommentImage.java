package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * destination_comment_images 한 행. 여행지 댓글에 첨부된 사진이다.
 * 댓글당 최대 3장이며 display_order 로 순서를 보관한다.
 */
@Data
@NoArgsConstructor
public class DestinationCommentImage {

    private Long id;
    private Long commentId;
    private String imageUrl;
    private Integer displayOrder;
    private Timestamp createdAt;
}
