package com.example.travlediary.repository.diary;

import com.example.travlediary.dto.DiaryListItemDto;
import com.example.travlediary.model.Diary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 개인 여행일기(다이어리) 저장소.
 * 수정/삭제 SQL 에도 user_id 조건을 넣어 서비스 검증과 함께 소유권을 이중으로 지킨다.
 */
@Mapper
public interface DiaryMapper {

    /** 회원이 가진 다이어리 목록 (최근 여행부터) */
    List<Diary> findByUserId(@Param("userId") Long userId);

    /**
     * 일기장형 목록 한 쪽. 페이지 수까지 한 번에 읽는다. (다이어리마다 재조회하지 않는다)
     * keyword 가 있으면 제목/한 줄 메모/본문에서 찾고, 결과는 다이어리 한 권 단위다.
     */
    List<DiaryListItemDto> findListItems(@Param("userId") Long userId,
                                         @Param("keyword") String keyword,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    /** 같은 조건의 전체 다이어리 수 (쪽수 계산용) */
    int countListItems(@Param("userId") Long userId,
                       @Param("keyword") String keyword);

    /** 본인 소유 다이어리 1건 */
    Diary findByIdAndUserId(@Param("diaryId") Long diaryId,
                            @Param("userId") Long userId);

    /** 다이어리 등록. 생성된 id 는 diary.id 에 채워진다. */
    int insert(Diary diary);

    /** 본인 소유 다이어리 수정 */
    int update(Diary diary);

    /** 본인 소유 다이어리 삭제 (페이지/요소는 FK CASCADE 로 함께 지워진다) */
    int delete(@Param("diaryId") Long diaryId,
               @Param("userId") Long userId);
}
