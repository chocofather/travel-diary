package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryListItemDto;
import com.example.travlediary.model.Diary;
import com.example.travlediary.repository.diary.DiaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DiaryServiceImpl implements DiaryService {

    private static final int MAX_TITLE_LENGTH = 150;
    /** 연도는 4자리만 허용한다. (브라우저 date input 의 min/max 와 같은 범위) */
    private static final int MIN_YEAR = 1000;
    private static final int MAX_YEAR = 9999;
    /** 표지 스타일 기본값 (DB 기본값과 같은 값) */
    private static final String DEFAULT_COVER_STYLE = "DEFAULT";

    private final DiaryMapper diaryMapper;

    @Override
    @Transactional(readOnly = true)
    public List<Diary> getMyDiaries(Long userId) {
        requireUser(userId);
        return diaryMapper.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiaryListItemDto> getMyDiaryList(Long userId) {
        requireUser(userId);
        return diaryMapper.findListItemsByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Diary getMyDiary(Long diaryId, Long userId) {
        return requireOwnedDiary(diaryId, userId);
    }

    @Override
    @Transactional
    public Diary create(Long userId, Diary diary) {
        requireUser(userId);
        Diary prepared = validated(diary);
        // 소유자는 요청 값을 믿지 않고 현재 사용자로 설정한다.
        prepared.setUserId(userId);

        if (diaryMapper.insert(prepared) != 1 || prepared.getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "다이어리를 저장하지 못했습니다.");
        }
        return requireOwnedDiary(prepared.getId(), userId);
    }

    @Override
    @Transactional
    public Diary update(Long diaryId, Long userId, Diary diary) {
        Diary existing = requireOwnedDiary(diaryId, userId);
        Diary prepared = validated(diary);
        // 대상과 소유자는 요청 값이 아니라 검증된 값으로 고정한다. (소유자 변경 불가)
        prepared.setId(existing.getId());
        prepared.setUserId(existing.getUserId());

        if (diaryMapper.update(prepared) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "다이어리를 찾을 수 없습니다.");
        }
        return requireOwnedDiary(existing.getId(), userId);
    }

    @Override
    @Transactional
    public void delete(Long diaryId, Long userId) {
        requireOwnedDiary(diaryId, userId);
        // 페이지/요소 행은 FK ON DELETE CASCADE 로 함께 삭제된다.
        if (diaryMapper.delete(diaryId, userId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "다이어리를 찾을 수 없습니다.");
        }
    }

    /**
     * 본인 소유 다이어리만 통과시킨다.
     * 없는 다이어리와 남의 다이어리를 같은 404 로 처리해 존재 여부가 드러나지 않게 한다.
     */
    private Diary requireOwnedDiary(Long diaryId, Long userId) {
        requireUser(userId);
        Diary diary = diaryId == null ? null : diaryMapper.findByIdAndUserId(diaryId, userId);
        if (diary == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "다이어리를 찾을 수 없습니다.");
        }
        return diary;
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    /** 연도가 4자리 범위를 벗어났는지 확인한다. */
    private boolean isYearOutOfRange(LocalDate date) {
        return date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR;
    }

    /** 저장 전 입력값 검증. 원본을 건드리지 않고 정리된 값을 담은 Diary 를 돌려준다. */
    private Diary validated(Diary diary) {
        if (diary == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "다이어리 정보를 입력해 주세요.");
        }

        String title = diary.getTitle() == null ? "" : diary.getTitle().strip();
        if (title.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목을 입력해 주세요.");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "제목은 150자 이하로 입력해 주세요.");
        }

        LocalDate startDate = diary.getStartDate();
        LocalDate endDate = diary.getEndDate();
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "여행 기간을 입력해 주세요.");
        }
        if (isYearOutOfRange(startDate) || isYearOutOfRange(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "여행 기간의 연도는 4자리(1000 ~ 9999)로 입력해 주세요.");
        }
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "여행 종료일은 시작일 이후여야 합니다.");
        }

        String coverImageUrl = diary.getCoverImageUrl() == null ? null : diary.getCoverImageUrl().strip();
        String coverStyle = diary.getCoverStyle() == null ? "" : diary.getCoverStyle().strip();

        Diary prepared = new Diary();
        prepared.setTitle(title);
        prepared.setStartDate(startDate);
        prepared.setEndDate(endDate);
        prepared.setCoverImageUrl(coverImageUrl == null || coverImageUrl.isEmpty() ? null : coverImageUrl);
        prepared.setCoverStyle(coverStyle.isEmpty() ? DEFAULT_COVER_STYLE : coverStyle);
        return prepared;
    }
}
