package com.example.travlediary.service.comment;

/** 댓글 첨부 사진 개수 제한을 넘겼을 때. 사용자에게 그대로 보여줄 메시지를 담는다. */
public class CommentImageLimitException extends RuntimeException {

    public CommentImageLimitException(String message) {
        super(message);
    }
}
