package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * post_comment_images 한 행. 커뮤니티 게시글 댓글에 첨부된 사진이다.
 * 댓글당 최대 3장이며 display_order 로 순서를 보관한다.
 */
@Data
@NoArgsConstructor
public class PostCommentImage {

    private Long id;
    private Long commentId;
    private String imageUrl;
    private Integer displayOrder;
    private Timestamp createdAt;
}
