package com.example.travlediary.service.board;

import com.example.travlediary.dto.BoardListDto;

import java.util.List;

public interface BoardService {
    // 통합 리스트(질문/팁/코스)
    List<BoardListDto> getBoardList(String boardType, String postType, String scope,
                                    Long countryId, String sort, int page, int size);

    // 전체 글 수(페이징)
    int getBoardCount(String boardType, String postType, String scope, Long countryId);

    // 공개 프로필용 작성자 콘텐츠 목록
    List<BoardListDto> getBoardListByUserId(Long userId, String type, int page, int size);

    // 공개 프로필용 작성자 콘텐츠 수
    int getBoardCountByUserId(Long userId, String type);
}
