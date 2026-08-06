package com.example.travlediary.repository.post;

import com.example.travlediary.model.PostImage;
import com.example.travlediary.model.UserPost;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PostMapper {

    // 게시글 저장
    int insertPost(UserPost post);

    // 이미지 저장
    int insertPostImage(PostImage image);
}
