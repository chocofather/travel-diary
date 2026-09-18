package com.example.travlediary.service.diary;

import com.example.travlediary.dto.GuestDiaryImportManifest;
import com.example.travlediary.model.Diary;
import com.example.travlediary.model.DiaryCover;
import com.example.travlediary.model.DiaryCoverElement;
import com.example.travlediary.model.DiaryElement;
import com.example.travlediary.model.DiaryPage;
import com.example.travlediary.repository.diary.DiaryCoverElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverMapper;
import com.example.travlediary.service.file.DiaryPrivatePhotoStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 브라우저에 있던 체험 여행일기를 지금 로그인한 회원의 정식 여행일기로 옮겨 담는다.
 *
 * <p>받은 값은 전부 신뢰하지 않는다. localStorage 와 IndexedDB 는 사용자가 마음대로 고칠 수 있어서,
 * 회원이 평소 화면으로 저장할 때보다 오히려 더 넓은 범위를 여기에서 다시 본다.
 * 다만 판정 기준은 회원과 같은 것을 쓴다 — 페이지·요소 저장은 기존 서비스를 그대로 지나고,
 * 스티커·라벨·글꼴은 같은 목록(catalog)이 허용 목록이 된다.
 *
 * <p>소유자는 요청에 담긴 값이 아니라 로그인 정보로만 정해진다.
 *
 * <p>표지는 두 갈래다. 기본 표지를 고른 체험 여행일기는 회원과 똑같이 diaries 의 값만 쓰고,
 * 꾸민 표지만 diary_covers / diary_cover_elements 를 만든다.
 * 체험 표지는 "내 디자인 보관함" 의 원본이 아니므로 diary_cover_designs 에는 남기지 않는다.
 *
 * <p>DB 는 한 트랜잭션이지만 파일은 트랜잭션이 되돌려 주지 않는다. 그래서 이번에 저장한 사진을
 * 적어 두었다가 트랜잭션이 끝나는 것을 보고 정리한다. 되돌아갔으면 지우고, 커밋됐으면 남긴다.
 */
@Service
@RequiredArgsConstructor
public class GuestDiaryImportService {

    /** 가져온 페이지 사진 저장 위치. 회원 페이지 사진과 같은 private 저장소다. */
    private static final String PAGE_IMAGE_DIRECTORY = DiaryPrivatePhotoStorage.PAGE_DIRECTORY;
    /** 가져온 표지 사진 저장 위치. 회원 표지 사진과 같은 private 저장소다. */
    private static final String COVER_IMAGE_DIRECTORY = DiaryPrivatePhotoStorage.COVER_DIRECTORY;

    private static final String TYPE_TEXT = "TEXT";
    private static final String TYPE_PHOTO = "PHOTO";
    private static final String TYPE_STICKER = "STICKER";
    private static final String TYPE_NOTE = "NOTE";
    private static final String CUSTOM_COVER_TYPE = "CUSTOM";

    /*
      요청 크기 상한. 체험은 3장까지지만 그 안에 무엇이든 넣어 보낼 수 있으므로
      한 번에 처리할 양을 여기에서 끊는다. 평소 꾸미기로는 닿지 않을 만큼 넉넉히 둔다.
    */
    private static final int MAX_PAGES = 3;
    private static final int MAX_ELEMENTS_PER_PAGE = 60;
    private static final int MAX_COVER_ELEMENTS = 60;
    private static final int MAX_PHOTOS = 30;
    private static final int MAX_TITLE_LENGTH = 150;
    private static final int MAX_CONTENT_LENGTH = 60_000;

    private final DiaryService diaryService;
    private final DiaryPageService diaryPageService;
    private final DiaryElementService diaryElementService;
    private final DiaryCoverMapper diaryCoverMapper;
    private final DiaryCoverElementMapper diaryCoverElementMapper;
    private final DiaryStickerCatalog diaryStickerCatalog;
    private final DiaryNoteCatalog diaryNoteCatalog;
    private final DiaryLabelFontCatalog diaryLabelFontCatalog;
    private final DiaryContentSanitizer diaryContentSanitizer;
    /** 가져온 사진도 회원 개인 사진이므로 공개 업로드 폴더가 아니라 private 저장소에 둔다. */
    private final DiaryPrivatePhotoStorage diaryPrivatePhotoStorage;

    /**
     * 체험 여행일기 한 권을 옮겨 담는다.
     *
     * @param userId 로그인한 회원. 요청 값이 아니라 인증 정보에서 온 값이어야 한다
     * @param manifest 브라우저가 보낸 내용 (신뢰하지 않는다)
     * @param photoParts 함께 올라온 사진 파일. 이름표로만 짝을 짓는다
     * @return 새로 만들어진 여행일기 번호
     */
    @Transactional
    public Long importDraft(Long userId,
                            GuestDiaryImportManifest manifest,
                            Map<String, MultipartFile> photoParts) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (manifest == null) {
            throw badRequest("가져올 여행일기 정보를 찾을 수 없습니다.");
        }

        List<GuestDiaryImportManifest.Page> pages = requirePages(manifest.pages());
        Map<String, MultipartFile> photos = requirePhotoParts(manifest, pages, photoParts);

        /*
          이번 요청에서 새로 만든 사진 파일. 트랜잭션이 되돌아가면 이 목록만 지운다.
          기존 파일이나 공용 스티커는 어떤 경우에도 손대지 않는다.
        */
        List<String> savedFiles = new ArrayList<>();
        registerFileCleanup(savedFiles);

        Diary diary = diaryService.create(userId, diaryOf(manifest));
        Long diaryId = diary.getId();

        // 꾸민 표지를 쓰던 체험 여행일기만 표지 행을 만든다. 기본 표지는 회원과 같은 규칙이다.
        if (isCustomCover(manifest)) {
            saveCover(diaryId, manifest.coverDesign(), photos, savedFiles);
        }

        for (GuestDiaryImportManifest.Page page : pages) {
            DiaryPage created = diaryPageService.append(diaryId, userId, pageOf(page));
            // 한 줄 메모는 회원 화면과 같이 장을 만든 뒤에 그 장의 규칙으로 저장한다.
            savePageHeader(diaryId, created.getId(), userId, page);
            List<GuestDiaryImportManifest.Element> elements = requireElements(
                    page.elements(), MAX_ELEMENTS_PER_PAGE, "페이지");
            for (GuestDiaryImportManifest.Element element : elements) {
                diaryElementService.create(diaryId, created.getId(), userId,
                        pageElementOf(element, photos, savedFiles));
            }
        }
        return diaryId;
    }

    /* ===== 파일 정리 ===== */

    /**
     * 트랜잭션이 끝나는 것을 보고 파일을 정리한다.
     *
     * <p>서비스 안에서 예외를 잡는 것만으로는 부족하다. 마지막 커밋 단계에서 실패하면
     * 여기까지 오지 않기 때문이다. 그래서 트랜잭션이 어떻게 끝났는지를 보고 판단한다.
     */
    private void registerFileCleanup(List<String> savedFiles) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_COMMITTED) {
                            // 저장이 끝났다. 사진은 그대로 둔다.
                            return;
                        }
                        savedFiles.forEach(GuestDiaryImportService.this::deleteSavedFile);
                    }
                });
    }

    /** 이번 요청에서 저장한 파일만 지운다. */
    private void deleteSavedFile(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        try {
            diaryPrivatePhotoStorage.delete(imageUrl);
        } catch (RuntimeException ignored) {
            // 정리 실패가 원래 오류를 덮지 않게 한다.
        }
    }

    /* ===== 다이어리 ===== */

    private Diary diaryOf(GuestDiaryImportManifest manifest) {
        Diary diary = new Diary();
        diary.setTitle(requireTitle(manifest.title()));
        diary.setStartDate(requireDate(manifest.startDate(), "여행 시작일"));
        diary.setEndDate(requireDate(manifest.endDate(), "여행 종료일"));
        if (diary.getEndDate().isBefore(diary.getStartDate())) {
            throw badRequest("여행 종료일이 시작일보다 빠릅니다.");
        }
        /*
          표지 스타일과 노트 종류는 아는 값인지 여행일기 서비스가 다시 본다.
          꾸민 표지를 쓰는 경우에도 그 표지를 떼면 돌아갈 자리로 재질을 적어 둔다. (회원과 같은 규칙)
        */
        diary.setNotebookType(manifest.notebookType());
        diary.setCoverStyle(isCustomCover(manifest)
                ? baseCoverStyleOf(manifest.coverDesign())
                : manifest.coverStyle());
        // 대표 이미지는 체험에 없다. 꾸민 표지의 사진은 표지 요소로 따로 저장된다.
        diary.setCoverImageUrl(null);
        return diary;
    }

    private String requireTitle(String title) {
        String value = title == null ? "" : title.strip();
        if (value.isEmpty()) {
            throw badRequest("여행일기 제목을 입력해 주세요.");
        }
        if (value.length() > MAX_TITLE_LENGTH) {
            throw badRequest("여행일기 제목이 너무 깁니다.");
        }
        return value;
    }

    private LocalDate requireDate(String value, String label) {
        if (value == null || value.isBlank()) {
            throw badRequest(label + "을 입력해 주세요.");
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException exception) {
            throw badRequest(label + "을 다시 확인해 주세요.");
        }
    }

    private boolean isCustomCover(GuestDiaryImportManifest manifest) {
        return CUSTOM_COVER_TYPE.equals(manifest.coverType())
                && manifest.coverDesign() != null;
    }

    private String baseCoverStyleOf(GuestDiaryImportManifest.Cover cover) {
        return cover.baseCoverStyle();
    }

    /* ===== 페이지 ===== */

    private List<GuestDiaryImportManifest.Page> requirePages(
            List<GuestDiaryImportManifest.Page> pages) {
        if (pages == null || pages.isEmpty()) {
            throw badRequest("가져올 페이지가 없습니다.");
        }
        if (pages.size() > MAX_PAGES) {
            throw badRequest("체험 여행일기는 " + MAX_PAGES + "장까지 가져올 수 있습니다.");
        }
        return pages;
    }

    /**
     * 페이지 기본정보.
     *
     * <p>자리 번호는 요청에서 받지 않는다. 보낸 차례대로 1부터 붙이므로
     * 번호가 겹치거나 비는 요청을 애초에 만들 수 없다.
     * 날짜 범위·배경·종이색·글꼴과 본문 정리는 회원과 같은 페이지 서비스가 맡는다.
     */
    private DiaryPage pageOf(GuestDiaryImportManifest.Page page) {
        if (page == null) {
            throw badRequest("페이지 정보를 확인할 수 없습니다.");
        }
        DiaryPage prepared = new DiaryPage();
        prepared.setPageDate(requireDate(page.pageDate(), "페이지 날짜"));
        prepared.setBackgroundType(page.backgroundType());
        prepared.setPaperColor(page.paperColor());
        prepared.setContent(requireContent(page.content()));
        /*
          한 줄 메모(오늘의 한 줄)는 여기에 담지 않는다. 페이지를 만드는 회원 규칙이
          날짜·순서·배경·종이색·본문만 받기 때문이다. 회원 화면도 장을 만든 뒤에
          한 줄 메모를 따로 저장하므로, 가져오기도 장이 만들어진 다음 같은 규칙으로 저장한다.
          (savePageHeader 참고)
        */
        return prepared;
    }

    /**
     * 한 줄 메모(오늘의 한 줄).
     *
     * <p>회원 화면이 쓰는 저장 규칙을 그대로 부른다 — 길이 상한, 아는 글꼴만 허용,
     * 빈 글은 비움까지 모두 그 규칙이 맡는다. 여기에서 기본값을 지어내지 않는다.
     *
     * <p>적어 둔 글이 없으면 부르지 않는다. 글 없이 글꼴/굵기만 남길 이유가 없다.
     */
    private void savePageHeader(Long diaryId, Long pageId, Long userId,
                                GuestDiaryImportManifest.Page page) {
        String header = page.pageHeader() == null ? "" : page.pageHeader().strip();
        if (header.isEmpty()) {
            return;
        }
        diaryPageService.updatePageHeader(diaryId, pageId, userId,
                header, page.pageHeaderFont(), Boolean.TRUE.equals(page.pageHeaderBold()));
    }

    /**
     * 본문. 길이를 먼저 끊고, 걸러내는 일은 회원 본문과 같은 규칙에 맡긴다.
     * 브라우저가 보낸 HTML 을 그대로 저장하지 않는다.
     */
    private String requireContent(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw badRequest("페이지 본문이 너무 깁니다.");
        }
        return diaryContentSanitizer.sanitize(content);
    }

    /* ===== 요소 ===== */

    private List<GuestDiaryImportManifest.Element> requireElements(
            List<GuestDiaryImportManifest.Element> elements, int limit, String label) {
        if (elements == null) {
            return List.of();
        }
        if (elements.size() > limit) {
            throw badRequest(label + "에 담긴 꾸미기가 너무 많습니다.");
        }
        return elements;
    }

    /**
     * 페이지 요소 한 개.
     *
     * <p>자리·크기·회전·겹침과 글꼴·라벨 디자인 검증은 회원과 같은 요소 서비스가 맡는다.
     * 여기에서는 그 서비스가 보지 않는 두 가지만 먼저 확인한다 —
     * 스티커 경로가 우리 공용 asset 인지, 사진이 정말 함께 올라왔는지.
     * (회원 화면은 이 둘을 서버가 직접 고르므로 확인할 것이 없다)
     */
    private DiaryElement pageElementOf(GuestDiaryImportManifest.Element element,
                                       Map<String, MultipartFile> photos,
                                       List<String> savedFiles) {
        String elementType = requireElementType(element);

        DiaryElement prepared = new DiaryElement();
        prepared.setElementType(elementType);
        prepared.setTextContent(element.textContent());
        prepared.setStyleType(element.styleType());
        prepared.setColorType(element.colorType());
        prepared.setPhotoStyle(element.photoStyle());
        prepared.setTextFont(requireTextFont(elementType, element.textFont()));
        prepared.setTextColor(element.textColor());
        // 사용자가 배치해 둔 자리와 크기를 그대로 옮긴다. 여기에서 다시 세지 않는다.
        prepared.setPositionX(element.positionX());
        prepared.setPositionY(element.positionY());
        prepared.setWidth(element.width());
        prepared.setHeight(element.height());
        prepared.setRotation(element.rotation());
        prepared.setZIndex(element.zIndex());
        prepared.setImageUrl(imageUrlOf(
                elementType, element, photos, savedFiles, PAGE_IMAGE_DIRECTORY));
        return prepared;
    }

    private String requireElementType(GuestDiaryImportManifest.Element element) {
        if (element == null) {
            throw badRequest("꾸미기 정보를 확인할 수 없습니다.");
        }
        String elementType = element.elementType() == null
                ? "" : element.elementType().strip();
        if (!DiaryCoverValues.ALLOWED_ELEMENT_TYPES.contains(elementType)) {
            throw badRequest("지원하지 않는 꾸미기 유형입니다.");
        }
        return elementType;
    }

    /** 라벨기 글씨의 글꼴. 목록에 있는 값만 남기고, 고르지 않았으면 비운다. */
    private String requireTextFont(String elementType, String textFont) {
        if (!TYPE_TEXT.equals(elementType) || textFont == null || textFont.isBlank()) {
            return null;
        }
        return diaryLabelFontCatalog.find(textFont)
                .orElseThrow(() -> badRequest("사용할 수 없는 글꼴입니다."))
                .code();
    }

    /**
     * 저장될 그림 경로.
     *
     * <p>스티커는 우리 공용 asset 목록에 있는 경로만 그대로 쓴다. 파일을 새로 만들지 않는다.
     * 사진은 브라우저가 말한 주소를 버리고, 함께 올라온 원본을 저장해 새 주소를 만든다.
     */
    private String imageUrlOf(String elementType,
                              GuestDiaryImportManifest.Element element,
                              Map<String, MultipartFile> photos,
                              List<String> savedFiles,
                              String directory) {
        if (TYPE_STICKER.equals(elementType)) {
            return diaryStickerCatalog.findByImageUrl(element.imageUrl())
                    .orElseThrow(() -> badRequest("사용할 수 없는 스티커입니다."))
                    .imageUrl();
        }
        if (!TYPE_PHOTO.equals(elementType)) {
            return null;
        }

        MultipartFile file = photos.get(photoRefOf(element));
        if (file == null) {
            throw badRequest("사진 원본을 찾을 수 없습니다.");
        }
        String saved = diaryPrivatePhotoStorage.saveImportedPhoto(file, directory);
        savedFiles.add(saved);
        return saved;
    }

    private String photoRefOf(GuestDiaryImportManifest.Element element) {
        String photoRef = element.photoRef() == null ? "" : element.photoRef().strip();
        if (photoRef.isEmpty()) {
            throw badRequest("사진 원본을 찾을 수 없습니다.");
        }
        return photoRef;
    }

    /* ===== 꾸민 표지 ===== */

    /**
     * 꾸민 표지를 이 여행일기의 표지로 만든다.
     *
     * <p>"내 디자인 보관함"(diary_cover_designs)에는 남기지 않는다. 체험 표지는 보관함 원본이 아니라
     * 이 여행일기에 입힌 표지 자체이기 때문이다. 그래서 사진도 한 벌만 저장한다.
     */
    private void saveCover(Long diaryId,
                           GuestDiaryImportManifest.Cover cover,
                           Map<String, MultipartFile> photos,
                           List<String> savedFiles) {
        DiaryCover prepared = new DiaryCover();
        prepared.setDiaryId(diaryId);
        prepared.setBaseCoverStyle(DiaryCoverValues.baseCoverStyle(cover.baseCoverStyle()));
        prepared.setBackgroundColor(DiaryCoverValues.backgroundColor(cover.backgroundColor()));
        if (diaryCoverMapper.insert(prepared) != 1 || prepared.getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "표지를 저장하지 못했습니다.");
        }

        List<GuestDiaryImportManifest.Element> elements = requireElements(
                cover.elements(), MAX_COVER_ELEMENTS, "표지");
        for (GuestDiaryImportManifest.Element element : elements) {
            saveCoverElement(prepared.getId(), element, photos, savedFiles);
        }
    }

    /**
     * 표지 요소 한 개.
     *
     * <p>표지에는 회원 쪽에도 "요청 값을 검증해 저장하는" 길이 없다(보관함에서 옮겨 담기만 한다).
     * 그래서 페이지 요소가 쓰는 것과 같은 판정을 여기에서 직접 적용한다.
     */
    private void saveCoverElement(Long coverId,
                                  GuestDiaryImportManifest.Element element,
                                  Map<String, MultipartFile> photos,
                                  List<String> savedFiles) {
        String elementType = requireElementType(element);

        DiaryCoverElement prepared = new DiaryCoverElement();
        prepared.setCoverId(coverId);
        prepared.setElementType(elementType);
        prepared.setPositionX(position(element.positionX(), "가로 위치"));
        prepared.setPositionY(position(element.positionY(), "세로 위치"));
        prepared.setWidth(size(element.width(), "너비"));
        prepared.setHeight(size(element.height(), "높이"));
        prepared.setRotation(rotation(element.rotation()));
        prepared.setZIndex(zIndex(element.zIndex()));

        if (TYPE_TEXT.equals(elementType)) {
            prepared.setTextContent(requireLabelText(element.textContent()));
            prepared.setTextFont(requireTextFont(elementType, element.textFont()));
            prepared.setTextColor(requireTextColor(element.textColor()));
        } else if (TYPE_NOTE.equals(elementType)) {
            prepared.setTextContent(element.textContent() == null
                    ? "" : element.textContent().strip());
            prepared.setStyleType(diaryNoteCatalog.find(element.styleType())
                    .orElseThrow(() -> badRequest("라벨/메모지 디자인을 선택해 주세요."))
                    .code());
            prepared.setColorType(requireNoteColor(element.colorType()));
        } else {
            prepared.setImageUrl(imageUrlOf(
                    elementType, element, photos, savedFiles, COVER_IMAGE_DIRECTORY));
            if (TYPE_PHOTO.equals(elementType)) {
                prepared.setPhotoStyle(requirePhotoStyle(element.photoStyle()));
            }
        }

        if (diaryCoverElementMapper.insert(prepared) != 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "표지를 저장하지 못했습니다.");
        }
    }

    private String requireLabelText(String textContent) {
        String value = textContent == null ? "" : textContent.strip();
        if (value.isEmpty()) {
            throw badRequest("글씨 내용을 입력해 주세요.");
        }
        if (value.length() > 50) {
            throw badRequest("글씨가 너무 깁니다.");
        }
        return value;
    }

    private String requireTextColor(String textColor) {
        if (textColor == null || textColor.isBlank()) {
            return null;
        }
        String value = textColor.strip();
        if (!value.matches("^#[0-9a-fA-F]{6}$")) {
            throw badRequest("글자색을 다시 선택해 주세요.");
        }
        return value;
    }

    private String requireNoteColor(String colorType) {
        String requested = colorType == null ? "" : colorType.strip();
        if (requested.isEmpty()) {
            return com.example.travlediary.model.DiaryNoteColor.DEFAULT_CODE;
        }
        return diaryNoteCatalog.findColor(requested)
                .orElseThrow(() -> badRequest("라벨/메모지 색을 선택해 주세요."))
                .code();
    }

    private String requirePhotoStyle(String photoStyle) {
        if (photoStyle == null || photoStyle.isBlank()) {
            return null;
        }
        if (!com.example.travlediary.model.DiaryCoverPhotoStyle.isSupported(photoStyle)) {
            throw badRequest("사진 모양을 다시 선택해 주세요.");
        }
        return com.example.travlediary.model.DiaryCoverPhotoStyle.of(photoStyle).getCode();
    }

    /* ===== 자리/크기 (DB CHECK 과 같은 범위) ===== */

    private BigDecimal position(BigDecimal value, String label) {
        if (value == null) {
            throw badRequest(label + "를 확인할 수 없습니다.");
        }
        if (value.compareTo(new BigDecimal("-0.5")) < 0
                || value.compareTo(new BigDecimal("1.5")) > 0) {
            throw badRequest(label + "가 범위를 벗어났습니다.");
        }
        return value.setScale(5, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal size(BigDecimal value, String label) {
        if (value == null
                || value.compareTo(BigDecimal.ZERO) <= 0
                || value.compareTo(BigDecimal.ONE) > 0) {
            throw badRequest(label + "가 범위를 벗어났습니다.");
        }
        return value.setScale(5, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal rotation(BigDecimal value) {
        BigDecimal rotation = value == null ? BigDecimal.ZERO : value;
        if (rotation.abs().compareTo(new BigDecimal("360")) > 0) {
            throw badRequest("회전 각도가 범위를 벗어났습니다.");
        }
        return rotation.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private int zIndex(Integer value) {
        int zIndex = value == null ? 0 : value;
        if (zIndex < 0) {
            throw badRequest("겹침 순서가 올바르지 않습니다.");
        }
        return zIndex;
    }

    /* ===== 사진 짝짓기 ===== */

    /**
     * 사진 참조와 올라온 파일을 짝지어 확인한다.
     *
     * <p>올라오는 차례에 기대지 않고 manifest 의 표로만 짝을 짓는다.
     * 쓰지 않는 파일이 섞여 오거나, 쓰겠다고 적어 놓고 빠뜨린 것이 있으면 저장을 시작하지 않는다.
     */
    private Map<String, MultipartFile> requirePhotoParts(
            GuestDiaryImportManifest manifest,
            List<GuestDiaryImportManifest.Page> pages,
            Map<String, MultipartFile> photoParts) {

        Map<String, String> declared = manifest.photoParts() == null
                ? Map.of() : manifest.photoParts();
        Map<String, MultipartFile> uploaded = photoParts == null ? Map.of() : photoParts;

        Set<String> used = usedPhotoRefs(manifest, pages);
        if (used.size() > MAX_PHOTOS) {
            throw badRequest("사진이 너무 많습니다.");
        }
        // 쓰지도 않을 사진을 함께 올릴 이유가 없다.
        if (!declared.keySet().equals(used)) {
            throw badRequest("사진 정보가 맞지 않습니다.");
        }
        if (uploaded.size() != used.size()) {
            throw badRequest("사진 정보가 맞지 않습니다.");
        }

        Map<String, MultipartFile> resolved = new java.util.HashMap<>();
        Set<String> seenParts = new HashSet<>();
        for (Map.Entry<String, String> entry : declared.entrySet()) {
            String partName = entry.getValue();
            // 두 사진이 같은 파일을 가리키면 어느 쪽이 무엇인지 알 수 없다.
            if (partName == null || !seenParts.add(partName)) {
                throw badRequest("사진 정보가 맞지 않습니다.");
            }
            MultipartFile file = uploaded.get(partName);
            if (file == null || file.isEmpty()) {
                throw badRequest("사진 원본을 찾을 수 없습니다.");
            }
            resolved.put(entry.getKey(), file);
        }
        return resolved;
    }

    /** 지금 이 여행일기가 실제로 쓰고 있는 사진 참조. (모든 장 + 표지) */
    private Set<String> usedPhotoRefs(GuestDiaryImportManifest manifest,
                                      List<GuestDiaryImportManifest.Page> pages) {
        Set<String> refs = new HashSet<>();
        pages.forEach(page -> collectPhotoRefs(page.elements(), refs));
        if (isCustomCover(manifest)) {
            collectPhotoRefs(manifest.coverDesign().elements(), refs);
        }
        return refs;
    }

    private void collectPhotoRefs(List<GuestDiaryImportManifest.Element> elements,
                                  Set<String> refs) {
        if (elements == null) {
            return;
        }
        elements.stream()
                .filter(element -> element != null
                        && TYPE_PHOTO.equals(element.elementType())
                        && element.photoRef() != null
                        && !element.photoRef().isBlank())
                .forEach(element -> refs.add(element.photoRef().strip()));
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
