package com.example.travlediary.service.post;

import com.example.travlediary.dto.PostDetailDto;
import com.example.travlediary.dto.PostEditDto;
import com.example.travlediary.dto.PostUpdateRequest;
import com.example.travlediary.model.PostImage;
import com.example.travlediary.model.UserPost;

import java.util.List;

public interface PostService {

    PostDetailDto getPostDetail(Long postId, Long currentUserId);

    PostEditDto getPostForEdit(Long postId, Long userId);

    void updatePost(Long postId, Long userId, PostUpdateRequest request);

    void deletePost(Long postId, Long userId);

    /**
     * 게시글(질문/팁) 등록 + 이미지 등록
     * @param post      게시글(질문/팁) 정보
     * @param images    이미지 리스트(없으면 null/빈 리스트)
     * @return 생성된 게시글 id
     */
    Long createPost(UserPost post, List<PostImage> images);
}
