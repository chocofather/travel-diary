package com.example.travlediary.repository.post;

import com.example.travlediary.dto.PostDetailDto;
import com.example.travlediary.model.PostImage;
import com.example.travlediary.model.UserPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostMapper {

    int incrementViews(@Param("postId") Long postId);

    PostDetailDto findPostDetail(@Param("postId") Long postId);

    List<PostImage> findPostImages(@Param("postId") Long postId);

    // 게시글 저장
    int insertPost(UserPost post);

    // 이미지 저장
    int insertPostImage(PostImage image);
}
