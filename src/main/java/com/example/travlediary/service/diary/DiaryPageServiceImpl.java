package com.example.travlediary.service.diary;

import com.example.travlediary.model.Diary;
import com.example.travlediary.model.DiaryPage;
import com.example.travlediary.repository.diary.DiaryPageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DiaryPageServiceImpl implements DiaryPageService {

    /** 배경 유형 기본값 (DB 기본값과 같은 값) */
    private static final String DEFAULT_BACKGROUND_TYPE = "PLAIN";
    /** 현재 화면에서 쓰는 배경 유형 */
    private static final Set<String> BACKGROUND_TYPES =
            Set.of("PLAIN", "LINED", "GRID", "DOT");

    private final DiaryService diaryService;
    private final DiaryPageMapper diaryPageMapper;

    @Override
    @Transactional(readOnly = true)
    public List<DiaryPage> getPages(Long diaryId, Long userId) {
        Diary diary = requireOwnedDiary(diaryId, userId);
        return diaryPageMapper.findByDiaryId(diary.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public DiaryPage getPage(Long diaryId, Long pageId, Long userId) {
        Diary diary = requireOwnedDiary(diaryId, userId);
        return requirePageOfDiary(pageId, diary.getId());
    }

    @Override
    @Transactional
    public DiaryPage create(Long diaryId, Long userId, DiaryPage page) {
        Diary diary = requireOwnedDiary(diaryId, userId);
        DiaryPage prepared = validated(page, diary);
        // 대상 다이어리는 요청 값을 믿지 않고 검증된 diaryId 로 설정한다.
        prepared.setDiaryId(diary.getId());

        insertPage(prepared);
        return requirePageOfDiary(prepared.getId(), diary.getId());
    }

    @Override
    @Transactional
    public DiaryPage append(Long diaryId, Long userId, DiaryPage page) {
        Diary diary = requireOwnedDiary(diaryId, userId);
        if (page == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지 정보를 입력해 주세요.");
        }

        // 순서는 사용자가 정하지 않고 마지막 장 다음 번호로 붙인다.
        Integer lastPageOrder = diaryPageMapper.findMaxPageOrder(diary.getId());
        page.setPageOrder(lastPageOrder == null ? 1 : lastPageOrder + 1);
        return create(diary.getId(), userId, page);
    }

    @Override
    @Transactional
    public DiaryPage update(Long diaryId, Long pageId, Long userId, DiaryPage page) {
        Diary diary = requireOwnedDiary(diaryId, userId);
        DiaryPage existing = requirePageOfDiary(pageId, diary.getId());
        DiaryPage prepared = validated(page, diary);
        // 대상 페이지/다이어리는 요청 값이 아니라 검증된 값으로 고정한다.
        prepared.setId(existing.getId());
        prepared.setDiaryId(diary.getId());
        // 날짜/배경만 바꾸는 요청(content 없음)에서 본문이 지워지지 않게 기존 값을 그대로 둔다.
        if (prepared.getContent() == null) {
            prepared.setContent(existing.getContent());
        }

        updatePage(prepared);
        return requirePageOfDiary(existing.getId(), diary.getId());
    }

    @Override
    @Transactional
    public void delete(Long diaryId, Long pageId, Long userId) {
        Diary diary = requireOwnedDiary(diaryId, userId);
        requirePageOfDiary(pageId, diary.getId());
        // 요소 행은 FK ON DELETE CASCADE 로 함께 삭제된다.
        if (diaryPageMapper.delete(pageId, diary.getId()) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "페이지를 찾을 수 없습니다.");
        }
    }

    /** 부모 다이어리 소유권 확인. 없는 다이어리와 남의 다이어리를 같은 404 로 처리한다. */
    private Diary requireOwnedDiary(Long diaryId, Long userId) {
        return diaryService.getMyDiary(diaryId, userId);
    }

    /** 그 페이지가 해당 다이어리에 속하는지 확인한다. */
    private DiaryPage requirePageOfDiary(Long pageId, Long diaryId) {
        DiaryPage page = pageId == null ? null : diaryPageMapper.findByIdAndDiaryId(pageId, diaryId);
        if (page == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "페이지를 찾을 수 없습니다.");
        }
        return page;
    }

    /** UNIQUE(diary_id, page_order) 위반은 서버 오류가 아니라 입력 오류로 돌려준다. */
    private void insertPage(DiaryPage page) {
        try {
            if (diaryPageMapper.insert(page) != 1 || page.getId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "페이지를 저장하지 못했습니다.");
            }
        } catch (DuplicateKeyException exception) {
            throw duplicatePageOrder(exception);
        }
    }

    private void updatePage(DiaryPage page) {
        try {
            if (diaryPageMapper.update(page) != 1) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "페이지를 찾을 수 없습니다.");
            }
        } catch (DuplicateKeyException exception) {
            throw duplicatePageOrder(exception);
        }
    }

    private ResponseStatusException duplicatePageOrder(DuplicateKeyException exception) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "이미 사용 중인 페이지 순서입니다.", exception);
    }

    /** 저장 전 입력값 검증. 원본을 건드리지 않고 정리된 값을 담은 DiaryPage 를 돌려준다. */
    private DiaryPage validated(DiaryPage page, Diary diary) {
        if (page == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지 정보를 입력해 주세요.");
        }

        LocalDate pageDate = page.getPageDate();
        if (pageDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지 날짜를 입력해 주세요.");
        }
        // 같은 날짜에 여러 페이지는 허용하되, 여행 기간을 벗어난 날짜는 막는다.
        if (pageDate.isBefore(diary.getStartDate()) || pageDate.isAfter(diary.getEndDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "페이지 날짜는 여행 기간 안에서 선택해 주세요.");
        }

        Integer pageOrder = page.getPageOrder();
        if (pageOrder == null || pageOrder < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지 순서는 1 이상이어야 합니다.");
        }

        String backgroundType = page.getBackgroundType() == null
                ? "" : page.getBackgroundType().strip().toUpperCase(Locale.ROOT);
        if (backgroundType.isEmpty()) {
            backgroundType = DEFAULT_BACKGROUND_TYPE;
        }
        if (!BACKGROUND_TYPES.contains(backgroundType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 페이지 배경입니다.");
        }

        DiaryPage prepared = new DiaryPage();
        prepared.setPageDate(pageDate);
        prepared.setPageOrder(pageOrder);
        prepared.setBackgroundType(backgroundType);
        prepared.setContent(page.getContent());
        return prepared;
    }
}
