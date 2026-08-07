package com.example.travlediary.service.post;

import com.example.travlediary.dto.PostDetailDto;
import com.example.travlediary.dto.PostEditDto;
import com.example.travlediary.dto.PostUpdateRequest;
import com.example.travlediary.model.PostImage;
import com.example.travlediary.model.PostType;
import com.example.travlediary.model.UserPost;
import com.example.travlediary.repository.post.PostMapper;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final PostContentSanitizer postContentSanitizer;

    @Override
    @Transactional
    public PostDetailDto getPostDetail(Long postId, Long currentUserId) {
        if (postMapper.incrementViews(postId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }

        PostDetailDto post = postMapper.findPostDetail(postId, currentUserId);
        if (post == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }

        post.setContent(postContentSanitizer.sanitize(post.getContent()));
        post.setImages(postMapper.findPostImages(postId));
        UserPost activePost = postMapper.findActivePost(postId);
        post.setMyPost(activePost != null
                && currentUserId != null
                && Objects.equals(activePost.getUserId(), currentUserId));
        return post;
    }

    @Override
    @Transactional(readOnly = true)
    public PostEditDto getPostForEdit(Long postId, Long userId) {
        requireOwnedActivePost(postId, userId);
        PostEditDto post = postMapper.findPostForEdit(postId);
        if (post == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
        post.setContent(postContentSanitizer.sanitize(post.getContent()));
        return post;
    }

    @Override
    @Transactional
    public void updatePost(Long postId, Long userId, PostUpdateRequest request) {
        requireOwnedActivePost(postId, userId);
        ValidatedPost validated = validatePost(request.getTitle(), request.getPostType(), request.getContent());
        if (postMapper.updatePost(postId, userId, validated.title(), validated.postType(), validated.content()) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
    }

    @Override
    @Transactional
    public void deletePost(Long postId, Long userId) {
        requireOwnedActivePost(postId, userId);
        if (postMapper.softDeletePost(postId, userId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
    }

    @Override
    @Transactional
    public Long createPost(UserPost post, List<PostImage> images) {
        ValidatedPost validated = validatePost(post.getTitle(), post.getPostType(), post.getContent());
        post.setTitle(validated.title());
        post.setPostType(validated.postType());
        post.setContent(validated.content());

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

    private UserPost requireOwnedActivePost(Long postId, Long userId) {
        UserPost post = postId == null ? null : postMapper.findActivePost(postId);
        if (post == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다.");
        }
        if (!Objects.equals(post.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "게시글 변경 권한이 없습니다.");
        }
        return post;
    }

    private ValidatedPost validatePost(String title, PostType postType, String content) {
        String trimmedTitle = title == null ? "" : title.trim();
        if (trimmedTitle.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목을 입력해 주세요.");
        }
        if (trimmedTitle.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목은 255자 이하로 입력해 주세요.");
        }
        if (postType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "게시글 유형을 선택해 주세요.");
        }

        String sanitizedContent = postContentSanitizer.sanitize(content);
        org.jsoup.nodes.Document document = Jsoup.parseBodyFragment(sanitizedContent);
        boolean hasText = !document.text().trim().isEmpty();
        boolean hasImage = !document.select("img[src]").isEmpty();
        if (!hasText && !hasImage) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "본문을 입력해 주세요.");
        }
        return new ValidatedPost(trimmedTitle, postType, sanitizedContent);
    }

    private record ValidatedPost(String title,
                                 PostType postType,
                                 String content) {
    }
}
