package com.example.travlediary.repository.board;

import com.example.travlediary.dto.BoardListDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BoardMapper {

    List<BoardListDto> findBoardList(
            @Param("boardType") String boardType,
            @Param("postType") String postType,
            @Param("sort") String sort,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    int countBoard(
            @Param("boardType") String boardType,
            @Param("postType") String postType
    );

    List<BoardListDto> findBoardListByUserId(
            @Param("userId") Long userId,
            @Param("boardType") String boardType,
            @Param("postType") String postType,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    int countBoardByUserId(
            @Param("userId") Long userId,
            @Param("boardType") String boardType,
            @Param("postType") String postType
    );
}
