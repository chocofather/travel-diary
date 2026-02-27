package com.example.travlediary.repository.post;

import com.example.travlediary.dto.BoardListDto; // DTO를 BoardListDto로 변경!
import com.example.travlediary.model.PostImage;
import com.example.travlediary.model.UserPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostMapper {

    // 게시판 리스트 조회 (정렬, 페이징)
    List<BoardListDto> findPosts(
            @Param("postType") String postType,      // tip, question 등
            @Param("sort") String sort,              // 최신순, 조회수순 등
            @Param("offset") int offset,             // 페이징
            @Param("limit") int limit
    );

    // 전체 글 개수(페이징용)
    int countPosts(@Param("postType") String postType);

    // 게시글 저장
    int insertPost(UserPost post);

    // 이미지 저장
    int insertPostImage(PostImage image);
}
