package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;


@Data
@NoArgsConstructor
public class DestinationComment {
    private Long id; // 여행지 댓글 번호
    private Long parentCommentId; // 부모 댓글 번호
    private String content; // 내용
    private Integer likes; // 좋아요수
    private Boolean deleted; // 삭제여부
    private boolean moderated; // 관리자 조치로 숨겨진 댓글이면 true (사용자 직접 삭제와 구분)
    private Timestamp createdAt; // 생성일
    private Timestamp updatedAt; // 수정일
    private Timestamp deletedAt; // 삭제일
    private Long userId; // 작성자번호
    private Long destinationId; // 여행지번호

    private User writer;
}
