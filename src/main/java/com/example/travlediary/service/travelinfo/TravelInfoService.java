package com.example.travlediary.service.travelinfo;

import com.example.travlediary.dto.AdminTravelInfoDetailDto;
import com.example.travlediary.dto.AdminTravelInfoListItemDto;
import com.example.travlediary.dto.InfoPeriodForm;
import com.example.travlediary.dto.TravelInfoForm;
import com.example.travlediary.dto.TravelInfoDetailDto;
import com.example.travlediary.dto.TravelInfoListItemDto;
import com.example.travlediary.dto.TravelInfoPeriodDto;
import com.example.travlediary.dto.TravelInfoTranslationForm;
import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.model.BookmarkTargetType;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.InfoImage;
import com.example.travlediary.model.InfoPeriod;
import com.example.travlediary.model.TravelInfo;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.model.TravelInfoTranslation;
import com.example.travlediary.repository.bookmark.BookmarkMapper;
import com.example.travlediary.repository.category.InfoCategoryMapper;
import com.example.travlediary.repository.travelinfo.TravelInfoMapper;
import com.example.travlediary.service.category.ReferenceNameLocalizationService;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.post.PostContentSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TravelInfoService {

    private static final String KOREAN_CODE = SupportedLanguage.KOREAN.getLanguageTag();

    /** 번역 슬롯 언어. 한국어는 base 입력이 대신하므로 여기에서 뺀다. */
    private static final Set<String> SUPPORTED_TRANSLATION_CODES = SupportedLanguage.all().stream()
            .filter(language -> language != SupportedLanguage.KOREAN)
            .map(SupportedLanguage::getLanguageTag)
            .collect(Collectors.toUnmodifiableSet());

    private final TravelInfoMapper travelInfoMapper;
    private final BookmarkMapper bookmarkMapper;
    private final InfoCategoryMapper infoCategoryMapper;
    private final PostContentSanitizer postContentSanitizer;
    private final FileUploadService fileUploadService;
    private final TravelInfoLocalizationService travelInfoLocalizationService;
    private final ReferenceNameLocalizationService referenceNameLocalizationService;

    @Transactional(readOnly = true)
    public List<AdminTravelInfoListItemDto> getAdminList(TravelInfoScope scope,
                                                         TravelInfoContentType contentType,
                                                         Long categoryId) {
        return travelInfoMapper.findAdminList(scope, contentType, categoryId);
    }

    @Transactional(readOnly = true)
    public List<TravelInfoListItemDto> getPublicList(TravelInfoScope scope,
                                                      TravelInfoContentType contentType,
                                                      List<Long> categoryIds,
                                                      String keyword,
                                                      String sort,
                                                      long offset,
                                                      int limit) {
        return travelInfoMapper.findPublicList(
                scope, normalizePublicContentType(contentType), categoryIds,
                TravelInfoSearchKeyword.toLikeLiteral(keyword),
                TravelInfoSearchKeyword.toKoreanPrefixRegex(keyword), sort, offset, limit);
    }

    @Transactional(readOnly = true)
    public long countPublicList(TravelInfoScope scope,
                                TravelInfoContentType contentType,
                                List<Long> categoryIds,
                                String keyword) {
        return travelInfoMapper.countPublicList(
                scope, normalizePublicContentType(contentType), categoryIds,
                TravelInfoSearchKeyword.toLikeLiteral(keyword),
                TravelInfoSearchKeyword.toKoreanPrefixRegex(keyword));
    }

    /**
     * 공개 목록의 제목만 요청 언어로 바꿔 둔다. (목록 DTO 에는 본문이 없다)
     *
     * <p>번역은 목록 전체를 한 번에 읽는다. 조회 결과 DTO 는 이 요청에서만 쓰는 값이라
     * 표시할 제목을 그대로 담아도 관리자 화면이 쓰는 원문에는 영향이 없다.
     * 카테고리 이름은 아직 번역하지 않으므로 그대로 둔다.
     */
    @Transactional(readOnly = true)
    public void localizePublicList(List<TravelInfoListItemDto> travelInfoList,
                                   SupportedLanguage requestedLanguage) {
        if (travelInfoList == null || travelInfoList.isEmpty()) {
            // 볼 여행정보가 없으면 번역도 읽지 않는다.
            return;
        }

        Map<Long, String> baseTitles = new LinkedHashMap<>();
        for (TravelInfoListItemDto item : travelInfoList) {
            if (item != null && item.getId() != null) {
                baseTitles.putIfAbsent(item.getId(), item.getTitle());
            }
        }
        if (baseTitles.isEmpty()) {
            return;
        }

        Map<Long, TravelInfoTranslation> localized = travelInfoLocalizationService
                .resolveLocalizedContentByInfoIds(baseTitles, Map.of(), requestedLanguage);
        for (TravelInfoListItemDto item : travelInfoList) {
            if (item == null || item.getId() == null) {
                continue;
            }
            TravelInfoTranslation display = localized.get(item.getId());
            if (display != null && display.getTitle() != null) {
                item.setTitle(display.getTitle());
            }
        }

        localizeListCategoryNames(travelInfoList, requestedLanguage);
    }

    /**
     * 목록 카드의 카테고리 이름을 요청 언어로 바꾼다.
     *
     * <p>카테고리 번역도 목록 전체를 한 번에 읽는다. GENERAL / FESTIVAL 이 같은 카테고리
     * 번역 테이블을 쓰므로 유형을 나누지 않는다.
     */
    private void localizeListCategoryNames(List<TravelInfoListItemDto> travelInfoList,
                                           SupportedLanguage requestedLanguage) {
        Map<Long, String> baseCategoryNames = new LinkedHashMap<>();
        for (TravelInfoListItemDto item : travelInfoList) {
            if (item != null && item.getCategoryId() != null) {
                baseCategoryNames.putIfAbsent(item.getCategoryId(), item.getCategoryName());
            }
        }
        if (baseCategoryNames.isEmpty()) {
            return;
        }

        Map<Long, String> localizedNames = referenceNameLocalizationService
                .localizeInfoCategoryNames(baseCategoryNames, requestedLanguage);
        for (TravelInfoListItemDto item : travelInfoList) {
            if (item == null || item.getCategoryId() == null) {
                continue;
            }
            String name = localizedNames.get(item.getCategoryId());
            if (name != null) {
                item.setCategoryName(name);
            }
        }
    }

    /**
     * 공개 상세의 제목·본문을 요청 언어로 바꿔 둔다.
     *
     * <p>제목과 본문은 각각 따로 대체되므로 서로 다른 언어에서 올 수 있다.
     * 번역 본문도 원문과 똑같이 sanitize 를 거쳐 화면으로 나간다.
     * 카테고리 이름과 축제 상세정보는 아직 번역하지 않으므로 그대로 둔다.
     */
    @Transactional(readOnly = true)
    public void localizePublicDetail(TravelInfoDetailDto detail,
                                     SupportedLanguage requestedLanguage) {
        if (detail == null || detail.getId() == null) {
            return;
        }

        TravelInfoTranslation display = travelInfoLocalizationService.resolveLocalizedContent(
                detail.getId(), detail.getTitle(), detail.getContent(), requestedLanguage);
        if (display.getTitle() != null) {
            detail.setTitle(display.getTitle());
        }
        if (display.getContent() != null) {
            detail.setContent(postContentSanitizer.sanitize(display.getContent()));
        }
        detail.setCategoryName(referenceNameLocalizationService.localizeInfoCategoryName(
                detail.getCategoryId(), detail.getCategoryName(), requestedLanguage));
    }

    @Transactional(readOnly = true)
    public void populatePublicListBookmarks(List<TravelInfoListItemDto> travelInfoList,
                                            Long currentUserId) {
        if (travelInfoList == null || travelInfoList.isEmpty()) {
            return;
        }
        travelInfoList.forEach(item -> item.setBookmarked(false));
        if (currentUserId == null) {
            return;
        }

        List<Long> infoIds = travelInfoList.stream()
                .map(TravelInfoListItemDto::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (infoIds.isEmpty()) {
            return;
        }

        Set<Long> bookmarkedIds = bookmarkMapper.findBookmarkedTargetIds(
                currentUserId, BookmarkTargetType.TRAVEL_INFO.name(), infoIds);
        Set<Long> safeBookmarkedIds = bookmarkedIds == null ? Set.of() : bookmarkedIds;
        travelInfoList.forEach(item -> item.setBookmarked(
                item.getId() != null && safeBookmarkedIds.contains(item.getId())));
    }

    @Transactional(readOnly = true)
    public void populatePublicDetailBookmark(TravelInfoDetailDto detail, Long currentUserId) {
        if (detail == null) {
            return;
        }
        detail.setBookmarked(false);
        if (currentUserId == null || detail.getId() == null) {
            return;
        }
        detail.setBookmarked(bookmarkMapper.findByUserAndTarget(
                currentUserId, BookmarkTargetType.TRAVEL_INFO.name(), detail.getId()) != null);
    }

    @Transactional
    public TravelInfoDetailDto getPublicDetail(Long id) {
        if (id == null || travelInfoMapper.incrementPublicViews(id) != 1) {
            throw notFound();
        }

        TravelInfoDetailDto detail = travelInfoMapper.findPublicDetailById(id);
        if (detail == null) {
            throw notFound();
        }
        detail.setContent(postContentSanitizer.sanitize(detail.getContent()));

        if (detail.getContentType() == TravelInfoContentType.FESTIVAL) {
            List<InfoPeriod> periods = travelInfoMapper.findPeriodsByInfoId(id);
            detail.setPeriods(periods == null ? List.of() : periods.stream()
                    .map(period -> new TravelInfoPeriodDto(
                            period.getStartDate(), period.getEndDate()))
                    .toList());
        } else {
            detail.setPeriods(List.of());
        }
        return detail;
    }

    @Transactional(readOnly = true)
    public TravelInfo getById(Long id) {
        return requireTravelInfo(travelInfoMapper.findById(id));
    }

    @Transactional(readOnly = true)
    public String getThumbnailUrl(Long id) {
        InfoImage thumbnail = travelInfoMapper.findMainImageByInfoId(id);
        return thumbnail == null ? null : thumbnail.getImageUrl();
    }

    @Transactional(readOnly = true)
    public AdminTravelInfoDetailDto getAdminDetail(Long id) {
        TravelInfo travelInfo = requireTravelInfo(travelInfoMapper.findById(id));
        InfoCategory category = infoCategoryMapper.findById(travelInfo.getCategoryId());
        List<InfoPeriod> periods = travelInfo.getContentType() == TravelInfoContentType.FESTIVAL
                ? travelInfoMapper.findPeriodsByInfoId(id)
                : List.of();

        AdminTravelInfoDetailDto detail = new AdminTravelInfoDetailDto();
        detail.setId(travelInfo.getId());
        detail.setTitle(travelInfo.getTitle());
        detail.setContent(postContentSanitizer.sanitize(travelInfo.getContent()));
        detail.setScope(travelInfo.getScope());
        detail.setContentType(travelInfo.getContentType());
        detail.setCategoryId(travelInfo.getCategoryId());
        detail.setCategoryName(category == null ? null : category.getName());
        detail.setViews(travelInfo.getViews());
        detail.setCreatedAt(travelInfo.getCreatedAt());
        detail.setUpdatedAt(travelInfo.getUpdatedAt());
        detail.setPeriods(periods == null ? List.of() : periods);
        return detail;
    }

    @Transactional(readOnly = true)
    public TravelInfoForm getForm(Long id) {
        TravelInfo travelInfo = requireTravelInfo(travelInfoMapper.findById(id));
        travelInfo.setContent(postContentSanitizer.sanitize(travelInfo.getContent()));
        List<InfoPeriod> periods = travelInfoMapper.findPeriodsByInfoId(id);
        TravelInfoForm form = TravelInfoForm.from(travelInfo, periods);
        form.setTranslations(getTranslationForms(id));
        return form;
    }

    /**
     * 관리자 수정 화면 복원용. 저장된 줄이 있으면 슬롯에 채우고, 없으면 빈 슬롯을 돌려준다.
     *
     * <p>슬롯은 언어 코드로 찾아 채운다. 자리 번호에 뜻을 두지 않으므로 저장된 언어가
     * 몇 개든 나머지 언어는 빈 칸으로 남는다.
     */
    @Transactional(readOnly = true)
    public List<TravelInfoTranslationForm> getTranslationForms(Long travelInfoId) {
        List<TravelInfoTranslationForm> slots = TravelInfoTranslationForm.newTranslationSlots();
        if (travelInfoId == null) {
            return slots;
        }

        Map<String, TravelInfoTranslationForm> slotsByLanguage = new LinkedHashMap<>();
        for (TravelInfoTranslationForm slot : slots) {
            slotsByLanguage.putIfAbsent(slot.getLanguageCode(), slot);
        }

        List<TravelInfoTranslation> stored = travelInfoMapper.findTranslationsByInfoId(travelInfoId);
        if (stored != null) {
            for (TravelInfoTranslation translation : stored) {
                if (translation == null || translation.getLanguageCode() == null) {
                    continue;
                }
                TravelInfoTranslationForm slot = slotsByLanguage.get(translation.getLanguageCode());
                if (slot == null) {
                    // 슬롯에 없는 언어가 남아 있어도 화면에는 그리지 않는다.
                    continue;
                }
                slot.setTitle(translation.getTitle() == null ? "" : translation.getTitle());
                slot.setContent(translation.getContent() == null ? "" : translation.getContent());
            }
        }
        return slots;
    }

    @Transactional
    public Long create(TravelInfoForm form, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("관리자 정보를 확인할 수 없습니다.");
        }

        ValidatedTravelInfo validated = validate(form);
        String newThumbnailUrl = saveNewThumbnail(form.getThumbnailFile());
        boolean lifecycleRegistered = false;
        try {
            lifecycleRegistered = registerFileLifecycle(newThumbnailUrl, List.of());

            TravelInfo travelInfo = new TravelInfo();
            travelInfo.setTitle(validated.title());
            travelInfo.setContent(validated.content());
            travelInfo.setScope(form.getScope());
            travelInfo.setContentType(form.getContentType());
            travelInfo.setCategoryId(form.getCategoryId());
            travelInfo.setViews(0);
            travelInfo.setUserId(userId);

            if (travelInfoMapper.insertTravelInfo(travelInfo) != 1 || travelInfo.getId() == null) {
                throw new IllegalStateException("여행정보 저장에 실패했습니다.");
            }
            insertPeriods(travelInfo.getId(), validated.periods());
            // base 저장과 같은 트랜잭션에서 번역까지 끝낸다. ko 는 base 값으로 맞춰진다.
            saveTranslations(travelInfo.getId(), validated.title(), validated.content(),
                    form.getTranslations());
            if (newThumbnailUrl != null) {
                insertThumbnail(travelInfo.getId(), newThumbnailUrl);
            }
            return travelInfo.getId();
        } catch (RuntimeException exception) {
            if (!lifecycleRegistered) {
                deleteFileSafely(newThumbnailUrl);
            }
            throw exception;
        }
    }

    @Transactional
    public void update(Long id, TravelInfoForm form) {
        TravelInfo travelInfo = requireTravelInfo(travelInfoMapper.findByIdForUpdate(id));
        ValidatedTravelInfo validated = validate(form);

        boolean replaceThumbnail = hasNewThumbnail(form.getThumbnailFile());
        boolean deleteThumbnail = !replaceThumbnail && form.isRemoveThumbnail();
        List<String> previousThumbnailUrls = replaceThumbnail || deleteThumbnail
                ? mainThumbnailUrls(id)
                : List.of();
        String newThumbnailUrl = replaceThumbnail ? saveNewThumbnail(form.getThumbnailFile()) : null;
        boolean lifecycleRegistered = false;

        try {
            lifecycleRegistered = registerFileLifecycle(newThumbnailUrl, previousThumbnailUrls);

            travelInfo.setTitle(validated.title());
            travelInfo.setContent(validated.content());
            travelInfo.setScope(form.getScope());
            travelInfo.setContentType(form.getContentType());
            travelInfo.setCategoryId(form.getCategoryId());

            if (travelInfoMapper.updateTravelInfo(travelInfo) != 1) {
                throw notFound();
            }
            travelInfoMapper.deletePeriodsByInfoId(id);
            insertPeriods(id, validated.periods());
            // base 수정과 같은 트랜잭션에서 번역까지 끝낸다. ko 는 base 값으로 맞춰진다.
            saveTranslations(id, validated.title(), validated.content(), form.getTranslations());

            if (replaceThumbnail || deleteThumbnail) {
                travelInfoMapper.deleteMainImagesByInfoId(id);
                if (newThumbnailUrl != null) {
                    insertThumbnail(id, newThumbnailUrl);
                }
            }

            if (!lifecycleRegistered) {
                deleteFilesSafely(previousThumbnailUrls);
            }
        } catch (RuntimeException exception) {
            if (!lifecycleRegistered) {
                deleteFileSafely(newThumbnailUrl);
            }
            throw exception;
        }
    }

    @Transactional
    public void delete(Long id) {
        requireTravelInfo(travelInfoMapper.findByIdForUpdate(id));
        List<String> previousThumbnailUrls = mainThumbnailUrls(id);
        boolean lifecycleRegistered = registerFileLifecycle(null, previousThumbnailUrls);
        bookmarkMapper.deleteByTarget(BookmarkTargetType.TRAVEL_INFO.name(), id);
        if (travelInfoMapper.deleteTravelInfo(id) != 1) {
            throw notFound();
        }
        if (!lifecycleRegistered) {
            deleteFilesSafely(previousThumbnailUrls);
        }
    }

    /**
     * 여행정보 번역을 언어 한 줄씩 저장한다. GENERAL / FESTIVAL 이 같이 쓰는 공통 API 다.
     *
     * <p>한국어는 화면의 제목·본문 입력이 그대로 ko 줄이 된다. 그래서 번역 입력에 ko 슬롯이
     * 섞여 들어와도 쓰지 않는다 — base 를 번역 입력으로 덮어쓰는 길은 두지 않는다.
     *
     * <p>나머지 언어는 제목·본문 중 하나라도 실제 값이 있으면 남기고, 둘 다 비면 그 언어 줄만
     * 지운다. 언어별로 따로 처리하므로 한 언어를 지워도 다른 언어 줄은 그대로다.
     *
     * @param baseTitle   travel_info 에 저장한 원문 제목
     * @param baseContent travel_info 에 저장한 원문 본문
     */
    @Transactional
    public void saveTranslations(Long travelInfoId,
                                 String baseTitle,
                                 String baseContent,
                                 List<TravelInfoTranslationForm> translationForms) {
        if (travelInfoId == null) {
            return;
        }

        // 기존 줄은 한 번만 읽고 언어 코드로 찾아 쓴다.
        Map<String, TravelInfoTranslation> existing = new LinkedHashMap<>();
        List<TravelInfoTranslation> stored = travelInfoMapper.findTranslationsByInfoId(travelInfoId);
        if (stored != null) {
            for (TravelInfoTranslation translation : stored) {
                if (translation != null && translation.getLanguageCode() != null) {
                    existing.putIfAbsent(translation.getLanguageCode(), translation);
                }
            }
        }

        saveTranslation(koreanTranslation(travelInfoId, baseTitle, baseContent),
                existing.containsKey(KOREAN_CODE));

        if (translationForms == null) {
            return;
        }
        Set<String> handledLanguages = new HashSet<>();
        for (TravelInfoTranslationForm form : translationForms) {
            if (form == null || form.getLanguageCode() == null) {
                continue;
            }
            String languageCode = form.getLanguageCode();
            if (!SUPPORTED_TRANSLATION_CODES.contains(languageCode)) {
                // 화면이 정한 슬롯 언어만 저장한다. ko 슬롯과 임의 언어 코드는 무시한다.
                continue;
            }
            if (!handledLanguages.add(languageCode)) {
                // 같은 언어가 두 번 들어오면 앞의 값만 쓴다. (UNIQUE 충돌을 만들지 않는다)
                continue;
            }
            saveTranslation(translationOf(travelInfoId, form), existing.containsKey(languageCode));
        }
    }

    /** 값이 아예 없으면 그 언어 줄을 남기지 않는다. */
    private void saveTranslation(TravelInfoTranslation translation, boolean exists) {
        if (isEmpty(translation)) {
            if (exists) {
                travelInfoMapper.deleteTranslation(
                        translation.getTravelInfoId(), translation.getLanguageCode());
            }
            return;
        }
        if (exists) {
            travelInfoMapper.updateTranslation(translation);
        } else {
            travelInfoMapper.insertTranslation(translation);
        }
    }

    private TravelInfoTranslation koreanTranslation(Long travelInfoId,
                                                    String baseTitle,
                                                    String baseContent) {
        TravelInfoTranslation translation = new TravelInfoTranslation();
        translation.setTravelInfoId(travelInfoId);
        translation.setLanguageCode(KOREAN_CODE);
        translation.setTitle(nonBlankTitle(baseTitle));
        translation.setContent(nonBlankContent(baseContent));
        return translation;
    }

    private TravelInfoTranslation translationOf(Long travelInfoId,
                                                TravelInfoTranslationForm form) {
        TravelInfoTranslation translation = new TravelInfoTranslation();
        translation.setTravelInfoId(travelInfoId);
        translation.setLanguageCode(form.getLanguageCode());
        translation.setTitle(nonBlankTitle(form.getTitle()));
        translation.setContent(nonBlankContent(form.getContent()));
        return translation;
    }

    private boolean isEmpty(TravelInfoTranslation translation) {
        return translation.getTitle() == null && translation.getContent() == null;
    }

    private String nonBlankTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        return title.strip();
    }

    /** 번역 본문도 원문과 같은 sanitize 를 거친다. 태그만 남은 Quill HTML 은 값이 없는 것으로 본다. */
    private String nonBlankContent(String content) {
        String sanitized = postContentSanitizer.sanitize(content);
        return TravelInfoContent.hasContent(sanitized) ? sanitized : null;
    }

    private boolean hasNewThumbnail(MultipartFile thumbnailFile) {
        return thumbnailFile != null && !thumbnailFile.isEmpty();
    }

    private String saveNewThumbnail(MultipartFile thumbnailFile) {
        if (!hasNewThumbnail(thumbnailFile)) {
            return null;
        }
        try {
            return fileUploadService.saveTravelInfoThumbnail(thumbnailFile);
        } catch (IllegalArgumentException exception) {
            throw new TravelInfoValidationException("thumbnailFile", exception.getMessage());
        }
    }

    private void insertThumbnail(Long infoId, String imageUrl) {
        InfoImage thumbnail = new InfoImage();
        thumbnail.setImageUrl(imageUrl);
        thumbnail.setIsMain(true);
        thumbnail.setOrderIndex(1);
        thumbnail.setInfoId(infoId);
        if (travelInfoMapper.insertInfoImage(thumbnail) != 1) {
            throw new IllegalStateException("여행정보 썸네일 저장에 실패했습니다.");
        }
    }

    private TravelInfoContentType normalizePublicContentType(TravelInfoContentType contentType) {
        return contentType == null ? TravelInfoContentType.GENERAL : contentType;
    }

    private List<String> mainThumbnailUrls(Long infoId) {
        List<String> urls = travelInfoMapper.findMainImageUrlsByInfoId(infoId);
        return urls == null ? List.of() : urls.stream()
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .toList();
    }

    private boolean registerFileLifecycle(String newThumbnailUrl, List<String> previousThumbnailUrls) {
        if (newThumbnailUrl == null && previousThumbnailUrls.isEmpty()) {
            return false;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteFilesSafely(previousThumbnailUrls);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteFileSafely(newThumbnailUrl);
                }
            }
        });
        return true;
    }

    private void deleteFilesSafely(List<String> imageUrls) {
        imageUrls.forEach(this::deleteFileSafely);
    }

    private void deleteFileSafely(String imageUrl) {
        if (imageUrl == null) {
            return;
        }
        try {
            fileUploadService.deleteTravelInfoThumbnail(imageUrl);
        } catch (RuntimeException exception) {
            log.warn("여행정보 썸네일 파일을 정리하지 못했습니다: {}", imageUrl, exception);
        }
    }

    private ValidatedTravelInfo validate(TravelInfoForm form) {
        if (form == null) {
            throw new TravelInfoValidationException(null, "여행정보를 입력해 주세요.");
        }

        String title = form.getTitle() == null ? "" : form.getTitle().strip();
        form.setTitle(title);
        if (title.isEmpty()) {
            throw new TravelInfoValidationException("title", "제목을 입력해 주세요.");
        }
        if (title.length() > 255) {
            throw new TravelInfoValidationException("title", "제목은 255자 이하로 입력해 주세요.");
        }
        if (form.getScope() == null) {
            throw new TravelInfoValidationException("scope", "국내/해외 범위를 선택해 주세요.");
        }
        if (form.getContentType() == null) {
            throw new TravelInfoValidationException("contentType", "여행정보 유형을 선택해 주세요.");
        }

        String content = postContentSanitizer.sanitize(form.getContent());
        form.setContent(content);
        if (!TravelInfoContent.hasContent(content)) {
            throw new TravelInfoValidationException("content", "본문을 입력해 주세요.");
        }

        if (form.getCategoryId() == null) {
            throw new TravelInfoValidationException("categoryId", "정보 카테고리를 선택해 주세요.");
        }
        InfoCategory category = infoCategoryMapper.findById(form.getCategoryId());
        if (category == null) {
            throw new TravelInfoValidationException("categoryId", "존재하지 않는 정보 카테고리입니다.");
        }
        if (category.getContentType() != form.getContentType()) {
            throw new TravelInfoValidationException("categoryId",
                    "선택한 정보 카테고리의 유형이 여행정보 유형과 일치하지 않습니다.");
        }

        List<ValidatedPeriod> periods = validatePeriods(form);
        return new ValidatedTravelInfo(title, content, periods);
    }

    private List<ValidatedPeriod> validatePeriods(TravelInfoForm form) {
        if (form.getContentType() == TravelInfoContentType.GENERAL) {
            return List.of();
        }

        List<ValidatedPeriod> periods = new ArrayList<>();
        List<InfoPeriodForm> requestedPeriods = form.getPeriods() == null ? List.of() : form.getPeriods();
        for (InfoPeriodForm period : requestedPeriods) {
            if (period == null || (period.getStartDate() == null && period.getEndDate() == null)) {
                continue;
            }
            if (period.getStartDate() == null || period.getEndDate() == null) {
                throw new TravelInfoValidationException("periods", "축제 기간의 시작일과 종료일을 모두 입력해 주세요.");
            }
            if (period.getStartDate().isAfter(period.getEndDate())) {
                throw new TravelInfoValidationException("periods", "축제 기간의 시작일은 종료일보다 늦을 수 없습니다.");
            }
            periods.add(new ValidatedPeriod(period.getStartDate(), period.getEndDate()));
        }

        if (periods.isEmpty()) {
            throw new TravelInfoValidationException("periods", "축제 여행정보는 기간을 한 개 이상 입력해 주세요.");
        }

        Set<ValidatedPeriod> uniquePeriods = new HashSet<>();
        for (ValidatedPeriod period : periods) {
            if (!uniquePeriods.add(period)) {
                throw new TravelInfoValidationException("periods", "동일한 축제 기간을 중복해서 입력할 수 없습니다.");
            }
        }

        periods.sort(Comparator.comparing(ValidatedPeriod::startDate)
                .thenComparing(ValidatedPeriod::endDate));
        for (int index = 1; index < periods.size(); index++) {
            ValidatedPeriod previous = periods.get(index - 1);
            ValidatedPeriod current = periods.get(index);
            if (!current.startDate().isAfter(previous.endDate())) {
                throw new TravelInfoValidationException("periods", "서로 겹치는 축제 기간을 입력할 수 없습니다.");
            }
        }
        return periods;
    }

    private void insertPeriods(Long infoId, List<ValidatedPeriod> periods) {
        for (ValidatedPeriod validatedPeriod : periods) {
            InfoPeriod period = new InfoPeriod();
            period.setInfoId(infoId);
            period.setStartDate(validatedPeriod.startDate());
            period.setEndDate(validatedPeriod.endDate());
            if (travelInfoMapper.insertPeriod(period) != 1) {
                throw new IllegalStateException("여행정보 기간 저장에 실패했습니다.");
            }
        }
    }

    private TravelInfo requireTravelInfo(TravelInfo travelInfo) {
        if (travelInfo == null) {
            throw notFound();
        }
        return travelInfo;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "여행정보를 찾을 수 없습니다.");
    }

    private record ValidatedTravelInfo(String title, String content, List<ValidatedPeriod> periods) {
    }

    private record ValidatedPeriod(LocalDate startDate, LocalDate endDate) {
    }
}
