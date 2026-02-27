package com.example.travlediary.service.post;

import com.example.travlediary.model.PostImage;
import com.example.travlediary.model.UserPost;
import com.example.travlediary.repository.post.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;

    @Override
    @Transactional
    public Long createPost(UserPost post, List<PostImage> images) {
        // 1. 게시글 저장
        postMapper.insertPost(post); // post.id가 useGeneratedKeys로 세팅됨

        // 2. 이미지가 있으면 저장 (postId 연관)
        if (images != null && !images.isEmpty()) {
            for (PostImage img : images) {
                img.setPostId(post.getId());
                postMapper.insertPostImage(img);
            }
        }

        return post.getId();
    }
}
