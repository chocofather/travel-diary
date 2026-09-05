package com.example.travlediary.service.travelinfo;

import com.example.travlediary.dto.FestivalEditData;
import com.example.travlediary.dto.FestivalEditForm;
import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.InfoImage;
import com.example.travlediary.model.InfoPeriod;
import com.example.travlediary.model.TravelInfo;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.repository.bookmark.BookmarkMapper;
import com.example.travlediary.repository.category.InfoCategoryMapper;
import com.example.travlediary.repository.travelinfo.FestivalInfoMapper;
import com.example.travlediary.repository.travelinfo.TravelInfoMapper;
import com.example.travlediary.service.kto.KtoFestivalImageDownloadService;
import com.example.travlediary.service.post.PostContentSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class FestivalAdminService {

    private static final String ADMIN_SOURCE_TYPE = "ADMIN";
    private static final String BOOKMARK_TARGET_TYPE = "TRAVEL_INFO";

    private final TravelInfoMapper travelInfoMapper;
    private final FestivalInfoMapper festivalInfoMapper;
    private final InfoCategoryMapper infoCategoryMapper;
    private final BookmarkMapper bookmarkMapper;
    private final PostContentSanitizer postContentSanitizer;
    private final KtoFestivalImageDownloadService festivalImageDownloadService;
    private final TravelInfoService travelInfoService;
    private final FestivalInfoService festivalInfoService;

    @Transactional(readOnly = true)
    public FestivalEditData getEditData(Long id) {
        TravelInfo travelInfo = requireFestival(travelInfoMapper.findById(id));
        List<InfoPeriod> periods = safeList(travelInfoMapper.findPeriodsByInfoId(id));
        FestivalInfo festivalInfo = festivalInfoMapper.findByInfoId(id);
        List<InfoImage> images = safeList(travelInfoMapper.findImagesByInfoId(id));

        FestivalEditForm form = new FestivalEditForm();
        form.setTitle(travelInfo.getTitle());
        form.setContent(postContentSanitizer.sanitize(travelInfo.getContent()));
        form.setScope(travelInfo.getScope());
        form.setCategoryId(travelInfo.getCategoryId());
        if (!periods.isEmpty()) {
            form.setStartDate(periods.get(0).getStartDate());
            form.setEndDate(periods.get(0).getEndDate());
        }
        copyFestivalInfoToForm(festivalInfo, form);
        // 번역 슬롯 구성은 각 서비스가 갖고 있다. 축제 전용으로 다시 만들지 않는다.
        form.setTranslations(travelInfoService.getTranslationForms(id));
        form.setFestivalInfoTranslations(festivalInfoService.getTranslationForms(id));
        images.stream()
                .filter(image -> Boolean.TRUE.equals(image.getIsThumbnail()))
                .map(InfoImage::getId)
                .findFirst()
                .ifPresent(form::setThumbnailImageId);
        // 외국어 자동입력이 좌표를 되찾을 때 쓰는 국문 KTO 식별자. 화면 입력값이 아니다.
        return new FestivalEditData(form, images,
                festivalInfo == null ? null : festivalInfo.getExternalContentId());
    }

    @Transactional
    public void update(Long id, FestivalEditForm form) {
        TravelInfo travelInfo = requireFestival(travelInfoMapper.findByIdForUpdate(id));
        PreparedEdit prepared = prepareEdit(form);
        InfoCategory category = validateFestivalCategory(prepared.categoryId());
        FestivalInfo existingFestivalInfo = festivalInfoMapper.findByInfoId(id);
        List<InfoImage> images = safeList(travelInfoMapper.findImagesByInfoId(id));
        validateThumbnailOwnership(prepared.thumbnailImageId(), images);

        travelInfo.setTitle(prepared.title());
        travelInfo.setContent(prepared.content());
        travelInfo.setScope(prepared.scope());
        travelInfo.setCategoryId(category.getId());
        travelInfo.setContentType(TravelInfoContentType.FESTIVAL);
        requireSingleRow(travelInfoMapper.updateTravelInfo(travelInfo),
                "축제·행사 기본정보를 수정하지 못했습니다.");
        // base 수정과 같은 트랜잭션에서 번역까지 끝낸다. 비운 언어는 그 줄만 지워진다.
        travelInfoService.saveTranslations(
                id, prepared.title(), prepared.content(), form.getTranslations());

        travelInfoMapper.deletePeriodsByInfoId(id);
        InfoPeriod period = new InfoPeriod();
        period.setInfoId(id);
        period.setStartDate(prepared.startDate());
        period.setEndDate(prepared.endDate());
        requireSingleRow(travelInfoMapper.insertPeriod(period),
                "축제·행사 기간을 수정하지 못했습니다.");

        FestivalInfo festivalInfo = createFestivalInfo(id, prepared, existingFestivalInfo);
        if (existingFestivalInfo == null) {
            requireSingleRow(festivalInfoMapper.insert(festivalInfo),
                    "축제·행사 상세정보를 저장하지 못했습니다.");
        } else {
            requireSingleRow(festivalInfoMapper.update(festivalInfo),
                    "축제·행사 상세정보를 수정하지 못했습니다.");
        }
        // 행사 상세정보 번역도 같은 트랜잭션에서 저장한다. 비운 언어는 그 줄만 지워진다.
        festivalInfoService.saveTranslations(id, festivalInfo,
                form.getFestivalInfoTranslations());

        travelInfoMapper.clearThumbnailsByInfoId(id);
        if (prepared.thumbnailImageId() != null) {
            requireSingleRow(travelInfoMapper.setThumbnailByIdAndInfoId(prepared.thumbnailImageId(), id),
                    "목록 썸네일을 변경하지 못했습니다.");
        }
    }

    @Transactional
    public void delete(Long id) {
        requireFestival(travelInfoMapper.findByIdForUpdate(id));
        Set<String> imageUrls = new LinkedHashSet<>();
        safeList(travelInfoMapper.findImagesByInfoId(id)).stream()
                .map(InfoImage::getImageUrl)
                .filter(url -> url != null && !url.isBlank())
                .forEach(imageUrls::add);
        registerAfterCommitCleanup(imageUrls);

        bookmarkMapper.deleteByTarget(BOOKMARK_TARGET_TYPE, id);
        requireSingleRow(travelInfoMapper.deleteTravelInfo(id),
                "축제·행사를 삭제하지 못했습니다.");
    }

    private PreparedEdit prepareEdit(FestivalEditForm form) {
        if (form == null) {
            throw new FestivalValidationException(null, "축제·행사 정보를 입력해 주세요.");
        }
        String title = requiredText(form.getTitle(), "title", "제목을 입력해 주세요.", 255);
        if (form.getScope() == null) {
            throw new FestivalValidationException("scope", "여행 범위를 선택해 주세요.");
        }
        if (form.getStartDate() == null) {
            throw new FestivalValidationException("startDate", "행사 시작일을 입력해 주세요.");
        }
        if (form.getEndDate() == null) {
            throw new FestivalValidationException("endDate", "행사 종료일을 입력해 주세요.");
        }
        if (form.getEndDate().isBefore(form.getStartDate())) {
            throw new FestivalValidationException("endDate", "행사 종료일은 시작일보다 빠를 수 없습니다.");
        }
        return new PreparedEdit(
                title,
                postContentSanitizer.sanitize(form.getContent()),
                form.getScope(),
                form.getCategoryId(),
                form.getStartDate(),
                form.getEndDate(),
                optionalText(form.getEventPlace(), "eventPlace", 255),
                optionalText(form.getAddress(), "address", 500),
                optionalText(form.getPlayTime(), "playTime", 500),
                optionalText(form.getUseTime()),
                optionalText(form.getSponsor1(), "sponsor1", 255),
                optionalText(form.getSponsor1Tel(), "sponsor1Tel", 100),
                optionalText(form.getSponsor2(), "sponsor2", 255),
                optionalText(form.getSponsor2Tel(), "sponsor2Tel", 100),
                optionalText(form.getContactTel(), "contactTel", 100),
                optionalText(form.getHomepageUrl(), "homepageUrl", 1000),
                form.getThumbnailImageId());
    }

    private InfoCategory validateFestivalCategory(Long categoryId) {
        if (categoryId == null) {
            throw new FestivalValidationException("categoryId", "축제 분류를 선택해 주세요.");
        }
        InfoCategory category = infoCategoryMapper.findById(categoryId);
        if (category == null || category.getContentType() != TravelInfoContentType.FESTIVAL) {
            throw new FestivalValidationException("categoryId", "축제·행사 분류만 선택할 수 있습니다.");
        }
        return category;
    }

    private void validateThumbnailOwnership(Long thumbnailImageId, List<InfoImage> images) {
        if (thumbnailImageId == null) {
            return;
        }
        boolean owned = images.stream().anyMatch(image -> thumbnailImageId.equals(image.getId()));
        if (!owned) {
            throw new FestivalValidationException("thumbnailImageId", "선택한 목록 썸네일을 다시 확인해 주세요.");
        }
    }

    private FestivalInfo createFestivalInfo(Long id,
                                             PreparedEdit prepared,
                                             FestivalInfo existing) {
        FestivalInfo festivalInfo = new FestivalInfo();
        festivalInfo.setInfoId(id);
        festivalInfo.setEventPlace(prepared.eventPlace());
        festivalInfo.setAddress(prepared.address());
        festivalInfo.setPlayTime(prepared.playTime());
        festivalInfo.setUseTime(prepared.useTime());
        festivalInfo.setSponsor1(prepared.sponsor1());
        festivalInfo.setSponsor1Tel(prepared.sponsor1Tel());
        festivalInfo.setSponsor2(prepared.sponsor2());
        festivalInfo.setSponsor2Tel(prepared.sponsor2Tel());
        festivalInfo.setContactTel(prepared.contactTel());
        festivalInfo.setHomepageUrl(prepared.homepageUrl());
        festivalInfo.setSourceType(existing == null ? ADMIN_SOURCE_TYPE : existing.getSourceType());
        festivalInfo.setExternalContentId(existing == null ? null : existing.getExternalContentId());
        return festivalInfo;
    }

    private void registerAfterCommitCleanup(Set<String> imageUrls) {
        if (imageUrls.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("축제 삭제 이미지 정리를 등록하지 못했습니다: info image count={}", imageUrls.size());
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                imageUrls.forEach(FestivalAdminService.this::deleteFestivalImageSafely);
            }
        });
    }

    private void deleteFestivalImageSafely(String imageUrl) {
        try {
            boolean deleted = festivalImageDownloadService.deleteDownloadedFestivalImage(imageUrl);
            if (!deleted) {
                log.warn("관리 축제 업로드 경로가 아니어서 이미지 파일 삭제를 건너뜁니다: {}", imageUrl);
            }
        } catch (RuntimeException exception) {
            log.warn("삭제된 축제의 이미지 파일을 정리하지 못했습니다: {}", imageUrl, exception);
        }
    }

    private String requiredText(String value, String field, String message, int maxLength) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new FestivalValidationException(field, message);
        }
        if (normalized.length() > maxLength) {
            throw new FestivalValidationException(field, maxLength + "자 이내로 입력해 주세요.");
        }
        return normalized;
    }

    private String optionalText(String value, String field, int maxLength) {
        String normalized = optionalText(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new FestivalValidationException(field, maxLength + "자 이내로 입력해 주세요.");
        }
        return normalized;
    }

    private String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private void requireSingleRow(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new IllegalStateException(message);
        }
    }

    private void copyFestivalInfoToForm(FestivalInfo source, FestivalEditForm target) {
        if (source == null) {
            return;
        }
        target.setEventPlace(source.getEventPlace());
        target.setAddress(source.getAddress());
        target.setPlayTime(source.getPlayTime());
        target.setUseTime(source.getUseTime());
        target.setSponsor1(source.getSponsor1());
        target.setSponsor1Tel(source.getSponsor1Tel());
        target.setSponsor2(source.getSponsor2());
        target.setSponsor2Tel(source.getSponsor2Tel());
        target.setContactTel(source.getContactTel());
        target.setHomepageUrl(source.getHomepageUrl());
    }

    private TravelInfo requireFestival(TravelInfo travelInfo) {
        if (travelInfo == null || travelInfo.getContentType() != TravelInfoContentType.FESTIVAL) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "축제·행사 정보를 찾을 수 없습니다.");
        }
        return travelInfo;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record PreparedEdit(
            String title,
            String content,
            TravelInfoScope scope,
            Long categoryId,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            String eventPlace,
            String address,
            String playTime,
            String useTime,
            String sponsor1,
            String sponsor1Tel,
            String sponsor2,
            String sponsor2Tel,
            String contactTel,
            String homepageUrl,
            Long thumbnailImageId) {
    }
}
