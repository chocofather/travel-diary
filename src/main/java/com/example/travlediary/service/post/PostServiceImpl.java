package com.example.travlediary.service.post;

import com.example.travlediary.dto.PostDetailDto;
import com.example.travlediary.model.PostImage;
import com.example.travlediary.model.UserPost;
import com.example.travlediary.repository.post.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final PostContentSanitizer postContentSanitizer;

    @Override
    @Transactional
    public PostDetailDto getPostDetail(Long postId) {
        if (postMapper.incrementViews(postId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }

        PostDetailDto post = postMapper.findPostDetail(postId);
        if (post == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }

        post.setContent(postContentSanitizer.sanitize(post.getContent()));
        post.setImages(postMapper.findPostImages(postId));
        return post;
    }

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
