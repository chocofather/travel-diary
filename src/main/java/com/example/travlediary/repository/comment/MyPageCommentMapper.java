package com.example.travlediary.repository.comment;

import com.example.travlediary.dto.MyPageCommentDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MyPageCommentMapper {

    List<MyPageCommentDto> findMyComments(
            @Param("userId") Long userId,
            @Param("commentType") String commentType,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    int countMyComments(
            @Param("userId") Long userId,
            @Param("commentType") String commentType
    );
}
