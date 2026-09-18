package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryPrivatePhotoRef;
import com.example.travlediary.model.Diary;
import com.example.travlediary.repository.diary.DiaryCoverDesignElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverElementMapper;
import com.example.travlediary.repository.diary.DiaryElementMapper;
import com.example.travlediary.repository.diary.DiaryMapper;
import com.example.travlediary.service.file.DiaryPrivatePhotoStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 개인 사진 한 장을 내보내기 전에 확인할 것을 모두 확인하는 자리.
 *
 * <p>사진마다 query 는 한 번이다. 다이어리·페이지·요소를 통째로 읽지 않고, 소유권과 부모 관계를
 * SQL 조건으로 밀어 넣어 필요한 세 칸(저장 키·다이어리 번호·PIN 해시)만 가져온다.
 *
 * <p>없는 번호, 남의 것, 부모 관계 불일치, 사진이 아닌 요소, 저장소에 없는 파일은 모두 같은
 * 404 다. 바깥에서 보면 구분되지 않아야 어떤 자원이 있는지 알아낼 수 없다.
 * 본인의 잠긴 다이어리만 {@link DiaryPinGuard} 를 통해 기존 PIN 정책과 같은 길로 막힌다.
 */
@Service
@RequiredArgsConstructor
public class DiaryPrivatePhotoServiceImpl implements DiaryPrivatePhotoService {

    private final DiaryMapper diaryMapper;
    private final DiaryElementMapper diaryElementMapper;
    private final DiaryCoverElementMapper diaryCoverElementMapper;
    private final DiaryCoverDesignElementMapper diaryCoverDesignElementMapper;
    private final DiaryPinGuard diaryPinGuard;
    private final DiaryPrivatePhotoStorage photoStorage;

    @Override
    @Transactional(readOnly = true)
    public DiaryPrivatePhotoStorage.StoredPhotoFile getCoverImage(Long diaryId, Long userId) {
        requireUser(userId);
        return openOwnedPhoto(diaryMapper.findCoverPhotoRef(diaryId, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public DiaryPrivatePhotoStorage.StoredPhotoFile getPageElementPhoto(
            Long diaryId, Long pageId, Long elementId, Long userId) {
        requireUser(userId);
        return openOwnedPhoto(
                diaryElementMapper.findPhotoRef(diaryId, pageId, elementId, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public DiaryPrivatePhotoStorage.StoredPhotoFile getCoverElementPhoto(
            Long diaryId, Long elementId, Long userId) {
        requireUser(userId);
        return openOwnedPhoto(diaryCoverElementMapper.findPhotoRef(diaryId, elementId, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public DiaryPrivatePhotoStorage.StoredPhotoFile getCoverDesignElementPhoto(
            Long designId, Long elementId, Long userId) {
        requireUser(userId);
        String storageKey = diaryCoverDesignElementMapper
                .findPhotoStorageKey(designId, elementId, userId);
        if (storageKey == null) {
            throw notFound();
        }
        // 표지 디자인은 다이어리에 속하지 않으므로 PIN 잠금과 무관하다.
        return open(storageKey);
    }

    /**
     * 소유권이 확인된 한 줄을 PIN 검사까지 지나 파일로 바꾼다.
     * PIN 검사에 넘길 다이어리는 잠금 판단에 필요한 두 칸만 담은 가벼운 값이다.
     */
    private DiaryPrivatePhotoStorage.StoredPhotoFile openOwnedPhoto(DiaryPrivatePhotoRef ref) {
        if (ref == null || ref.getImageUrl() == null) {
            throw notFound();
        }
        Diary locked = new Diary();
        locked.setId(ref.getDiaryId());
        locked.setPinHash(ref.getPinHash());
        // 잠긴 다이어리는 여기서 DiaryPinLockedException 으로 막힌다. (화면과 같은 정책)
        diaryPinGuard.requireUnlocked(locked);
        return open(ref.getImageUrl());
    }

    /** 저장소가 내보낼 수 없다고 하면 이유를 구분하지 않고 404 로 덮는다. */
    private DiaryPrivatePhotoStorage.StoredPhotoFile open(String storageKey) {
        try {
            return photoStorage.resolveForRead(storageKey);
        } catch (RuntimeException exception) {
            throw notFound();
        }
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "사진을 찾을 수 없습니다.");
    }
}
