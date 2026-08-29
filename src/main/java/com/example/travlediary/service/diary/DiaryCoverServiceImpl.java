package com.example.travlediary.service.diary;

import com.example.travlediary.model.Diary;
import com.example.travlediary.model.DiaryCover;
import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverDesignElement;
import com.example.travlediary.model.DiaryCoverElement;
import com.example.travlediary.repository.diary.DiaryCoverElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverMapper;
import com.example.travlediary.service.file.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DiaryCoverServiceImpl implements DiaryCoverService {

    /** 적용본 사진을 두는 곳. 보관함 원본(diary-cover-designs)과 나눠 둔다. */
    private static final String COVER_IMAGE_DIRECTORY = "diary-cover-elements";
    private static final String PHOTO_ELEMENT_TYPE = "PHOTO";
    /** 업로드한 파일을 가리키는 경로의 앞머리. 그 밖의 경로는 지우지 않는다. */
    private static final String UPLOAD_URL_PREFIX = "/uploads/";

    /** 다이어리 소유권은 기존 서비스가 이미 확인해 준다. (그 규칙을 그대로 따른다) */
    private final DiaryService diaryService;
    /** 표지 디자인 소유권도 보관함 서비스가 그대로 확인해 준다. */
    private final DiaryCoverDesignService diaryCoverDesignService;
    private final DiaryCoverDesignElementService diaryCoverDesignElementService;
    private final DiaryCoverMapper diaryCoverMapper;
    private final DiaryCoverElementMapper diaryCoverElementMapper;
    private final FileUploadService fileUploadService;

    /** 업로드 폴더의 실제 경로. (application.yml 의 custom.upload-path) */
    @Value("${custom.upload-path}")
    private String uploadPath;

    @Override
    @Transactional(readOnly = true)
    public Optional<DiaryCover> findMyCover(Long diaryId, Long userId) {
        requireOwnedDiary(diaryId, userId);
        // 없는 것이 정상이다. 그런 다이어리는 기본 표지를 쓴다.
        return Optional.ofNullable(diaryCoverMapper.findByDiaryIdAndUserId(diaryId, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public DiaryCover getMyCover(Long diaryId, Long userId) {
        return findMyCover(diaryId, userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "커스텀 표지를 찾을 수 없습니다."));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DiaryCoverElement> getElements(Long diaryId, Long userId) {
        DiaryCover cover = getMyCover(diaryId, userId);
        return diaryCoverElementMapper.findAllByCoverId(cover.getId());
    }

    @Override
    @Transactional
    public DiaryCover update(Long diaryId, Long userId,
                             String baseCoverStyle, String backgroundColor) {
        DiaryCover existing = getMyCover(diaryId, userId);
        existing.setBaseCoverStyle(DiaryCoverValues.baseCoverStyle(baseCoverStyle));
        existing.setBackgroundColor(DiaryCoverValues.backgroundColor(backgroundColor));

        if (diaryCoverMapper.update(existing, userId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "커스텀 표지를 찾을 수 없습니다.");
        }
        return getMyCover(diaryId, userId);
    }

    @Override
    @Transactional
    public List<DiaryCoverElement> delete(Long diaryId, Long userId) {
        DiaryCover existing = getMyCover(diaryId, userId);
        // 지우고 나면 못 읽으므로, 사진 파일 정리에 쓸 목록을 먼저 확보한다.
        List<DiaryCoverElement> elements =
                diaryCoverElementMapper.findAllByCoverId(existing.getId());

        if (diaryCoverMapper.deleteByDiaryIdAndUserId(diaryId, userId) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "커스텀 표지를 찾을 수 없습니다.");
        }
        return elements;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, DiaryCover> findCoversByDiary(List<Long> diaryIds, Long userId) {
        DiaryCoverValues.requireUser(userId);
        if (diaryIds == null || diaryIds.isEmpty()) {
            return Map.of();
        }
        // 한 번만 묻는다. 커스텀 표지가 없는 다이어리는 결과에 아예 없다.
        return diaryCoverMapper.findAllByDiaryIds(diaryIds, userId).stream()
                .collect(Collectors.toMap(DiaryCover::getDiaryId, cover -> cover,
                        (first, second) -> first, LinkedHashMap::new));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<DiaryCoverElement>> findElementsByCover(Collection<DiaryCover> covers) {
        if (covers == null || covers.isEmpty()) {
            return Map.of();
        }
        List<Long> coverIds = covers.stream().map(DiaryCover::getId).toList();
        return diaryCoverElementMapper.findAllByCoverIds(coverIds).stream()
                .collect(Collectors.groupingBy(DiaryCoverElement::getCoverId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    @Override
    @Transactional
    public Diary createWithDesign(Long userId, Diary diary, Long designId) {
        // 다이어리와 표지가 한 트랜잭션 안에 있다. 표지가 실패하면 다이어리도 남지 않는다.
        Diary created = diaryService.create(userId, diary);
        applyDesign(created.getId(), designId, userId);
        return created;
    }

    @Override
    @Transactional
    public DiaryCover applyDesign(Long diaryId, Long designId, Long userId) {
        requireOwnedDiary(diaryId, userId);
        // 남의 디자인은 여기서 막힌다. (보관함 서비스가 본인 것만 찾아 준다)
        DiaryCoverDesign design = diaryCoverDesignService.getMyDesign(designId, userId);
        List<DiaryCoverDesignElement> elements =
                diaryCoverDesignElementService.getElements(designId, userId);

        DiaryCover cover = new DiaryCover();
        cover.setDiaryId(diaryId);
        cover.setBaseCoverStyle(design.getBaseCoverStyle());
        cover.setBackgroundColor(design.getBackgroundColor());
        if (diaryCoverMapper.insert(cover) != 1 || cover.getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "표지를 적용하지 못했습니다.");
        }

        /*
          파일은 트랜잭션이 되돌려 주지 않는다. 그래서 이번에 새로 복사한 것만 적어 두었다가
          중간에 실패하면 그 복사본만 지운다. 원본 디자인의 사진은 어떤 경우에도 손대지 않는다.
        */
        List<String> copiedFiles = new ArrayList<>();
        try {
            for (DiaryCoverDesignElement source : elements) {
                copy(source, cover.getId(), copiedFiles);
            }
        } catch (RuntimeException exception) {
            copiedFiles.forEach(this::deleteCopiedFile);
            throw exception;
        }
        return cover;
    }

    /** 요소 한 줄을 그대로 옮겨 담는다. 사진만 파일까지 새로 만든다. */
    private void copy(DiaryCoverDesignElement source, Long coverId, List<String> copiedFiles) {
        DiaryCoverElement copied = new DiaryCoverElement();
        copied.setCoverId(coverId);
        copied.setElementType(source.getElementType());
        copied.setTextContent(source.getTextContent());
        copied.setStyleType(source.getStyleType());
        copied.setColorType(source.getColorType());
        copied.setPhotoStyle(source.getPhotoStyle());
        copied.setPositionX(source.getPositionX());
        copied.setPositionY(source.getPositionY());
        copied.setWidth(source.getWidth());
        copied.setHeight(source.getHeight());
        copied.setRotation(source.getRotation());
        copied.setZIndex(source.getZIndex());

        /*
          사진은 올린 파일이라 원본과 나눠 쓰면 한쪽을 지웠을 때 다른 쪽이 깨진다.
          그래서 파일을 새로 만들고 그 경로를 저장한다.
          스티커(마스킹테이프 포함)는 공용 asset 이라 경로만 그대로 옮긴다.
        */
        String copiedUrl = PHOTO_ELEMENT_TYPE.equals(source.getElementType())
                ? fileUploadService.copyStoredFile(source.getImageUrl(), COVER_IMAGE_DIRECTORY)
                : null;
        if (copiedUrl != null) {
            copiedFiles.add(copiedUrl);
        }
        copied.setImageUrl(copiedUrl != null ? copiedUrl : source.getImageUrl());

        if (diaryCoverElementMapper.insert(copied) != 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "표지를 적용하지 못했습니다.");
        }
    }

    /** 이번 요청에서 새로 만든 복사본만 지운다. (원본 디자인 파일은 건드리지 않는다) */
    private void deleteCopiedFile(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(UPLOAD_URL_PREFIX)) {
            return;
        }
        try {
            Files.deleteIfExists(
                    Paths.get(uploadPath, imageUrl.substring(UPLOAD_URL_PREFIX.length())));
        } catch (IOException | RuntimeException ignored) {
            // 정리 실패가 원래 오류를 덮지 않게 한다.
        }
    }

    /**
     * 표지 행에는 소유자가 없다. 그래서 다이어리 쪽에서 소유권을 확인한다.
     * (남의 diaryId 를 넣으면 여기서 먼저 막힌다)
     */
    private void requireOwnedDiary(Long diaryId, Long userId) {
        DiaryCoverValues.requireUser(userId);
        if (diaryId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "여행일기를 선택해 주세요.");
        }
        diaryService.getMyDiary(diaryId, userId);
    }
}
