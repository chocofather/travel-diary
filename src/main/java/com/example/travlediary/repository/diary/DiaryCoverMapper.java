package com.example.travlediary.repository.diary;

import com.example.travlediary.model.DiaryCover;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 다이어리에 적용된 커스텀 표지 저장소. (한 다이어리에 하나)
 * 표지 자체는 소유자를 들고 있지 않으므로, 소유권을 봐야 하는 자리에서는
 * diaries 와 JOIN 해 diaries.user_id 로 확인한다.
 */
@Mapper
public interface DiaryCoverMapper {

    /** 표지 등록. 생성된 id 는 cover.id 에 채워진다. */
    int insert(DiaryCover cover);

    /** 번호로 1건. (소유권은 확인하지 않는다 — 서비스가 확인한 뒤에만 쓴다) */
    DiaryCover findById(@Param("coverId") Long coverId);

    /** 그 다이어리의 표지 1건. 없으면 기본 표지를 쓰는 다이어리다. */
    DiaryCover findByDiaryId(@Param("diaryId") Long diaryId);

    /** 본인 소유 다이어리의 표지 1건 (diaries.user_id 까지 확인) */
    DiaryCover findByDiaryIdAndUserId(@Param("diaryId") Long diaryId,
                                      @Param("userId") Long userId);

    /**
     * 여러 다이어리의 표지를 한 번에 (목록 화면용).
     * 카드마다 따로 묻지 않으려고 둔 것이다. 커스텀 표지를 쓰지 않는 다이어리는 결과에 없다.
     * (빈 목록은 서비스가 먼저 걸러낸다)
     */
    List<DiaryCover> findAllByDiaryIds(@Param("diaryIds") List<Long> diaryIds,
                                       @Param("userId") Long userId);

    /**
     * 표지 기본 정보 수정 (바탕 표지 / 바탕색).
     * 표지 행에는 소유자가 없으므로 userId 를 따로 받아 diaries 와 JOIN 해 확인한다.
     */
    int update(@Param("cover") DiaryCover cover,
               @Param("userId") Long userId);

    /** 커스텀 표지 제거. 이 행이 사라지면 다시 기본 표지로 돌아간다. (요소는 FK CASCADE) */
    int deleteByDiaryIdAndUserId(@Param("diaryId") Long diaryId,
                                 @Param("userId") Long userId);
}
